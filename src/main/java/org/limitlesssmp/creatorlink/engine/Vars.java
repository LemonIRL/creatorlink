package org.limitlesssmp.creatorlink.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Variable stringification and recursive $-insertion.
 * Insertion is recursive up to a cap, controllable per-event via the
 * documented {@code variableInsertionMaxRecursion} variable (-1 disables
 * insertion, N allows N+1 passes).
 */
public final class Vars {
    public static final String MAX_RECURSION_VAR = "variableInsertionMaxRecursion";
    private static final int MAX_PASSES = 16;

    private Vars() {}

    public static String stringify(Object value) {
        if (value == null) return "";
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(stringify(list.get(i)));
            }
            return sb.toString();
        }
        return String.valueOf(value);
    }

    public static String insert(String input, EventContext ctx) {
        if (input == null || input.indexOf('$') < 0) return input;
        int passes = MAX_PASSES;
        Object cap = ctx.get(MAX_RECURSION_VAR);
        if (cap != null) {
            try {
                int v = (int) Double.parseDouble(stringify(cap).trim());
                if (v < 0) return input;
                passes = Math.min(v + 1, MAX_PASSES);
            } catch (NumberFormatException ignored) {}
        }
        String out = input;
        for (int i = 0; i < passes; i++) {
            String next = insertOnce(out, ctx.varsView());
            if (next.equals(out)) break;
            out = next;
        }
        return out;
    }

    /** Single substitution pass; longest variable names win (so $userLC beats $user). */
    private static String insertOnce(String input, Map<String, Object> vars) {
        List<String> names = new ArrayList<>(vars.keySet());
        names.sort(Comparator.comparingInt(String::length).reversed());
        StringBuilder sb = new StringBuilder(input.length());
        int i = 0;
        outer:
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '$') {
                for (String name : names) {
                    if (!name.isEmpty() && input.startsWith(name, i + 1)) {
                        sb.append(stringify(vars.get(name)));
                        i += 1 + name.length();
                        continue outer;
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /**
     * Resolves a "number or variable" field: applies $-insertion, then parses.
     * A bare variable name (no $) is looked up as a fallback.
     */
    public static double toDouble(String raw, EventContext ctx, double fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        String s = insert(raw, ctx);
        if (s == null) return fallback;
        s = s.trim();
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            Object v = ctx.get(s);
            if (v != null) {
                try {
                    return Double.parseDouble(stringify(v).trim());
                } catch (NumberFormatException ignored) {}
            }
            return fallback;
        }
    }

    public static int toTicks(String raw, EventContext ctx, int fallback) {
        return (int) toDouble(raw, ctx, fallback);
    }
}
