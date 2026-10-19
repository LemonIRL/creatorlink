package org.limitlesssmp.creatorlink.engine.condition;

import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.EventContext;
import org.limitlesssmp.creatorlink.engine.Vars;

import java.util.ArrayList;
import java.util.List;

/** Conditions operating on array variables. */
public final class ArrayConditions {
    private ArrayConditions() {}

    static void registerAll() {
        ConditionRegistry.register("array", Contains.class);
        ConditionRegistry.register("arrayAppend", Append.class);
        ConditionRegistry.register("arrayCombine", Combine.class);
        ConditionRegistry.register("arrayDelete", Delete.class);
        ConditionRegistry.register("arrayExplode", Explode.class);
        ConditionRegistry.register("arrayLength", Length.class);
    }

    public static class Contains extends Condition {
        public String variableName;
        public String variableResult;
        public boolean caseSensitive;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            List<Object> array = ctx.getArray(Vars.insert(variableName, ctx));
            if (array == null) return false;
            String expected = Vars.insert(variableResult, ctx);
            if (expected == null) return false;
            for (Object o : array) {
                String s = Vars.stringify(o);
                if (caseSensitive ? s.equals(expected) : s.equalsIgnoreCase(expected)) return true;
            }
            return false;
        }
    }

    public static class Append extends Condition {
        public String variableName;
        public String stringToAdd;
        public boolean create;
        public boolean isGlobal;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String name = Vars.insert(variableName, ctx);
            List<Object> array = ctx.getArray(name);
            if (array == null) {
                if (!create) return false;
                array = new ArrayList<>();
                ctx.set(name, array);
            }
            array.add(Vars.insert(stringToAdd, ctx));
            if (isGlobal) rt.setGlobal(name, array);
            return true;
        }
    }

    public static class Combine extends Condition {
        public String variableName1;
        public String variableName2;
        public String variableOutputName;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            List<Object> a = ctx.getArray(Vars.insert(variableName1, ctx));
            List<Object> b = ctx.getArray(Vars.insert(variableName2, ctx));
            if (a == null || b == null) return false;
            List<Object> out = new ArrayList<>(a);
            out.addAll(b);
            ctx.set(Vars.insert(variableOutputName, ctx), out);
            return true;
        }
    }

    public static class Delete extends Condition {
        public String variableName;
        public String stringToRemove;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            List<Object> array = ctx.getArray(Vars.insert(variableName, ctx));
            if (array == null) return false;
            String remove = Vars.insert(stringToRemove, ctx);
            return array.removeIf(o -> Vars.stringify(o).equals(remove));
        }
    }

    public static class Explode extends Condition {
        public String variableName;
        public String variableOutputName;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            List<Object> array = ctx.getArray(Vars.insert(variableName, ctx));
            if (array == null) return false;
            String out = Vars.insert(variableOutputName, ctx);
            ctx.set(out + "Length", (long) array.size());
            for (int i = 0; i < array.size(); i++) {
                ctx.set(out + i, array.get(i));
            }
            return true;
        }
    }

    public static class Length extends Condition {
        public String variableName;
        public String variableOutputName;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            List<Object> array = ctx.getArray(Vars.insert(variableName, ctx));
            if (array == null) return false;
            ctx.set(Vars.insert(variableOutputName, ctx), (long) array.size());
            return true;
        }
    }
}
