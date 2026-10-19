package org.limitlesssmp.creatorlink.engine.outcome;

import org.limitlesssmp.creatorlink.engine.ConfigIO;
import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.EventContext;
import org.limitlesssmp.creatorlink.engine.Vars;
import org.limitlesssmp.creatorlink.engine.condition.Condition;
import org.limitlesssmp.creatorlink.engine.config.ConfigEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;

/** Structural and file/data outcomes that don't need the game bridge. */
public final class CoreOutcomes {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private CoreOutcomes() {}

    static void registerAll() {
        OutcomeRegistry.register("null", Null.class);
        OutcomeRegistry.register("multiple", Multiple.class);
        OutcomeRegistry.register("conditional", Conditional.class);
        OutcomeRegistry.register("constant", Constant.class);
        OutcomeRegistry.register("event", EventTrigger.class);
        OutcomeRegistry.register("repeat", Repeat.class);
        OutcomeRegistry.register("note", Note.class);
        OutcomeRegistry.register("noteDeletion", NoteDeletion.class);
        OutcomeRegistry.register("writeToFile", WriteToFile.class);
        OutcomeRegistry.register("readFromUrl", ReadFromUrl.class);
        OutcomeRegistry.register("cciChangeProfile", ChangeProfile.class);
        OutcomeRegistry.register("cciClearQueue", ClearQueue.class);
        OutcomeRegistry.register("cciFromJson", FromJson.class);
        OutcomeRegistry.register("cciSendEventToSocket", SendEventToSocket.class);
        OutcomeRegistry.register("cciSendToPlayer", SendToPlayer.class);
    }

    public static class Null extends Outcome {
        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            return true;
        }
    }

    public static class Multiple extends Outcome {
        public List<Outcome> outcomes;
        public boolean singleOutcomeOnly;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (outcomes == null) return false;
            boolean any = false;
            for (Outcome o : outcomes) {
                if (o == null) continue;
                boolean ok = o.run(rt, ctx);
                any |= ok;
                if (ok && singleOutcomeOnly) return true;
            }
            return any;
        }
    }

    public static class Conditional extends Outcome {
        public Condition condition;
        public Outcome outcome;
        public Outcome conditionNotMetOutcome;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (condition == null || outcome == null) return false;
            if (condition.test(rt, ctx)) return outcome.run(rt, ctx);
            return conditionNotMetOutcome != null && conditionNotMetOutcome.run(rt, ctx);
        }
    }

    public static class Constant extends Outcome {
        public String name;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            Outcome o = rt.constants.outcome(Vars.insert(name, ctx));
            if (o == null) {
                EngineRuntime.LOGGER.warn("Constant outcome '{}' not found", name);
                return false;
            }
            return o.run(rt, ctx);
        }
    }

    public static class EventTrigger extends Outcome {
        public List<ConfigEvent> events;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (events == null) return false;
            boolean any = false;
            for (ConfigEvent e : events) {
                if (e == null || e.disabled) continue;
                ConfigEvent eff = e.effective(rt, ctx);
                if (eff == null) continue;
                if (eff.testConditions(rt, ctx)) {
                    any |= eff.triggerOutcomes(rt, ctx);
                    if (!e.allowsOtherEventsToTrigger) break;
                }
            }
            return any;
        }
    }

    public static class Repeat extends Outcome {
        private static final int MAX_ITERATIONS = 100_000;
        public String times;
        public String arrayVariable;
        public Outcome outcome;
        public Condition breakCondition;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (outcome == null) return false;
            if (arrayVariable != null && !arrayVariable.isEmpty()) {
                List<Object> array = ctx.getArray(Vars.insert(arrayVariable, ctx));
                if (array == null) return false;
                int i = 0;
                for (Object o : List.copyOf(array)) {
                    ctx.set("iteCount", (long) i++);
                    ctx.set("arrayObject", o);
                    outcome.run(rt, ctx);
                    if (breakCondition != null && breakCondition.test(rt, ctx)) break;
                }
                ctx.remove("arrayObject");
                return true;
            }
            int n = (int) Vars.toDouble(times, ctx, 0);
            n = Math.min(n, MAX_ITERATIONS);
            for (int i = 0; i < n; i++) {
                ctx.set("iteCount", (long) i);
                outcome.run(rt, ctx);
                if (breakCondition != null && breakCondition.test(rt, ctx)) break;
            }
            return n > 0;
        }
    }

    public static class Note extends Outcome {
        public String noteName;
        public String noteContent;
        public boolean append;
        public boolean putInNotesArray;
        public String expiresIn;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (noteName == null || noteContent == null) return false;
            rt.notes.put(Vars.insert(noteName, ctx), Vars.insert(noteContent, ctx), append, putInNotesArray,
                    expiresIn == null ? null : Vars.insert(expiresIn, ctx));
            return true;
        }
    }

    public static class NoteDeletion extends Outcome {
        public String noteName;
        public int deletionType;
        public String noteContent;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (noteName == null) return false;
            rt.notes.delete(Vars.insert(noteName, ctx), deletionType,
                    noteContent == null ? null : Vars.insert(noteContent, ctx));
            return true;
        }
    }

    public static class WriteToFile extends Outcome {
        public String fileName;
        public String content;
        public boolean append;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            Path dir = rt.profileDir();
            if (dir == null || fileName == null) return false;
            Path file = dir.resolve(Vars.insert(fileName, ctx)).normalize();
            if (!file.startsWith(dir.normalize())) return false;
            try {
                Files.createDirectories(file.getParent());
                String body = Vars.insert(content, ctx);
                if (append) {
                    Files.writeString(file, body == null ? "" : body, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } else {
                    Files.writeString(file, body == null ? "" : body);
                }
                return true;
            } catch (Exception e) {
                EngineRuntime.LOGGER.warn("writeToFile failed: {}", e.toString());
                return false;
            }
        }
    }

    public static class ReadFromUrl extends Outcome {
        public String url;
        public String variableName;
        public Outcome outcome;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (url == null || outcome == null) return false;
            String resolved = Vars.insert(url, ctx);
            HttpRequest req = HttpRequest.newBuilder(URI.create(resolved))
                    .timeout(Duration.ofSeconds(15)).GET().build();
            EventContext snapshot = ctx.copy();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
                if (err != null) {
                    EngineRuntime.LOGGER.warn("readFromUrl {} failed: {}", resolved, err.toString());
                    return;
                }
                snapshot.set(variableName == null ? "urlContent" : Vars.insert(variableName, snapshot), res.body());
                // hop back to the game tick thread before running the outcome
                rt.scheduler.schedule(0, () -> outcome.run(rt, snapshot));
            });
            return true;
        }
    }

    public static class ChangeProfile extends Outcome {
        public String profile;
        public boolean disableToast;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (profile == null || rt.profileChanger == null) return false;
            rt.profileChanger.accept(Vars.insert(profile, ctx), disableToast);
            return true;
        }
    }

    public static class ClearQueue extends Outcome {
        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            rt.scheduler.clearQueues();
            return true;
        }
    }

    public static class FromJson extends Outcome {
        public String source;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (source == null) return false;
            String json = Vars.insert(source, ctx);
            try {
                Outcome o = ConfigIO.GSON.fromJson(json, Outcome.class);
                if (o != null) return o.run(rt, ctx);
            } catch (Exception ignored) {}
            try {
                Condition c = ConfigIO.GSON.fromJson(json, Condition.class);
                if (c != null) return c.test(rt, ctx);
            } catch (Exception ignored) {}
            try {
                ConfigEvent e = ConfigIO.GSON.fromJson(json, ConfigEvent.class);
                if (e != null && e.testConditions(rt, ctx)) return e.triggerOutcomes(rt, ctx);
            } catch (Exception ignored) {}
            return false;
        }
    }

    public static class SendEventToSocket extends Outcome {
        public String configType;
        public String platform;
        public String eventType;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (configType == null || eventType == null || rt.dispatcher == null) return false;
            rt.dispatcher.dispatch(
                    Vars.insert(configType, ctx),
                    platform == null ? null : Vars.insert(platform, ctx),
                    Vars.insert(eventType, ctx),
                    ctx.copy().varsView());
            return true;
        }
    }

    public static class SendToPlayer extends Outcome {
        public String player;
        public Outcome outcome;
        public boolean silenceErrors;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (player == null || outcome == null) return false;
            String json = ConfigIO.GSON.toJson(outcome, Outcome.class);
            return rt.bridge().sendToPlayer(Vars.insert(player, ctx), json, silenceErrors);
        }
    }
}
