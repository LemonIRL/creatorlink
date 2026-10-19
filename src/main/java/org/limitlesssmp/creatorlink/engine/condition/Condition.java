package org.limitlesssmp.creatorlink.engine.condition;

import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.EventContext;

/**
 * Base condition. "Unconditional" conditions perform work (math, string ops,
 * variable manipulation) and always pass. inverseMatch flips the result.
 */
public abstract class Condition {
    public String displayName;
    public boolean inverseMatch;

    public final boolean test(EngineRuntime rt, EventContext ctx) {
        boolean result;
        try {
            result = check(rt, ctx);
        } catch (Exception e) {
            EngineRuntime.LOGGER.warn("Condition {} threw: {}", getClass().getSimpleName(), e.toString());
            result = false;
        }
        return inverseMatch != result;
    }

    protected abstract boolean check(EngineRuntime rt, EventContext ctx);
}
