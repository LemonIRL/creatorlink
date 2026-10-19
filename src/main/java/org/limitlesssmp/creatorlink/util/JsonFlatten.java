package org.limitlesssmp.creatorlink.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flattens event payload JSON into the variables map the config format expects:
 * nested object keys join with "_" (event.reward.title → reward_title), arrays of
 * primitives become List variables, arrays of objects flatten with an index.
 */
public final class JsonFlatten {
    private JsonFlatten() {}

    public static Map<String, Object> flatten(JsonObject obj) {
        Map<String, Object> out = new LinkedHashMap<>();
        flattenInto(obj, "", out);
        return out;
    }

    public static void flattenInto(JsonObject obj, String prefix, Map<String, Object> out) {
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "_" + e.getKey();
            JsonElement v = e.getValue();
            if (v == null || v.isJsonNull()) continue;
            if (v.isJsonObject()) {
                flattenInto(v.getAsJsonObject(), key, out);
            } else if (v.isJsonArray()) {
                JsonArray arr = v.getAsJsonArray();
                boolean primitives = true;
                for (JsonElement el : arr) {
                    if (!el.isJsonPrimitive()) {
                        primitives = false;
                        break;
                    }
                }
                if (primitives) {
                    List<Object> list = new ArrayList<>(arr.size());
                    for (JsonElement el : arr) list.add(primitive(el.getAsJsonPrimitive()));
                    out.put(key, list);
                } else {
                    int i = 0;
                    for (JsonElement el : arr) {
                        if (el.isJsonObject()) {
                            flattenInto(el.getAsJsonObject(), key + "_" + i, out);
                        }
                        i++;
                    }
                    out.put(key + "_length", (long) arr.size());
                }
            } else if (v.isJsonPrimitive()) {
                out.put(key, primitive(v.getAsJsonPrimitive()));
            }
        }
    }

    /** JSON numbers keep integer form when integral so $vars insert as "5" not "5.0". */
    public static Object primitive(JsonPrimitive p) {
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isNumber()) {
            double d = p.getAsDouble();
            long l = (long) d;
            if (d == l && !p.getAsString().contains(".")) return l;
            return d;
        }
        return p.getAsString();
    }
}
