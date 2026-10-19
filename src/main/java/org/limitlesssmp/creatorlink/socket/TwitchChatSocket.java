package org.limitlesssmp.creatorlink.socket;

import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.EventProcessor;

import java.net.http.WebSocket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Twitch IRC over WebSocket, read-only via the anonymous justinfan login.
 * Chat messages dispatch as socket "chat", platform "twitch", type
 * "message"/"action", with all IRC tags exposed as variables (so
 * custom-reward-id channel point captures keep working).
 */
public final class TwitchChatSocket extends SocketBase {
    private static final String URL = "wss://irc-ws.chat.twitch.tv:443";

    private final List<String> channels;
    private volatile WebSocket ws;
    /** Attempt the live {@link #ws} belongs to, for callbacks raised from parsed lines. */
    private volatile int liveAttempt;

    public TwitchChatSocket(EngineRuntime rt, EventProcessor processor, List<String> channels) {
        super("TwitchChat", rt, processor);
        this.channels = channels;
    }

    @Override
    protected void doConnect(int attempt) throws Exception {
        // Every callback carries the attempt it was opened under. A connection
        // that has been replaced (RECONNECT, restart, parse error → reconnect)
        // is ignored and closed instead of joining the channel a second time —
        // otherwise two live IRC sessions would dispatch every chat line twice.
        WebSocket socket = Ws.connect(URL, null,
                s -> { if (isCurrent(attempt)) { ws = s; liveAttempt = attempt; } },
                raw -> { if (isCurrent(attempt)) onRaw(raw); },
                reason -> onDisconnected(attempt, reason),
                t -> onError(attempt, t)).join();
        if (!isCurrent(attempt)) {
            socket.abort();
            return;
        }
        String nick = "justinfan" + (10000 + rt.random.nextInt(80000));
        send("CAP REQ :twitch.tv/tags twitch.tv/commands");
        send("NICK " + nick);
        for (String chan : channels) {
            send("JOIN #" + chan.toLowerCase().replace("#", ""));
        }
        if (!onConnected(attempt)) socket.abort();
    }

    @Override
    protected void doDisconnect() {
        WebSocket w = ws;
        if (w != null) {
            ws = null;
            try {
                w.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } catch (Exception ignored) {}
            w.abort(); // don't wait on the server: a half-open socket still delivers chat
        }
    }

    private void send(String line) {
        WebSocket w = ws;
        if (w != null) w.sendText(line, true);
    }

    private void onRaw(String raw) {
        for (String line : raw.split("\r\n")) {
            if (line.isEmpty()) continue;
            logEvent(line);
            try {
                handleLine(line);
            } catch (Exception e) {
                // One odd line must not tear down (and re-open) the connection.
                EngineRuntime.LOGGER.warn("[TwitchChat] couldn't parse line: {} ({})", line, e.toString());
            }
        }
    }

    private void handleLine(String line) {
        if (line.startsWith("PING")) {
            send("PONG" + line.substring(4));
            return;
        }

        Map<String, Object> tags = new LinkedHashMap<>();
        String rest = line;
        if (rest.startsWith("@")) {
            int sp = rest.indexOf(' ');
            String tagBlock = rest.substring(1, sp);
            rest = rest.substring(sp + 1);
            for (String tag : tagBlock.split(";")) {
                int eq = tag.indexOf('=');
                if (eq < 0) continue;
                String value = tag.substring(eq + 1)
                        .replace("\\s", " ").replace("\\:", ";").replace("\\\\", "\\");
                tags.put(tag.substring(0, eq), value);
            }
        }

        String prefix = null;
        if (rest.startsWith(":")) {
            int sp = rest.indexOf(' ');
            prefix = rest.substring(1, sp);
            rest = rest.substring(sp + 1);
        }

        int trailingIdx = rest.indexOf(" :");
        String trailing = null;
        String middle = rest;
        if (trailingIdx >= 0) {
            trailing = rest.substring(trailingIdx + 2);
            middle = rest.substring(0, trailingIdx);
        }
        String[] parts = middle.split(" ");
        String command = parts[0];

        switch (command) {
            case "PRIVMSG" -> {
                String message = trailing == null ? "" : trailing;
                String type = "message";
                if (message.length() > 8 && message.startsWith("ACTION") && message.endsWith("")) {
                    type = "action";
                    message = message.substring(8, message.length() - 1).trim();
                }
                Map<String, Object> vars = new LinkedHashMap<>(tags);
                String login = prefix == null ? "" : prefix.substring(0, Math.max(prefix.indexOf('!'), 0));
                Object display = tags.get("display-name");
                vars.put("user", display == null || String.valueOf(display).isEmpty() ? login : display);
                vars.put("login", login);
                vars.put("message", message);
                if (parts.length > 1) vars.put("channel", parts[1].replace("#", ""));
                dispatch("chat", "twitch", type, vars);
            }
            case "USERNOTICE" -> {
                // subs/raids arriving via IRC; msg-id (sub, resub, raid, ...) becomes the event type
                Object msgId = tags.get("msg-id");
                if (msgId == null) return;
                Map<String, Object> vars = new LinkedHashMap<>(tags);
                Object display = tags.get("display-name");
                if (display != null) vars.put("user", display);
                if (trailing != null) vars.put("message", trailing);
                dispatch("chat", "twitch", String.valueOf(msgId), vars);
            }
            case "RECONNECT" -> onDisconnected(liveAttempt, "server requested reconnect");
            default -> {}
        }
    }
}
