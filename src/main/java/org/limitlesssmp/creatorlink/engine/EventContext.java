package org.limitlesssmp.creatorlink.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A processed stream event: the variables list plus routing info.
 * Values are String, Long, Double, Boolean, or List&lt;Object&gt; (arrays).
 */
public final class EventContext {
    public final String socketType;
    public final String platform;
    public final String eventType;
    private final Map<String, Object> vars = new LinkedHashMap<>();

    public EventContext(String socketType, String platform, String eventType) {
        this.socketType = socketType;
        this.platform = platform;
        this.eventType = eventType;
    }

    public Object get(String name) {
        return vars.get(name);
    }

    public String getString(String name) {
        Object v = vars.get(name);
        return v == null ? null : Vars.stringify(v);
    }

    public List<Object> getArray(String name) {
        Object v = vars.get(name);
        if (v instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> l = (List<Object>) list;
            return l;
        }
        return null;
    }

    public void set(String name, Object value) {
        vars.put(name, value);
    }

    public void setAll(Map<String, Object> values) {
        vars.putAll(values);
    }

    public Object remove(String name) {
        return vars.remove(name);
    }

    public boolean has(String name) {
        return vars.containsKey(name);
    }

    public Map<String, Object> varsView() {
        return Collections.unmodifiableMap(vars);
    }

    public EventContext copy() {
        EventContext c = new EventContext(socketType, platform, eventType);
        c.vars.putAll(vars);
        return c;
    }
}
