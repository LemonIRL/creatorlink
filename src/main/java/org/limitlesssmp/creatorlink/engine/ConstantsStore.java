package org.limitlesssmp.creatorlink.engine;

import com.google.gson.annotations.SerializedName;
import org.limitlesssmp.creatorlink.engine.condition.Condition;
import org.limitlesssmp.creatorlink.engine.config.ConfigEvent;
import org.limitlesssmp.creatorlink.engine.outcome.Outcome;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Constants from constants.json: named Conditions, Outcomes, and Config Events
 * referenced via ConstantCondition/ConstantOutcome/Event.constantName.
 * File format: {"conditions": {...}, "outcomes": {...}, "events": {...}}
 */
public final class ConstantsStore {
    public static final class Data {
        @SerializedName("conditions") public Map<String, Condition> conditions = new LinkedHashMap<>();
        @SerializedName("outcomes") public Map<String, Outcome> outcomes = new LinkedHashMap<>();
        @SerializedName("events") public Map<String, ConfigEvent> events = new LinkedHashMap<>();
    }

    private volatile Data data = new Data();

    public void set(Data data) {
        this.data = data == null ? new Data() : data;
        if (this.data.conditions == null) this.data.conditions = new LinkedHashMap<>();
        if (this.data.outcomes == null) this.data.outcomes = new LinkedHashMap<>();
        if (this.data.events == null) this.data.events = new LinkedHashMap<>();
    }

    public Condition condition(String name) {
        return data.conditions.get(name);
    }

    public Outcome outcome(String name) {
        return data.outcomes.get(name);
    }

    public ConfigEvent event(String name) {
        return data.events.get(name);
    }
}
