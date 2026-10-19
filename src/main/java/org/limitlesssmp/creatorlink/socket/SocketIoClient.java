package org.limitlesssmp.creatorlink.socket;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.limitlesssmp.creatorlink.engine.EngineRuntime;

import java.net.http.WebSocket;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Minimal Engine.IO v3 + Socket.IO v2 client over the JDK WebSocket —
 * enough for Streamlabs, StreamElements and DonationAlerts. Packet framing:
 * <engine.io digit><socket.io digit><json>. We answer server pings and send
 * our own on the negotiated interval (EIO3 is client-initiated).
 */
public final class SocketIoClient {
    private final String url;
    private final BiConsumer<String, JsonArray> onEvent;
    private final Runnable onConnect;
    private final Consumer<String> onDisconnect;
    private final Consumer<Throwable> onError;

    private volatile WebSocket ws;
    private volatile ScheduledFuture<?> pinger;
    private volatile boolean closed;

    public SocketIoClient(String url, BiConsumer<String, JsonArray> onEvent,
                          Runnable onConnect, Consumer<String> onDisconnect, Consumer<Throwable> onError) {
        this.url = url;
        this.onEvent = onEvent;
        this.onConnect = onConnect;
        this.onDisconnect = onDisconnect;
        this.onError = onError;
    }

    public void connect() throws Exception {
        closed = false;
        // ws must be assigned via onOpen: the server's open/connect frames can
        // arrive before join() returns, and onConnect replies immediately.
        Ws.connect(url, null, socket -> ws = socket, this::onMessage, reason -> {
            stopPinger();
            if (!closed) onDisconnect.accept(reason);
        }, t -> {
            stopPinger();
            if (!closed) onError.accept(t);
        }).join();
    }

    public void close() {
        closed = true;
        stopPinger();
        WebSocket w = ws;
        if (w != null) {
            w.sendText("41", true); // socket.io disconnect
            w.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            ws = null;
        }
    }

    public void emit(String event, JsonElement data) {
        JsonArray arr = new JsonArray();
        arr.add(event);
        if (data != null) arr.add(data);
        send("42" + arr);
    }

    private void send(String packet) {
        WebSocket w = ws;
        if (w != null) w.sendText(packet, true);
    }

    private void stopPinger() {
        ScheduledFuture<?> p = pinger;
        if (p != null) {
            p.cancel(false);
            pinger = null;
        }
    }

    private void onMessage(String packet) {
        if (packet.isEmpty()) return;
        char eio = packet.charAt(0);
        String rest = packet.substring(1);
        switch (eio) {
            case '0' -> { // open: negotiate ping interval
                try {
                    JsonObject open = JsonParser.parseString(rest).getAsJsonObject();
                    long interval = open.has("pingInterval") ? open.get("pingInterval").getAsLong() : 25000;
                    stopPinger();
                    pinger = SocketBase.EXECUTOR.scheduleAtFixedRate(() -> send("2"),
                            interval, interval, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    EngineRuntime.LOGGER.warn("socket.io open parse failed: {}", e.toString());
                }
            }
            case '2' -> send("3"); // server ping (EIO4 style) → pong
            case '3' -> {}          // pong for our ping
            case '1' -> {           // engine.io close
                stopPinger();
                if (!closed) onDisconnect.accept("engine.io close");
            }
            case '4' -> onSocketIo(rest);
            default -> {}
        }
    }

    private void onSocketIo(String data) {
        if (data.isEmpty()) return;
        char sio = data.charAt(0);
        String body = data.substring(1);
        switch (sio) {
            case '0' -> onConnect.run();     // connected to namespace
            case '1' -> {                     // namespace disconnect
                if (!closed) onDisconnect.accept("socket.io disconnect");
            }
            case '2' -> {                     // event: [name, args...]
                // strip an optional ack id before the JSON array
                int arrStart = body.indexOf('[');
                if (arrStart < 0) return;
                try {
                    JsonArray arr = JsonParser.parseString(body.substring(arrStart)).getAsJsonArray();
                    if (arr.isEmpty()) return;
                    String name = arr.get(0).getAsString();
                    JsonArray args = new JsonArray();
                    for (int i = 1; i < arr.size(); i++) args.add(arr.get(i));
                    onEvent.accept(name, args);
                } catch (Exception e) {
                    EngineRuntime.LOGGER.warn("socket.io event parse failed: {}", e.toString());
                }
            }
            case '4' -> {
                if (!closed) onError.accept(new IllegalStateException("socket.io error: " + body));
            }
            default -> {}
        }
    }
}
