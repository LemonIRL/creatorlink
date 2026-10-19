package org.limitlesssmp.creatorlink.socket;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.EventProcessor;
import org.limitlesssmp.creatorlink.util.JsonFlatten;

import java.util.Map;

/**
 * DonationAlerts alert widget socket. After connect we register with the
 * widget token; donations arrive on the "donation" event (JSON as a string).
 */
public final class DonationAlertsSocket extends SocketBase {
    private final String token;
    private volatile SocketIoClient client;

    public DonationAlertsSocket(EngineRuntime rt, EventProcessor processor, String token) {
        super("DonationAlerts", rt, processor);
        this.token = token;
    }

    @Override
    protected void doConnect(int attempt) throws Exception {
        client = new SocketIoClient(
                "wss://socket.donationalerts.ru:443/socket.io/?EIO=3&transport=websocket",
                this::onEvent,
                () -> {
                    JsonObject add = new JsonObject();
                    add.addProperty("token", token);
                    add.addProperty("type", "alert_widget");
                    client.emit("add-user", add);
                    onConnected();
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
        if (!name.equals("donation") || args.isEmpty()) return;
        try {
            JsonObject data = args.get(0).isJsonObject()
                    ? args.get(0).getAsJsonObject()
                    : JsonParser.parseString(args.get(0).getAsString()).getAsJsonObject();
            Map<String, Object> vars = JsonFlatten.flatten(data);
            if (!vars.containsKey("user") && vars.containsKey("username")) {
                vars.put("user", vars.get("username"));
            }
            dispatch("donationalerts", null, "donation", vars);
        } catch (Exception e) {
            EngineRuntime.LOGGER.warn("[DonationAlerts] bad donation payload: {}", e.toString());
        }
    }
}
