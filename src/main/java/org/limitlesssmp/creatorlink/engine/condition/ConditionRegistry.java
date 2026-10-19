package org.limitlesssmp.creatorlink.engine.condition;

import java.util.HashMap;
import java.util.Map;

/** Maps serialization ids ("stringEquals", "variableCheck", ...) to condition classes. */
public final class ConditionRegistry {
    private static final Map<String, Class<? extends Condition>> BY_ID = new HashMap<>();
    private static final Map<Class<?>, String> BY_CLASS = new HashMap<>();

    static {
        CoreConditions.registerAll();
        StringConditions.registerAll();
        ArrayConditions.registerAll();
        MathConditions.registerAll();
        GameConditions.registerAll();
    }

    private ConditionRegistry() {}

    public static void register(String id, Class<? extends Condition> cls) {
        BY_ID.put(id, cls);
        BY_CLASS.put(cls, id);
    }

    public static Class<? extends Condition> byId(String id) {
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

    public static Condition create(String id) {
        Class<? extends Condition> cls = BY_ID.get(id);
        if (cls == null) return null;
        try {
            return cls.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
