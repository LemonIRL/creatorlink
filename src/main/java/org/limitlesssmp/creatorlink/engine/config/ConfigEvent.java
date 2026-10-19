package org.limitlesssmp.creatorlink.engine.config;

import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.EventContext;
import org.limitlesssmp.creatorlink.engine.Vars;
import org.limitlesssmp.creatorlink.engine.condition.Condition;
import org.limitlesssmp.creatorlink.engine.outcome.Outcome;

import java.util.ArrayList;
import java.util.List;

/**
 * A Config Event: conditions + outcomes plus queue/delay/cooldown behaviour.
 * Field names match the serialized CCI format exactly.
 */
public final class ConfigEvent {
    public String name;
    public String constantName;
    public boolean disabled;
    public List<Condition> conditions;
    public List<Outcome> outcomes;
    public boolean triggersFromAnyConditionMet;
    public boolean allowsOtherEventsToTrigger;
    public boolean singleOutcomeOnly;
    public String playTime;
    public ConfigEvent playTimeEvent;
    public String delay;
    public String cooldown;
    public boolean disableShortCircuitWhenOnCooldown;

    /** Runtime cooldown bookkeeping; never serialized. */
    public transient long cooldownUntilTick = Long.MIN_VALUE;

    /**
     * Resolves constantName delegation: conditions/outcomes/flags come from the
     * constant event, while queue/playTime/delay/cooldown stay with this event.
     * Returns null if the referenced constant doesn't exist.
     */
    public ConfigEvent effective(EngineRuntime rt, EventContext ctx) {
        if (constantName == null || constantName.isEmpty()) return this;
        String resolved = Vars.insert(constantName, ctx);
        ConfigEvent constant = rt.constants.event(resolved);
        if (constant == null) {
            EngineRuntime.LOGGER.warn("Constant event '{}' not found", resolved);
        }
        return constant;
    }

    public boolean testConditions(EngineRuntime rt, EventContext ctx) {
        if (conditions == null || conditions.isEmpty()) return true;
        if (triggersFromAnyConditionMet) {
            for (Condition c : conditions) {
                if (c != null && c.test(rt, ctx)) return true;
            }
            return false;
        }
        for (Condition c : conditions) {
            if (c != null && !c.test(rt, ctx)) return false;
        }
        return true;
    }

    /** Requires at least one outcome to be considered valid, per spec. */
    public boolean valid() {
        return outcomes != null && !outcomes.isEmpty() || constantName != null && !constantName.isEmpty();
    }

    public boolean triggerOutcomes(EngineRuntime rt, EventContext ctx) {
        if (outcomes == null || outcomes.isEmpty()) return false;
        List<Outcome> live = new ArrayList<>();
        int totalWeight = 0;
        for (Outcome o : outcomes) {
            if (o == null || o.disabled) continue;
            live.add(o);
            if (o.weight > 0) totalWeight += o.weight;
        }
        if (live.isEmpty()) return false;
        if (totalWeight > 0) {
            int pick = rt.random.nextInt(totalWeight);
            for (Outcome o : live) {
                if (o.weight <= 0) continue;
                pick -= o.weight;
                if (pick < 0) return o.run(rt, ctx);
            }
            return false;
        }
        if (singleOutcomeOnly) {
            for (Outcome o : live) {
                if (o.run(rt, ctx)) return true;
            }
            return false;
        }
        boolean any = false;
        for (Outcome o : live) {
            any |= o.run(rt, ctx);
        }
        return any;
    }
}
