package org.limitlesssmp.creatorlink.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.limitlesssmp.creatorlink.engine.condition.Condition;
import org.limitlesssmp.creatorlink.engine.condition.ConditionRegistry;
import org.limitlesssmp.creatorlink.engine.config.EventConfiguration;
import org.limitlesssmp.creatorlink.engine.outcome.Outcome;
import org.limitlesssmp.creatorlink.engine.outcome.OutcomeRegistry;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

/**
 * Gson wiring for the CCI-compatible config format. Conditions and Outcomes
 * are polymorphic on their "type" field. Keys written with a leading
 * underscore (the old documented style, e.g. "_type") are accepted too.
 */
public final class ConfigIO {
    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter(Condition.class, new PolyCodec<>(ConditionRegistry::byId, ConditionRegistry::idOf, "condition"))
            .registerTypeAdapter(Outcome.class, new PolyCodec<>(OutcomeRegistry::byId, OutcomeRegistry::idOf, "outcome"))
            .create();

    private ConfigIO() {}

    public static EventConfiguration loadEventConfiguration(Path file) {
        try {
            JsonElement root = com.google.gson.JsonParser.parseString(Files.readString(file));
            EventConfiguration cfg = GSON.fromJson(normalizeTree(root), EventConfiguration.class);
            prune(cfg);
            return cfg;
        } catch (Exception e) {
            EngineRuntime.LOGGER.error("Failed to load {}: {}", file, e.toString());
            return null;
        }
    }

    /**
     * Old CCI builds wrote every key with a leading underscore ("_configs",
     * "_for", "_outcomes"); PolyCodec only sees condition/outcome objects, so
     * normalize the whole tree up front or such files parse to an empty config.
     */
    private static JsonElement normalizeTree(JsonElement el) {
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            JsonObject out = new JsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                String key = e.getKey();
                if (key.startsWith("_") && key.length() > 1 && !obj.has(key.substring(1))) {
                    key = key.substring(1);
                }
                out.add(key, normalizeTree(e.getValue()));
            }
            return out;
        }
        if (el.isJsonArray()) {
            com.google.gson.JsonArray out = new com.google.gson.JsonArray();
            for (JsonElement e : el.getAsJsonArray()) out.add(normalizeTree(e));
            return out;
        }
        return el;
    }

    /** Unknown condition/outcome types deserialize to null — drop them so the engine never trips on one. */
    private static void prune(EventConfiguration cfg) {
        if (cfg == null) return;
        if (cfg.configs != null) {
            cfg.configs.removeIf(java.util.Objects::isNull);
            for (var c : cfg.configs) {
                if (c.types == null) continue;
                for (var events : c.types.values()) {
                    if (events == null) continue;
                    events.removeIf(java.util.Objects::isNull);
                    for (var ev : events) {
                        if (ev.conditions != null) ev.conditions.removeIf(java.util.Objects::isNull);
                        if (ev.outcomes != null) ev.outcomes.removeIf(java.util.Objects::isNull);
                    }
                }
            }
        }
        if (cfg.init != null) {
            cfg.init.removeIf(java.util.Objects::isNull);
            for (var ev : cfg.init) {
                if (ev.conditions != null) ev.conditions.removeIf(java.util.Objects::isNull);
                if (ev.outcomes != null) ev.outcomes.removeIf(java.util.Objects::isNull);
            }
        }
    }

    public static ConstantsStore.Data loadConstants(Path file) {
        try {
            if (!Files.exists(file)) return new ConstantsStore.Data();
            return GSON.fromJson(Files.readString(file), ConstantsStore.Data.class);
        } catch (Exception e) {
            EngineRuntime.LOGGER.error("Failed to load {}: {}", file, e.toString());
            return new ConstantsStore.Data();
        }
    }

    private static final class PolyCodec<T> implements JsonDeserializer<T>, JsonSerializer<T> {
        private final Function<String, Class<? extends T>> byId;
        private final Function<Class<?>, String> idOf;
        private final String kind;

        PolyCodec(Function<String, Class<? extends T>> byId, Function<Class<?>, String> idOf, String kind) {
            this.byId = byId;
            this.idOf = idOf;
            this.kind = kind;
        }

        @Override
        public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (!json.isJsonObject()) throw new JsonParseException("Expected object for " + kind);
            JsonObject obj = normalizeUnderscoreKeys(json.getAsJsonObject());
            JsonElement typeEl = obj.get("type");
            String id = typeEl == null ? null : typeEl.getAsString();
            Class<? extends T> cls = id == null ? null : byId.apply(id);
            if (cls == null) {
                EngineRuntime.LOGGER.warn("Unknown {} type '{}' — skipping", kind, id);
                return null;
            }
            return context.deserialize(obj, cls);
        }

        @Override
        public JsonElement serialize(T src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = context.serialize(src, src.getClass()).getAsJsonObject();
            String id = idOf.apply(src.getClass());
            if (id != null) obj.addProperty("type", id);
            return obj;
        }

        private static JsonObject normalizeUnderscoreKeys(JsonObject obj) {
            JsonObject out = new JsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                String key = e.getKey();
                if (key.startsWith("_") && key.length() > 1 && !obj.has(key.substring(1))) {
                    key = key.substring(1);
                }
                out.add(key, e.getValue());
            }
            return out;
        }
    }
}
