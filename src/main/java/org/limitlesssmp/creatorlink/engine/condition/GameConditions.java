package org.limitlesssmp.creatorlink.engine.condition;

import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.EventContext;
import org.limitlesssmp.creatorlink.engine.Vars;

/** Conditions that query the running game via the GameBridge. */
public final class GameConditions {
    private GameConditions() {}

    static void registerAll() {
        ConditionRegistry.register("gameSettings", GameSettings.class);
        ConditionRegistry.register("inventory", Inventory.class);
        ConditionRegistry.register("playerState", PlayerState.class);
        ConditionRegistry.register("stats", Statistics.class);
        ConditionRegistry.register("statsRequest", RequestStatistics.class);
        ConditionRegistry.register("target", Target.class);
        ConditionRegistry.register("targetExists", TargetExists.class);
        ConditionRegistry.register("freeSpace", TwoHighSpace.class);
    }

    public static class GameSettings extends Condition {
        public String key;
        public String value;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String actual = rt.bridge().gameSettingGet(Vars.insert(key, ctx));
            return actual != null && actual.equals(Vars.insert(value, ctx));
        }
    }

    public static class Inventory extends Condition {
        public String target;
        public String index;
        public String variableName;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            int idx = (int) Vars.toDouble(index, ctx, -1);
            String result = rt.bridge().inventoryQuery(Vars.insert(target, ctx), idx);
            if (result == null) return false;
            ctx.set(Vars.insert(variableName, ctx), result);
            return true;
        }
    }

    public static class PlayerState extends Condition {
        public String state;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            Boolean result = rt.bridge().playerState(Vars.insert(state, ctx));
            return result != null && result;
        }
    }

    public static class Statistics extends Condition {
        public String statToCheck;
        public String variableName;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            Long value = rt.bridge().statistic(Vars.insert(statToCheck, ctx));
            if (value == null) return false;
            ctx.set(Vars.insert(variableName, ctx), value);
            return true;
        }
    }

    public static class RequestStatistics extends Condition {
        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            rt.bridge().requestStatistics();
            return true;
        }
    }

    public static class Target extends Condition {
        public String target;
        public String argument;
        public String variableName;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String result = rt.bridge().target(Vars.insert(target, ctx), Vars.insert(argument, ctx));
            if (result == null) return false;
            ctx.set(Vars.insert(variableName, ctx), result);
            return true;
        }
    }

    public static class TargetExists extends Condition {
        public String target;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            return rt.bridge().targetExists(Vars.insert(target, ctx));
        }
    }

    public static class TwoHighSpace extends Condition {
        public String target;
        public String horiRadius;
        public String vertRadius;
        public String tries;
        public String variableName;
        public String customHeight;
        public String customWidth;
        public String minHoriDistance;
        public boolean requireSolidBlock;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String result = rt.bridge().freeSpace(
                    Vars.insert(target, ctx),
                    Vars.toDouble(horiRadius, ctx, 0),
                    Vars.toDouble(vertRadius, ctx, 0),
                    (int) Vars.toDouble(tries, ctx, 8),
                    Vars.toDouble(customHeight, ctx, 2),
                    Vars.toDouble(customWidth, ctx, 1),
                    Vars.toDouble(minHoriDistance, ctx, 0),
                    requireSolidBlock);
            if (result == null) return false;
            ctx.set(Vars.insert(variableName, ctx), result);
            return true;
        }
    }
}
