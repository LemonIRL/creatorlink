package org.limitlesssmp.creatorlink.engine.condition;

import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.EventContext;
import org.limitlesssmp.creatorlink.engine.Vars;

import java.util.ArrayList;
import java.util.List;

/** String conditions; unconditional variants mirror java.lang.String methods. */
public final class StringConditions {
    private StringConditions() {}

    static void registerAll() {
        ConditionRegistry.register("stringEquals", Equals.class);
        ConditionRegistry.register("stringContains", Contains.class);
        ConditionRegistry.register("stringStarts", StartsWith.class);
        ConditionRegistry.register("stringEnds", EndsWith.class);
        ConditionRegistry.register("stringSubstr", SubString.class);
        ConditionRegistry.register("stringCompare", CompareTo.class);
        ConditionRegistry.register("stringConcat", Concat.class);
        ConditionRegistry.register("stringIndex", IndexOf.class);
        ConditionRegistry.register("stringLength", Length.class);
        ConditionRegistry.register("stringLower", LowerCase.class);
        ConditionRegistry.register("stringUpper", UpperCase.class);
        ConditionRegistry.register("stringReplace", Replace.class);
        ConditionRegistry.register("stringSplit", Split.class);
        ConditionRegistry.register("stringTrim", Trim.class);
    }

    public abstract static class SourceTarget extends Condition {
        public String source;
        public String target;

        protected String src(EventContext ctx) {
            return Vars.insert(source, ctx);
        }

        protected String tgt(EventContext ctx) {
            return Vars.insert(target, ctx);
        }
    }

    public static class Equals extends SourceTarget {
        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String s = src(ctx);
            return s != null && s.equals(tgt(ctx));
        }
    }

    public static class Contains extends SourceTarget {
        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String s = src(ctx);
            String t = tgt(ctx);
            return s != null && t != null && s.contains(t);
        }
    }

    public static class StartsWith extends SourceTarget {
        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String s = src(ctx);
            String t = tgt(ctx);
            return s != null && t != null && s.startsWith(t);
        }
    }

    public static class EndsWith extends SourceTarget {
        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String s = src(ctx);
            String t = tgt(ctx);
            return s != null && t != null && s.endsWith(t);
        }
    }

    public static class SubString extends Condition {
        public String source;
        public Integer beginIndex;
        public Integer endIndex;
        public String result;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String s = Vars.insert(source, ctx);
            if (s == null || beginIndex == null || beginIndex < 0 || beginIndex > s.length()) return false;
            int end = endIndex == null ? s.length() : Math.min(endIndex, s.length());
            if (end < beginIndex) return false;
            ctx.set(Vars.insert(result, ctx), s.substring(beginIndex, end));
            return true;
        }
    }

    public static class CompareTo extends Condition {
        public String source;
        public String target;
        public String result;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String s = Vars.insert(source, ctx);
            String t = Vars.insert(target, ctx);
            ctx.set(Vars.insert(result, ctx), (long) (s == null || t == null ? 0 : s.compareTo(t)));
            return true;
        }
    }

    public static class Concat extends Condition {
        public String prefix;
        public String suffix;
        public String variableName;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String p = Vars.insert(prefix, ctx);
            String s = Vars.insert(suffix, ctx);
            ctx.set(Vars.insert(variableName, ctx), (p == null ? "" : p) + (s == null ? "" : s));
            return true;
        }
    }

    public static class IndexOf extends Condition {
        public String source;
        public String target;
        public boolean last;
        public String result;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String s = Vars.insert(source, ctx);
            String t = Vars.insert(target, ctx);
            long idx = s == null || t == null ? -1 : (last ? s.lastIndexOf(t) : s.indexOf(t));
            ctx.set(Vars.insert(result, ctx), idx);
            return true;
        }
    }

    public static class Length extends Condition {
        public String variableName;
        public String variableResult;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String s = Vars.insert(variableName, ctx);
            ctx.set(Vars.insert(variableResult, ctx), (long) (s == null ? 0 : s.length()));
            return true;
        }
    }

    public static class LowerCase extends Condition {
        public String source;
        public String result;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String s = Vars.insert(source, ctx);
            ctx.set(Vars.insert(result, ctx), s == null ? "" : s.toLowerCase());
            return true;
        }
    }

    public static class UpperCase extends Condition {
        public String source;
        public String result;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String s = Vars.insert(source, ctx);
            ctx.set(Vars.insert(result, ctx), s == null ? "" : s.toUpperCase());
            return true;
        }
    }

    public static class Replace extends Condition {
        public String source;
        public String target;
        public String replacement;
        public String result;
        public boolean literal;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String s = Vars.insert(source, ctx);
            String t = Vars.insert(target, ctx);
            String r = Vars.insert(replacement, ctx);
            if (s == null || t == null) return false;
            if (r == null) r = "";
            String out;
            try {
                out = literal ? s.replace(t, r) : s.replaceAll(t, r);
            } catch (Exception e) {
                return false;
            }
            ctx.set(Vars.insert(result, ctx), out);
            return true;
        }
    }

    public static class Split extends Condition {
        public String source;
        public String separator;
        public String result;
        public boolean omitEmptyStrings;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String s = Vars.insert(source, ctx);
            String sep = Vars.insert(separator, ctx);
            if (s == null || sep == null) return false;
            List<Object> out = new ArrayList<>();
            try {
                for (String part : s.split(sep, -1)) {
                    if (omitEmptyStrings && part.isEmpty()) continue;
                    out.add(part);
                }
            } catch (Exception e) {
                return false;
            }
            ctx.set(Vars.insert(result, ctx), out);
            return true;
        }
    }

    public static class Trim extends Condition {
        public String source;
        public String result;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String s = Vars.insert(source, ctx);
            ctx.set(Vars.insert(result, ctx), s == null ? "" : s.trim());
            return true;
        }
    }
}
