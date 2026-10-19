package org.limitlesssmp.creatorlink.engine.condition;

import net.fabricmc.loader.api.FabricLoader;
import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.EventContext;
import org.limitlesssmp.creatorlink.engine.NoteStore;
import org.limitlesssmp.creatorlink.engine.Vars;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Variable, message, note, flow-control, and misc conditions. */
public final class CoreConditions {
    private CoreConditions() {}

    static void registerAll() {
        ConditionRegistry.register("variableCheck", VariableCheck.class);
        ConditionRegistry.register("variableExists", VariableExists.class);
        ConditionRegistry.register("variableIsArray", VariableIsArray.class);
        ConditionRegistry.register("variableIsNumber", VariableIsNumber.class);
        ConditionRegistry.register("variableInsert", VariableInsert.class);
        ConditionRegistry.register("variableRemove", VariableRemove.class);
        ConditionRegistry.register("message", Message.class);
        ConditionRegistry.register("user", SpecificUser.class);
        ConditionRegistry.register("amount", SpecificAmount.class);
        ConditionRegistry.register("range", RangedAmount.class);
        ConditionRegistry.register("random", RandomChance.class);
        ConditionRegistry.register("note", NoteCond.class);
        ConditionRegistry.register("notesList", NotesList.class);
        ConditionRegistry.register("conditional", Conditional.class);
        ConditionRegistry.register("multiple", Multiple.class);
        ConditionRegistry.register("constant", Constant.class);
        ConditionRegistry.register("repeat", Repeat.class);
        ConditionRegistry.register("null", Null.class);
        ConditionRegistry.register("logArgs", LogArgs.class);
        ConditionRegistry.register("jsonSafe", JsonSafe.class);
        ConditionRegistry.register("readFile", ReadFile.class);
        ConditionRegistry.register("modExists", ModExists.class);
        ConditionRegistry.register("server", Server.class);
        ConditionRegistry.register("uuidToNBT", UuidToNbt.class);
    }

    public static class VariableCheck extends Condition {
        public String variableName;
        public String variableResult;
        public boolean caseSensitive;
        public boolean isGlobal;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String name = Vars.insert(variableName, ctx);
            Object value = isGlobal ? rt.getGlobal(name) : ctx.get(name);
            if (value == null) return false;
            String actual = Vars.stringify(value);
            String expected = Vars.insert(variableResult, ctx);
            if (expected == null) return false;
            return caseSensitive ? actual.equals(expected) : actual.equalsIgnoreCase(expected);
        }
    }

    public static class VariableExists extends Condition {
        public String variableName;
        public boolean isGlobal;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String name = Vars.insert(variableName, ctx);
            return isGlobal ? rt.hasGlobal(name) : ctx.has(name);
        }
    }

    public static class VariableIsArray extends Condition {
        public String variableName;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            return ctx.get(Vars.insert(variableName, ctx)) instanceof List;
        }
    }

    public static class VariableIsNumber extends Condition {
        public String variableName;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            Object v = ctx.get(Vars.insert(variableName, ctx));
            if (v instanceof Number) return true;
            if (v == null) return false;
            try {
                Double.parseDouble(Vars.stringify(v).trim());
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    public static class VariableInsert extends Condition {
        public String variableName;
        public String variableInput;
        public boolean isGlobal;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String name = Vars.insert(variableName, ctx);
            String value = Vars.insert(variableInput, ctx);
            ctx.set(name, value);
            if (isGlobal) rt.setGlobal(name, value);
            return true;
        }
    }

    public static class VariableRemove extends Condition {
        public String variableName;
        public boolean isGlobal;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String name = Vars.insert(variableName, ctx);
            ctx.remove(name);
            if (isGlobal) rt.removeGlobal(name);
            return true;
        }
    }

    public static class Message extends Condition {
        public String optionalVariableName;
        public boolean exactPhrase;
        public boolean caseSensitive;
        public String phrase;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String varName = optionalVariableName == null || optionalVariableName.isEmpty()
                    ? "message" : Vars.insert(optionalVariableName, ctx);
            String value = ctx.getString(varName);
            if (value == null) return false;
            String target = Vars.insert(phrase, ctx);
            if (target == null) return false;
            if (!caseSensitive) {
                value = value.toLowerCase();
                target = target.toLowerCase();
            }
            return exactPhrase ? value.equals(target) : value.contains(target);
        }
    }

    public static class SpecificUser extends Condition {
        public String username;
        public boolean caseSensitive;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String expected = Vars.insert(username, ctx);
            if (expected == null) return false;
            for (String key : new String[] {"user", "user_name", "username", "name"}) {
                String actual = ctx.getString(key);
                if (actual == null) continue;
                return caseSensitive ? actual.equals(expected) : actual.equalsIgnoreCase(expected);
            }
            return false;
        }
    }

    public static class SpecificAmount extends Condition {
        public String optionalVariableName;
        public Double amount;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            if (amount == null) return false;
            String varName = optionalVariableName == null || optionalVariableName.isEmpty()
                    ? "amount" : Vars.insert(optionalVariableName, ctx);
            String value = ctx.getString(varName);
            if (value == null) return false;
            try {
                return Double.parseDouble(value.trim()) == amount;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    public static class RangedAmount extends Condition {
        public String optionalVariableName;
        public Double min;
        public Double max;
        public String minVar;
        public String maxVar;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String varName = optionalVariableName == null || optionalVariableName.isEmpty()
                    ? "amount" : Vars.insert(optionalVariableName, ctx);
            String value = ctx.getString(varName);
            if (value == null) return false;
            double v;
            try {
                v = Double.parseDouble(value.trim());
            } catch (NumberFormatException e) {
                return false;
            }
            double lo = minVar != null ? Vars.toDouble(minVar, ctx, Double.NEGATIVE_INFINITY)
                    : min != null ? min : Double.NEGATIVE_INFINITY;
            double hi = maxVar != null ? Vars.toDouble(maxVar, ctx, Double.POSITIVE_INFINITY)
                    : max != null ? max : Double.POSITIVE_INFINITY;
            return v >= lo && v <= hi;
        }
    }

    public static class RandomChance extends Condition {
        public double chance;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            return rt.random.nextDouble() < chance;
        }
    }

    public static class NoteCond extends Condition {
        public String noteName;
        public String variableName;
        public boolean loadNotesArray;
        public boolean meetConditionIfNoteDoesNotExist;
        public String defaultVariableValue;
        public boolean allowExpired;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String name = Vars.insert(noteName, ctx);
            String varName = Vars.insert(variableName, ctx);
            NoteStore.Note note = rt.notes.get(name, allowExpired);
            Object content = null;
            if (note != null) {
                content = loadNotesArray
                        ? (note.notes == null ? null : new ArrayList<Object>(note.notes))
                        : note.note;
            }
            if (content != null) {
                ctx.set(varName, content);
                return true;
            }
            if (defaultVariableValue != null) {
                String def = Vars.insert(defaultVariableValue, ctx);
                if (loadNotesArray) {
                    ctx.set(varName, new ArrayList<Object>(Arrays.asList(def.split(", "))));
                } else {
                    ctx.set(varName, def);
                }
            }
            return meetConditionIfNoteDoesNotExist;
        }
    }

    public static class NotesList extends Condition {
        public String variableName;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            ctx.set(Vars.insert(variableName, ctx), new ArrayList<Object>(rt.notes.names()));
            return true;
        }
    }

    public static class Conditional extends Condition {
        public Condition condition;
        public Condition passCondition;
        public Condition failCondition;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            if (condition == null || passCondition == null) return false;
            if (condition.test(rt, ctx)) return passCondition.test(rt, ctx);
            return failCondition != null && failCondition.test(rt, ctx);
        }
    }

    public static class Multiple extends Condition {
        public List<Condition> conditions;
        public boolean any;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            if (conditions == null || conditions.isEmpty()) return true;
            if (any) {
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
    }

    public static class Constant extends Condition {
        public String name;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            Condition c = rt.constants.condition(Vars.insert(name, ctx));
            if (c == null) {
                EngineRuntime.LOGGER.warn("Constant condition '{}' not found", name);
                return false;
            }
            return c.test(rt, ctx);
        }
    }

    public static class Repeat extends Condition {
        private static final int MAX_ITERATIONS = 100_000;
        public String times;
        public String arrayVariable;
        public Condition condition;
        public Condition breakCondition;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            if (condition == null) return false;
            if (arrayVariable != null && !arrayVariable.isEmpty()) {
                List<Object> array = ctx.getArray(Vars.insert(arrayVariable, ctx));
                if (array == null) return true;
                int i = 0;
                for (Object o : List.copyOf(array)) {
                    ctx.set("iteCount", (long) i++);
                    ctx.set("arrayObject", o);
                    condition.test(rt, ctx);
                    if (breakCondition != null && breakCondition.test(rt, ctx)) break;
                }
                ctx.remove("arrayObject");
                return true;
            }
            int n = (int) Vars.toDouble(times, ctx, 0);
            n = Math.min(n, MAX_ITERATIONS);
            for (int i = 0; i < n; i++) {
                ctx.set("iteCount", (long) i);
                condition.test(rt, ctx);
                if (breakCondition != null && breakCondition.test(rt, ctx)) break;
            }
            return true;
        }
    }

    public static class Null extends Condition {
        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            return true;
        }
    }

    public static class LogArgs extends Condition {
        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            EngineRuntime.LOGGER.info("[logArgs] {}/{}/{}: {}", ctx.socketType, ctx.platform, ctx.eventType, ctx.varsView());
            return true;
        }
    }

    public static class JsonSafe extends Condition {
        public String variableName;
        public String variableResult;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            String source = Vars.insert(variableName, ctx);
            if (source == null) source = "";
            StringBuilder sb = new StringBuilder(source.length());
            for (int i = 0; i < source.length(); i++) {
                char c = source.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    case '\b' -> sb.append("\\b");
                    case '\f' -> sb.append("\\f");
                    default -> {
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            ctx.set(Vars.insert(variableResult, ctx), sb.toString());
            return true;
        }
    }

    public static class ReadFile extends Condition {
        public String fileName;
        public String variableName;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            Path dir = rt.profileDir();
            if (dir == null || fileName == null) return false;
            Path file = dir.resolve(Vars.insert(fileName, ctx)).normalize();
            if (!file.startsWith(dir.normalize()) || !Files.exists(file)) return false;
            try {
                String content = Files.readString(file);
                ctx.set(variableName == null || variableName.isEmpty() ? "fileContent" : Vars.insert(variableName, ctx), content);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    public static class ModExists extends Condition {
        public String modId;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            return modId != null && FabricLoader.getInstance().isModLoaded(Vars.insert(modId, ctx));
        }
    }

    public static class Server extends Condition {
        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            return rt.bridge().canSendServerOutcomes();
        }
    }

    public static class UuidToNbt extends Condition {
        public String input;
        public String variableResult;
        public boolean convertToMostLeast;

        @Override
        protected boolean check(EngineRuntime rt, EventContext ctx) {
            try {
                UUID uuid = UUID.fromString(Vars.insert(input, ctx).trim());
                long most = uuid.getMostSignificantBits();
                long least = uuid.getLeastSignificantBits();
                String outVar = Vars.insert(variableResult, ctx);
                if (convertToMostLeast) {
                    ctx.set(outVar + "Most", String.valueOf(most));
                    ctx.set(outVar + "Least", String.valueOf(least));
                } else {
                    int a = (int) (most >> 32);
                    int b = (int) most;
                    int c = (int) (least >> 32);
                    int d = (int) least;
                    ctx.set(outVar, "[I;" + a + "," + b + "," + c + "," + d + "]");
                }
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }
}
