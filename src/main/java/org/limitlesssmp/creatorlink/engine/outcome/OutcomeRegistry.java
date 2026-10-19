package org.limitlesssmp.creatorlink.engine.outcome;

import java.util.HashMap;
import java.util.Map;

/** Maps serialization ids ("command", "toast", "chat", ...) to outcome classes. */
public final class OutcomeRegistry {
    private static final Map<String, Class<? extends Outcome>> BY_ID = new HashMap<>();
    private static final Map<Class<?>, String> BY_CLASS = new HashMap<>();

    static {
        CoreOutcomes.registerAll();
        GameOutcomes.registerAll();
    }

    private OutcomeRegistry() {}

    public static void register(String id, Class<? extends Outcome> cls) {
        BY_ID.put(id, cls);
        BY_CLASS.put(cls, id);
    }

    public static Class<? extends Outcome> byId(String id) {
        return BY_ID.get(id);
    }

    public static String idOf(Class<?> cls) {
        return BY_CLASS.get(cls);
    }

    public static java.util.List<String> ids() {
        java.util.List<String> ids = new java.util.ArrayList<>(BY_ID.keySet());
        java.util.Collections.sort(ids);
        return ids;
    }

    public static Outcome create(String id) {
        Class<? extends Outcome> cls = BY_ID.get(id);
        if (cls == null) return null;
        try {
            return cls.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
