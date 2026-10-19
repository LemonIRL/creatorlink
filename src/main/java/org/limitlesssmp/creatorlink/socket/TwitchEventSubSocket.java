package org.limitlesssmp.creatorlink.socket;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.EventProcessor;
import org.limitlesssmp.creatorlink.util.JsonFlatten;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Twitch EventSub over WebSocket. On session_welcome we create the documented
 * set of subscriptions via Helix. Notifications dispatch as socket
 * "twitcheventsub", platform = subscription type minus its last segment
 * (channel.subscription.message → channel.subscription + message); channel.raid
 * maps to incoming/outgoing based on which side of the raid we subscribed to.
 */
public final class TwitchEventSubSocket extends SocketBase {
    private static final String WS_URL = "wss://eventsub.wss.twitch.tv/ws";
    private static final String HELIX_SUBS = "https://api.twitch.tv/helix/eventsub/subscriptions";
    private static final String VALIDATE = "https://id.twitch.tv/oauth2/validate";
    private static final String TTG_REFRESH = "https://twitchtokengenerator.com/api/refresh/";

    /** type, version, extra condition keys ("mod" → moderator_user_id, "user" → user_id, "to"/"from" → raid sides). */
    private static final String[][] SUBSCRIPTIONS = {
            {"channel.update", "2", ""},
            {"channel.follow", "2", "mod"},
            {"channel.cheer", "1", ""},
            {"channel.subscribe", "1", ""},
            {"channel.subscription.end", "1", ""},
            {"channel.subscription.gift", "1", ""},
            {"channel.subscription.message", "1", ""},
            {"channel.channel_points_custom_reward.add", "1", ""},
            {"channel.channel_points_custom_reward.update", "1", ""},
            {"channel.channel_points_custom_reward.remove", "1", ""},
            {"channel.channel_points_custom_reward_redemption.add", "1", ""},
            {"channel.channel_points_custom_reward_redemption.update", "1", ""},
            {"channel.channel_points_automatic_reward_redemption.add", "1", ""},
            {"channel.poll.begin", "1", ""},
            {"channel.poll.progress", "1", ""},
            {"channel.poll.end", "1", ""},
            {"channel.prediction.begin", "1", ""},
            {"channel.prediction.progress", "1", ""},
            {"channel.prediction.lock", "1", ""},
            {"channel.prediction.end", "1", ""},
            {"channel.hype_train.begin", "1", ""},
            {"channel.hype_train.progress", "1", ""},
            {"channel.hype_train.end", "1", ""},
            {"channel.charity_campaign.start", "1", ""},
            {"channel.charity_campaign.progress", "1", ""},
            {"channel.charity_campaign.stop", "1", ""},
            {"channel.charity_campaign.donate", "1", ""},
            {"channel.goal.begin", "1", ""},
            {"channel.goal.progress", "1", ""},
            {"channel.goal.end", "1", ""},
            {"channel.ad_break.begin", "1", ""},
            {"channel.chat.message", "1", "user"},
            {"channel.chat.notification", "1", "user"},
            {"channel.chat.clear", "1", "user"},
            {"channel.chat.message_delete", "1", "user"},
            {"channel.chat_settings.update", "1", "user"},
            {"channel.bits.use", "1", ""},
            {"stream.online", "1", ""},
            {"stream.offline", "1", ""},
            {"channel.raid", "1", "to"},
            {"channel.raid", "1", "from"},
    };

    private volatile String accessToken;
    private volatile String refreshToken;
    private final String configuredClientId;
    /** Called when the token refreshes so it can be persisted back to the config file. */
    private final BiConsumer<String, String> tokenPersister;

    private volatile String clientId;
    private volatile String userId;
    private volatile WebSocket ws;
    private volatile String sessionId;
    private volatile long lastMessageAt;
    private volatile int keepaliveSeconds = 10;
    private volatile ScheduledFuture<?> watchdog;

    public TwitchEventSubSocket(EngineRuntime rt, EventProcessor processor, String accessToken,
                                String refreshToken, String clientIdOverride,
                                BiConsumer<String, String> tokenPersister) {
        super("TwitchEventSub", rt, processor);
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.configuredClientId = clientIdOverride;
        this.tokenPersister = tokenPersister;
    }

    @Override
    protected void doConnect(int attempt) throws Exception {
        if (!validateToken() && !refreshAndValidate()) {
            throw new IllegalStateException("EventSub token invalid and refresh failed — regenerate at twitchtokengenerator.com");
        }
        connectWs(WS_URL);
    }

    private void connectWs(String url) throws Exception {
        Ws.connect(url, null, socket -> ws = socket, this::onMessage, reason -> onDisconnected(reason), this::onError).join();
        lastMessageAt = System.currentTimeMillis();
        if (watchdog != null) watchdog.cancel(false);
        watchdog = EXECUTOR.scheduleAtFixedRate(() -> {
            if (isClosed()) return;
            long silent = System.currentTimeMillis() - lastMessageAt;
            if (silent > (keepaliveSeconds + 15) * 1000L) {
                EngineRuntime.LOGGER.warn("[TwitchEventSub] keepalive lost ({}s silent)", silent / 1000);
                try {
                    doDisconnect();
                } catch (Exception ignored) {}
                onDisconnected("keepalive timeout");
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    @Override
    protected void doDisconnect() {
        if (watchdog != null) {
            watchdog.cancel(false);
            watchdog = null;
        }
        WebSocket w = ws;
        if (w != null) {
            w.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            ws = null;
        }
    }

    private void onMessage(String text) {
        lastMessageAt = System.currentTimeMillis();
        logEvent(text);
        JsonObject msg = JsonParser.parseString(text).getAsJsonObject();
        JsonObject metadata = msg.getAsJsonObject("metadata");
        JsonObject payload = msg.getAsJsonObject("payload");
        String type = metadata.get("message_type").getAsString();

        switch (type) {
            case "session_welcome" -> {
                JsonObject session = payload.getAsJsonObject("session");
                sessionId = session.get("id").getAsString();
                if (!session.get("keepalive_timeout_seconds").isJsonNull()) {
                    keepaliveSeconds = session.get("keepalive_timeout_seconds").getAsInt();
                }
                EXECUTOR.execute(this::createSubscriptions);
                onConnected();
            }
            case "session_keepalive" -> {}
            case "session_reconnect" -> {
                String url = payload.getAsJsonObject("session").get("reconnect_url").getAsString();
                EXECUTOR.execute(() -> {
                    try {
                        WebSocket old = ws;
                        connectWs(url);
                        if (old != null) old.sendClose(WebSocket.NORMAL_CLOSURE, "reconnect");
                    } catch (Exception e) {
                        onError(e);
                    }
                });
            }
            case "notification" -> handleNotification(metadata, payload);
            case "revocation" -> EngineRuntime.LOGGER.warn("[TwitchEventSub] subscription revoked: {}",
                    payload.getAsJsonObject("subscription").get("type").getAsString());
            default -> {}
        }
    }

    private void handleNotification(JsonObject metadata, JsonObject payload) {
        String subType = metadata.get("subscription_type").getAsString();
        JsonObject event = payload.getAsJsonObject("event");
        if (event == null) return;

        String family;
        String eventType;
        if (subType.equals("channel.raid")) {
            family = "channel.raid";
            JsonObject condition = payload.getAsJsonObject("subscription").getAsJsonObject("condition");
            eventType = condition != null && condition.has("to_broadcaster_user_id")
                    && !condition.get("to_broadcaster_user_id").isJsonNull() ? "incoming" : "outgoing";
        } else {
            int lastDot = subType.lastIndexOf('.');
            family = subType.substring(0, lastDot);
            eventType = subType.substring(lastDot + 1);
        }

        Map<String, Object> vars = JsonFlatten.flatten(event);
        dispatch("twitcheventsub", family, eventType, vars);
    }

    private void createSubscriptions() {
        int created = 0;
        for (String[] sub : SUBSCRIPTIONS) {
            JsonObject condition = new JsonObject();
            switch (sub[2]) {
                case "to" -> condition.addProperty("to_broadcaster_user_id", userId);
                case "from" -> condition.addProperty("from_broadcaster_user_id", userId);
                default -> {
                    condition.addProperty("broadcaster_user_id", userId);
                    if (sub[2].equals("mod")) condition.addProperty("moderator_user_id", userId);
                    if (sub[2].equals("user")) condition.addProperty("user_id", userId);
                }
            }
            JsonObject transport = new JsonObject();
            transport.addProperty("method", "websocket");
            transport.addProperty("session_id", sessionId);
            JsonObject body = new JsonObject();
            body.addProperty("type", sub[0]);
            body.addProperty("version", sub[1]);
            body.add("condition", condition);
            body.add("transport", transport);

            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(HELIX_SUBS))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Client-Id", clientId)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();
                HttpResponse<String> res = Ws.HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() / 100 == 2) {
                    created++;
                } else if (res.statusCode() != 403 && res.statusCode() != 400 && res.statusCode() != 409) {
                    EngineRuntime.LOGGER.warn("[TwitchEventSub] {} v{} → HTTP {}: {}", sub[0], sub[1], res.statusCode(),
                            res.body() == null ? "" : res.body().substring(0, Math.min(200, res.body().length())));
                }
            } catch (Exception e) {
                EngineRuntime.LOGGER.warn("[TwitchEventSub] failed to create {}: {}", sub[0], e.toString());
            }
        }
        EngineRuntime.LOGGER.info("[TwitchEventSub] created {} subscriptions for user {}", created, userId);
    }

    private boolean validateToken() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(VALIDATE))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "OAuth " + accessToken)
                    .GET().build();
            HttpResponse<String> res = Ws.HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return false;
            JsonObject obj = JsonParser.parseString(res.body()).getAsJsonObject();
            userId = obj.get("user_id").getAsString();
            clientId = configuredClientId != null && !configuredClientId.isEmpty()
                    ? configuredClientId : obj.get("client_id").getAsString();
            return true;
        } catch (Exception e) {
            EngineRuntime.LOGGER.warn("[TwitchEventSub] validate failed: {}", e.toString());
            return false;
        }
    }

    /** twitchtokengenerator.com refresh flow (their client id owns the token). */
    private boolean refreshAndValidate() {
        if (refreshToken == null || refreshToken.isEmpty()) return false;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(TTG_REFRESH + refreshToken))
                    .timeout(Duration.ofSeconds(15)).GET().build();
            HttpResponse<String> res = Ws.HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            JsonObject obj = JsonParser.parseString(res.body()).getAsJsonObject();
            if (!obj.has("success") || !obj.get("success").getAsBoolean()) return false;
            accessToken = obj.get("token").getAsString();
            if (obj.has("refresh") && !obj.get("refresh").isJsonNull()) {
                refreshToken = obj.get("refresh").getAsString();
            }
            EngineRuntime.LOGGER.info("[TwitchEventSub] token refreshed");
            if (tokenPersister != null) tokenPersister.accept(accessToken, refreshToken);
            return validateToken();
        } catch (Exception e) {
            EngineRuntime.LOGGER.warn("[TwitchEventSub] refresh failed: {}", e.toString());
            return false;
        }
    }
}
