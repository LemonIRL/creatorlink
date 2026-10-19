package org.limitlesssmp.creatorlink.socket;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.EventProcessor;
import org.limitlesssmp.creatorlink.util.JsonFlatten;

import java.util.Map;

/**
 * StreamElements realtime socket (JWT auth). Events arrive as
 * {type, provider, data:{...}}; data flattens to variables with the
 * conventional aliases ($user, $amount, $message) on top.
 */
public final class StreamElementsSocket extends SocketBase {
    private final String jwt;
    private volatile SocketIoClient client;

    public StreamElementsSocket(EngineRuntime rt, EventProcessor processor, String jwt) {
        super("StreamElements", rt, processor);
        this.jwt = jwt;
    }

    @Override
    protected void doConnect(int attempt) throws Exception {
        client = new SocketIoClient(
                "wss://realtime.streamelements.com/socket.io/?EIO=3&transport=websocket",
                this::onEvent,
                () -> {
                    JsonObject auth = new JsonObject();
                    auth.addProperty("method", "jwt");
                    auth.addProperty("token", jwt);
                    client.emit("authenticate", auth);
                },
                this::onDisconnected, this::onError);
        client.connect();
    }

    @Override
    protected void doDisconnect() {
        SocketIoClient c = client;
        if (c != null) {
            c.close();
            client = null;
        }
    }

    private void onEvent(String name, JsonArray args) {
        logEvent(name + " " + args);
        switch (name) {
            case "authenticated" -> onConnected();
            case "unauthorized" -> onError(new IllegalStateException("StreamElements JWT rejected"));
            case "event", "event:test", "event:update" -> {
                if (args.isEmpty() || !args.get(0).isJsonObject()) return;
                JsonObject obj = args.get(0).getAsJsonObject();
                // test/replay events wrap the payload in {listener, event}; event may be
                // an object or an array, and listener names differ from live type ids
                // (follower-latest → live type "follow").
                if (obj.has("listener") && obj.has("event")) {
                    String type = listenerToType(obj.get("listener").getAsString());
                    var eventEl = obj.get("event");
                    if (eventEl.isJsonObject()) {
                        dispatchEvent(type, null, eventEl.getAsJsonObject());
                    } else if (eventEl.isJsonArray()) {
                        for (var el : eventEl.getAsJsonArray()) {
                            if (el.isJsonObject()) dispatchEvent(type, null, el.getAsJsonObject());
                        }
                    }
                    return;
                }
                String type = obj.has("type") ? obj.get("type").getAsString() : null;
                if (type == null) return;
                String provider = obj.has("provider") && !obj.get("provider").isJsonNull()
                        ? obj.get("provider").getAsString() : null;
                JsonObject data = obj.has("data") && obj.get("data").isJsonObject()
                        ? obj.getAsJsonObject("data") : new JsonObject();
                dispatchEvent(type, provider, data);
            }
            default -> {}
        }
    }

    /** Maps test-event listener names to the type ids live events use. */
    private static String listenerToType(String listener) {
        String type = listener.replace("-latest", "");
        return switch (type) {
            case "follower" -> "follow";
            case "subscriber" -> "subscriber";
            default -> type;
        };
    }

    private void dispatchEvent(String type, String provider, JsonObject data) {
        Map<String, Object> vars = JsonFlatten.flatten(data);
        // conventional aliases used by existing configs
        if (!vars.containsKey("user")) {
            Object user = vars.getOrDefault("displayName", vars.get("username"));
            if (user != null) vars.put("user", user);
        }
        dispatch("streamelements", provider, type, vars);
    }
}
