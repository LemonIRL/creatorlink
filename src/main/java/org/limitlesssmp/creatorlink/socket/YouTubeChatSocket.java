package org.limitlesssmp.creatorlink.socket;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.EventProcessor;
import org.limitlesssmp.creatorlink.util.JsonFlatten;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * YouTube live chat polling (Google API key + channel id). Messages dispatch
 * into the shared "chat" configuration with platform "youtube"; snippet fields
 * sit at the top level ($displayMessage) and author fields under
 * $authorDetails_* to match existing configs.
 */
public final class YouTubeChatSocket extends SocketBase {
    private static final String API = "https://www.googleapis.com/youtube/v3";

    private final String apiKey;
    private final String channelId;
    private volatile String liveChatId;
    private volatile String videoId;
    private volatile String pageToken;
    private volatile ScheduledFuture<?> poller;
    private volatile boolean primed; // skip history on first poll
    private volatile long lastLikes = -1;
    private volatile long lastStatsMs;

    public YouTubeChatSocket(EngineRuntime rt, EventProcessor processor, String apiKey, String channelId) {
        super("YouTubeChat", rt, processor);
        this.apiKey = apiKey;
        this.channelId = channelId;
    }

    @Override
    protected void doConnect(int attempt) throws Exception {
        videoId = findLiveVideo();
        if (videoId == null) {
            throw new IllegalStateException("No active live stream found for channel " + channelId);
        }
        liveChatId = findLiveChatId(videoId);
        if (liveChatId == null) {
            throw new IllegalStateException("Live stream " + videoId + " has no active chat");
        }
        pageToken = null;
        primed = false;
        lastLikes = -1;
        lastStatsMs = 0;
        onConnected();
        schedulePoll(0);
    }

    @Override
    protected void doDisconnect() {
        ScheduledFuture<?> p = poller;
        if (p != null) {
            p.cancel(false);
            poller = null;
        }
    }

    private void schedulePoll(long delayMillis) {
        if (isClosed()) return;
        poller = EXECUTOR.schedule(this::poll, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void poll() {
        if (isClosed()) return;
        try {
            StringBuilder url = new StringBuilder(API + "/liveChat/messages?part=snippet,authorDetails&liveChatId="
                    + enc(liveChatId) + "&key=" + enc(apiKey));
            if (pageToken != null) url.append("&pageToken=").append(enc(pageToken));
            JsonObject res = getJson(url.toString());
            pageToken = res.has("nextPageToken") ? res.get("nextPageToken").getAsString() : null;
            long interval = res.has("pollingIntervalMillis") ? res.get("pollingIntervalMillis").getAsLong() : 5000;

            if (res.has("items")) {
                JsonArray items = res.getAsJsonArray("items");
                if (primed) {
                    for (JsonElement el : items) {
                        if (!el.isJsonObject()) continue;
                        JsonObject item = el.getAsJsonObject();
                        JsonObject snippet = item.getAsJsonObject("snippet");
                        if (snippet == null) continue;
                        Map<String, Object> vars = new LinkedHashMap<>(JsonFlatten.flatten(snippet));
                        JsonObject author = item.getAsJsonObject("authorDetails");
                        if (author != null) JsonFlatten.flattenInto(author, "authorDetails", vars);
                        String type = snippet.has("type") ? snippet.get("type").getAsString() : "textMessageEvent";
                        vars.putIfAbsent("user", vars.get("authorDetails_displayName"));
                        vars.putIfAbsent("message", vars.get("displayMessage"));
                        dispatch("chat", "youtube", type, vars);
                    }
                }
                primed = true;
            }
            if (res.has("offlineAt")) {
                onDisconnected("stream went offline");
                return;
            }
            // like counter: cheap (1 quota unit) once a minute; fires "likes" events on increases
            if (System.currentTimeMillis() - lastStatsMs >= 60_000) {
                lastStatsMs = System.currentTimeMillis();
                pollLikes();
            }
            schedulePoll(Math.max(interval, 2000));
        } catch (Exception e) {
            onError(e);
        }
    }

    private void pollLikes() {
        try {
            JsonObject res = getJson(API + "/videos?part=statistics&id=" + enc(videoId) + "&key=" + enc(apiKey));
            if (!res.has("items") || res.getAsJsonArray("items").isEmpty()) return;
            JsonObject stats = res.getAsJsonArray("items").get(0).getAsJsonObject().getAsJsonObject("statistics");
            if (stats == null || !stats.has("likeCount")) return;
            long likes = Long.parseLong(stats.get("likeCount").getAsString());
            if (lastLikes >= 0 && likes > lastLikes) {
                Map<String, Object> vars = new LinkedHashMap<>();
                vars.put("likes", String.valueOf(likes));
                vars.put("newLikes", String.valueOf(likes - lastLikes));
                dispatch("chat", "youtube", "likes", vars);
            }
            lastLikes = likes;
        } catch (Exception e) {
            EngineRuntime.LOGGER.warn("[YouTubeChat] like poll failed: {}", e.toString());
        }
    }

    private String findLiveVideo() throws Exception {
        JsonObject res = getJson(API + "/search?part=id&eventType=live&type=video&channelId=" + enc(channelId) + "&key=" + enc(apiKey));
        if (!res.has("items")) return null;
        for (JsonElement el : res.getAsJsonArray("items")) {
            JsonObject id = el.getAsJsonObject().getAsJsonObject("id");
            if (id != null && id.has("videoId")) return id.get("videoId").getAsString();
        }
        return null;
    }

    private String findLiveChatId(String videoId) throws Exception {
        JsonObject res = getJson(API + "/videos?part=liveStreamingDetails&id=" + enc(videoId) + "&key=" + enc(apiKey));
        if (!res.has("items")) return null;
        for (JsonElement el : res.getAsJsonArray("items")) {
            JsonObject details = el.getAsJsonObject().getAsJsonObject("liveStreamingDetails");
            if (details != null && details.has("activeLiveChatId")) {
                return details.get("activeLiveChatId").getAsString();
            }
        }
        return null;
    }

    private static JsonObject getJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();
        HttpResponse<String> res = Ws.HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IllegalStateException("YouTube API HTTP " + res.statusCode() + ": "
                    + res.body().substring(0, Math.min(200, res.body().length())));
        }
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
