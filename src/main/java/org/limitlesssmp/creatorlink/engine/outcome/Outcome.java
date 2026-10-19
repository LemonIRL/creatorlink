package org.limitlesssmp.creatorlink.engine.outcome;

import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.EventContext;

/**
 * Base outcome. run() returns whether the outcome succeeded (used by
 * singleOutcomeOnly). weight enables weighted random selection when any
 * outcome in the pool has weight &gt; 0.
 */
public abstract class Outcome {
    public String displayName;
    public int weight;
    public boolean disabled;

    public final boolean run(EngineRuntime rt, EventContext ctx) {
        if (disabled) return false;
        try {
            return execute(rt, ctx);
        } catch (Exception e) {
            EngineRuntime.LOGGER.warn("Outcome {} threw: {}", getClass().getSimpleName(), e.toString());
            return false;
        }
    }

    protected abstract boolean execute(EngineRuntime rt, EventContext ctx);
}
