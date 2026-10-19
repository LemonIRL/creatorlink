package org.limitlesssmp.creatorlink.engine.condition;

import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.EventContext;
import org.limitlesssmp.creatorlink.engine.Vars;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Math, random, base64 and localisation conditions (all unconditional). */
public final class MathConditions {
    private MathConditions() {}

    static void registerAll() {
        ConditionRegistry.register("math", Arithmetic.class);
        ConditionRegistry.register("randomNumber", RandomNumber.class);
        ConditionRegistry.register("randomString", RandomString.class);
        ConditionRegistry.register("base64Decode", B64Decode.class);
        ConditionRegistry.register("base64Encode", B64Encode.class);
        ConditionRegistry.register("localisedString", LocalisedString.class);
    }

    public static class Arithmetic extends Condition {
        public String calc;
        public String value1;
        public String value2;
        public String variableName;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            double v1 = Vars.toDouble(value1, ctx, 0);
            double v2 = Vars.toDouble(value2, ctx, 0);
            String op = calc == null ? "+" : calc.trim();
            Object result;
            switch (op) {
                case "+" -> result = v1 + v2;
                case "-" -> result = v1 - v2;
                case "x", "*" -> result = v1 * v2;
                case "/" -> result = v2 == 0 ? 0.0 : v1 / v2;
                case "pow" -> result = Math.pow(v1, v2);
                case "sqrt" -> result = Math.sqrt(v1);
                case "mod", "%" -> result = v2 == 0 ? 0.0 : v1 % v2;
                case "max" -> result = Math.max(v1, v2);
                case "min" -> result = Math.min(v1, v2);
                // round/floor/ceil convert to the documented "integer-type" (whole number)
                case "round" -> result = Math.round(v1);
                case "floor" -> result = (long) Math.floor(v1);
                case "ceil" -> result = (long) Math.ceil(v1);
                case "log" -> result = Math.log(v1);
                case "signum" -> result = Math.signum(v1);
                case "abs" -> result = Math.abs(v1);
                case "sin" -> result = Math.sin(Math.toRadians(v1));
                case "cos" -> result = Math.cos(Math.toRadians(v1));
                case "tan" -> result = Math.tan(Math.toRadians(v1));
                case "asin" -> result = Math.toDegrees(Math.asin(v1));
                case "acos" -> result = Math.toDegrees(Math.acos(v1));
                case "atan" -> result = Math.toDegrees(Math.atan(v1));
                default -> result = 0.0;
            }
            ctx.set(Vars.insert(variableName, ctx), result);
            return true;
        }
    }

    public static class RandomNumber extends Condition {
        public String value1;
        public String value2;
        public String variableName;
        public boolean roundNumber;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            double v1 = Vars.toDouble(value1, ctx, 0);
            double v2 = Vars.toDouble(value2, ctx, 0);
            double lo = Math.min(v1, v2);
            double hi = Math.max(v1, v2);
            double val = lo + rt.random.nextDouble() * (hi - lo);
            Object result = roundNumber ? (Object) Math.round(val) : (Object) val;
            ctx.set(Vars.insert(variableName, ctx), result);
            return true;
        }
    }

    public static class RandomString extends Condition {
        public List<String> values;
        public String variableName;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            if (values == null || values.isEmpty()) return true;
            List<String> resolved = new ArrayList<>(values.size());
            for (String v : values) resolved.add(Vars.insert(v, ctx));
            ctx.set(Vars.insert(variableName, ctx), resolved.get(rt.random.nextInt(resolved.size())));
            return true;
        }
    }

    public static class B64Decode extends Condition {
        public String value;
        public String variableResult;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            try {
                String decoded = new String(Base64.getDecoder().decode(Vars.insert(value, ctx)));
                ctx.set(Vars.insert(variableResult, ctx), decoded);
            } catch (Exception ignored) {}
            return true;
        }
    }

    public static class B64Encode extends Condition {
        public String value;
        public String variableName;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String v = Vars.insert(value, ctx);
            ctx.set(Vars.insert(variableName, ctx), Base64.getEncoder().encodeToString((v == null ? "" : v).getBytes()));
            return true;
        }
    }

    public static class LocalisedString extends Condition {
        public String value;
        public String variableResult;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            ctx.set(Vars.insert(variableResult, ctx), rt.bridge().localize(Vars.insert(value, ctx)));
            return true;
        }
    }
}
