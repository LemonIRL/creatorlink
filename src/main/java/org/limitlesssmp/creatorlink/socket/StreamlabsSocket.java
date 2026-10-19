package org.limitlesssmp.creatorlink.socket;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.EventProcessor;
import org.limitlesssmp.creatorlink.util.JsonFlatten;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Streamlabs socket API. Events arrive as {type, for, message:[...]}; each
 * element of message dispatches separately with its fields flattened.
 * Event type ids are Streamlabs' own (donation, follow, bits, subscription...).
 */
public final class StreamlabsSocket extends SocketBase {
    private final String token;
    private volatile SocketIoClient client;

    public StreamlabsSocket(EngineRuntime rt, EventProcessor processor, String token) {
        super("Streamlabs", rt, processor);
        this.token = token;
    }

    @Override
    protected void doConnect(int attempt) throws Exception {
        client = new SocketIoClient(
                "wss://sockets.streamlabs.com/socket.io/?token=" + token + "&EIO=3&transport=websocket",
                this::onEvent, this::onConnected, this::onDisconnected, this::onError);
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
        if (!name.equals("event") || args.isEmpty() || !args.get(0).isJsonObject()) return;
        JsonObject data = args.get(0).getAsJsonObject();
        String type = data.has("type") ? data.get("type").getAsString() : null;
        if (type == null) return;
        String platform = data.has("for") && !data.get("for").isJsonNull() ? data.get("for").getAsString() : null;

        JsonElement message = data.get("message");
        if (message != null && message.isJsonArray()) {
            for (JsonElement el : message.getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                Map<String, Object> vars = JsonFlatten.flatten(el.getAsJsonObject());
                dispatch("streamlabs", platform, type, vars);
            }
        } else if (message != null && message.isJsonObject()) {
            dispatch("streamlabs", platform, type, JsonFlatten.flatten(message.getAsJsonObject()));
        } else {
            dispatch("streamlabs", platform, type, new LinkedHashMap<>());
        }
    }
}
