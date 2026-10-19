package org.limitlesssmp.creatorlink.client.editor;

import org.limitlesssmp.creatorlink.engine.condition.Condition;
import org.limitlesssmp.creatorlink.engine.condition.ConditionRegistry;
import org.limitlesssmp.creatorlink.engine.config.ConfigEvent;
import org.limitlesssmp.creatorlink.engine.config.Configuration;
import org.limitlesssmp.creatorlink.engine.config.EventConfiguration;
import org.limitlesssmp.creatorlink.engine.outcome.Outcome;
import org.limitlesssmp.creatorlink.engine.outcome.OutcomeRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reflection-driven view over the config object graph: which fields an object
 * has (for the fields pane) and which children it has (for the navigator tree).
 * Works for every Condition/Outcome subclass automatically.
 */
public final class EditorModel {
    public enum Kind { STRING, BOOL, INT, DOUBLE, STRING_LIST, OBJECT, OBJECT_LIST, TYPES_MAP, UNSUPPORTED }

    /** One editable field of an object. */
    public static final class FieldRef {
        public final Object owner;
        public final Field field;
        public final Kind kind;
        public final Class<?> elementType; // for OBJECT/OBJECT_LIST: Condition, Outcome, or ConfigEvent

        FieldRef(Object owner, Field field, Kind kind, Class<?> elementType) {
            this.owner = owner;
            this.field = field;
            this.kind = kind;
            this.elementType = elementType;
        }

        public String name() {
            return field.getName().equals("forPlatform") ? "for" : field.getName();
        }

        public Object get() {
            try {
                return field.get(owner);
            } catch (IllegalAccessException e) {
                return null;
            }
        }

        public void set(Object value) {
            try {
                field.set(owner, value);
            } catch (IllegalAccessException ignored) {}
        }

        @SuppressWarnings("unchecked")
        public List<Object> list() {
            Object v = get();
            if (v == null) {
                v = new ArrayList<>();
                set(v);
            }
            return (List<Object>) v;
        }

        @SuppressWarnings("unchecked")
        public Map<String, List<ConfigEvent>> typesMap() {
            Object v = get();
            if (v == null) {
                v = new LinkedHashMap<String, List<ConfigEvent>>();
                set(v);
            }
            return (Map<String, List<ConfigEvent>>) v;
        }
    }

    private EditorModel() {}

    public static List<FieldRef> fieldsOf(Object obj) {
        List<FieldRef> out = new ArrayList<>();
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                int mods = f.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isTransient(mods)) continue;
                f.setAccessible(true);
                out.add(classify(obj, f));
            }
            cls = cls.getSuperclass();
        }
        return out;
    }

    private static FieldRef classify(Object owner, Field f) {
        Class<?> t = f.getType();
        if (t == String.class) return new FieldRef(owner, f, Kind.STRING, null);
        if (t == boolean.class || t == Boolean.class) return new FieldRef(owner, f, Kind.BOOL, null);
        if (t == int.class || t == Integer.class) return new FieldRef(owner, f, Kind.INT, null);
        if (t == double.class || t == Double.class) return new FieldRef(owner, f, Kind.DOUBLE, null);
        if (Condition.class.isAssignableFrom(t)) return new FieldRef(owner, f, Kind.OBJECT, Condition.class);
        if (Outcome.class.isAssignableFrom(t)) return new FieldRef(owner, f, Kind.OBJECT, Outcome.class);
        if (t == ConfigEvent.class) return new FieldRef(owner, f, Kind.OBJECT, ConfigEvent.class);
        if (Map.class.isAssignableFrom(t)) return new FieldRef(owner, f, Kind.TYPES_MAP, ConfigEvent.class);
        if (List.class.isAssignableFrom(t)) {
            Class<?> el = listElementType(f);
            if (el == String.class) return new FieldRef(owner, f, Kind.STRING_LIST, null);
            if (el != null && (Condition.class.isAssignableFrom(el) || Outcome.class.isAssignableFrom(el) || el == ConfigEvent.class)) {
                Class<?> base = Condition.class.isAssignableFrom(el) ? Condition.class
                        : Outcome.class.isAssignableFrom(el) ? Outcome.class : ConfigEvent.class;
                return new FieldRef(owner, f, Kind.OBJECT_LIST, base);
            }
        }
        return new FieldRef(owner, f, Kind.UNSUPPORTED, null);
    }

    private static Class<?> listElementType(Field f) {
        Type g = f.getGenericType();
        if (g instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 1) {
            Type a = pt.getActualTypeArguments()[0];
            if (a instanceof Class<?> c) return c;
        }
        return null;
    }

    /** Creates a fresh instance of a pickable object type. */
    public static Object create(Class<?> base, String id) {
        if (base == Condition.class) return ConditionRegistry.create(id);
        if (base == Outcome.class) return OutcomeRegistry.create(id);
        if (base == ConfigEvent.class) return new ConfigEvent();
        return null;
    }

    public static List<String> idsFor(Class<?> base) {
        if (base == Condition.class) return ConditionRegistry.ids();
        if (base == Outcome.class) return OutcomeRegistry.ids();
        return List.of("event");
    }

    /** CCI-style picker entries ("chat — ChatOutcome"); the id before " — " is what create() takes. */
    public static List<String> pickList(Class<?> base) {
        List<String> out = new ArrayList<>();
        String kind = base == Condition.class ? "Condition" : "Outcome";
        for (String id : idsFor(base)) {
            Class<?> cls = base == Condition.class ? ConditionRegistry.byId(id)
                    : base == Outcome.class ? OutcomeRegistry.byId(id) : null;
            out.add(cls == null ? id : id + " — " + cls.getSimpleName() + kind);
        }
        return out;
    }

    /** Display label for a tree node. */
    public static String label(Object obj) {
        if (obj instanceof EventConfiguration) return "EventConfiguration";
        if (obj instanceof Configuration c) {
            return "Configuration [" + (c.forPlatform == null ? "all" : c.forPlatform) + "]";
        }
        if (obj instanceof ConfigEvent e) {
            if (e.name != null && !e.name.isEmpty()) return "Event: " + e.name;
            if (e.constantName != null && !e.constantName.isEmpty()) return "Event → " + e.constantName;
            return "Event";
        }
        if (obj instanceof Condition c) {
            String id = ConditionRegistry.idOf(c.getClass());
            return (id == null ? c.getClass().getSimpleName() : id)
                    + (c.displayName != null && !c.displayName.isEmpty() ? ": " + c.displayName : "");
        }
        if (obj instanceof Outcome o) {
            String id = OutcomeRegistry.idOf(o.getClass());
            String warn = o instanceof org.limitlesssmp.creatorlink.engine.outcome.GameOutcomes.Command c
                    && CommandLint.legacyReason(c.command) != null ? " §c⚠ old-version command" : "";
            return (id == null ? o.getClass().getSimpleName() : id)
                    + (o.displayName != null && !o.displayName.isEmpty() ? ": " + o.displayName : "") + warn;
        }
        return String.valueOf(obj);
    }

    /** An Event needs at least one outcome (or a constant reference) to be valid. */
    public static boolean isValid(Object obj) {
        if (obj instanceof ConfigEvent e) return e.valid();
        return true;
    }
}
