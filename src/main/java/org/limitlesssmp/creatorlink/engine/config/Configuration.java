package org.limitlesssmp.creatorlink.engine.config;

import com.google.gson.annotations.SerializedName;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Filters Events by platform ("for") and event type. Order of the types map
 * matters (top-to-bottom testing), so it must stay a LinkedHashMap.
 */
public final class Configuration {
    public LinkedHashMap<String, List<ConfigEvent>> types = new LinkedHashMap<>();
    public List<String> queue;
    public List<String> ignoredTypes;
    @SerializedName("for") public String forPlatform;

    /** Finds the queue definition (e.g. "host+subscription+bits") containing this event type. */
    public String queueFor(String eventType) {
        if (queue == null) return null;
        for (String q : queue) {
            if (q == null) continue;
            for (String part : q.split("\\+")) {
                if (part.equals(eventType)) return q;
            }
        }
        return null;
    }
}
