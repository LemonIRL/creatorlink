package org.limitlesssmp.creatorlink.engine.outcome;

import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.EventContext;
import org.limitlesssmp.creatorlink.engine.Vars;

import java.util.ArrayList;
import java.util.List;

/** Outcomes that act on the game via the GameBridge. */
public final class GameOutcomes {
    private GameOutcomes() {}

    static void registerAll() {
        OutcomeRegistry.register("chat", Chat.class);
        OutcomeRegistry.register("toast", Toast.class);
        OutcomeRegistry.register("command", Command.class);
        OutcomeRegistry.register("sound", Sound.class);
        OutcomeRegistry.register("narrator", Narrator.class);
        OutcomeRegistry.register("gameSettings", GameSettings.class);
        OutcomeRegistry.register("keyPress", KeyPress.class);
        OutcomeRegistry.register("popup", Popup.class);
        OutcomeRegistry.register("prompt", Prompt.class);
        OutcomeRegistry.register("input", Input.class);
        OutcomeRegistry.register("food", Food.class);
        OutcomeRegistry.register("health", Health.class);
        OutcomeRegistry.register("motion", Motion.class);
        OutcomeRegistry.register("mount", Mount.class);
        OutcomeRegistry.register("inventory", Inventory.class);
        OutcomeRegistry.register("crash", GameCrash.class);
        OutcomeRegistry.register("fakeCrash", FakeCrash.class);
        OutcomeRegistry.register("imc", Imc.class);
        OutcomeRegistry.register("objectAccessor", ObjectAccessor.class);
    }

    public static class Chat extends Outcome {
        public String message;
        public boolean inActionBar;
        public boolean sendToServer;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (message == null) return false;
            return rt.bridge().chat(Vars.insert(message, ctx), inActionBar, sendToServer);
        }
    }

    public static class Toast extends Outcome {
        public int toastType;
        public String title;
        public String subtitle;
        public String titleColor;
        public String subtitleColor;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (title == null) return false;
            return rt.bridge().toast(toastType, Vars.insert(title, ctx),
                    subtitle == null ? null : Vars.insert(subtitle, ctx), titleColor, subtitleColor);
        }
    }

    public static class Command extends Outcome {
        public String command;
        public boolean executeAsSelf;
        public String handleFeedback;
        public List<String> feedbackKeys;
        public boolean disableChatCommandFallback;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (command == null) return false;
            return rt.bridge().command(Vars.insert(command, ctx), executeAsSelf,
                    disableChatCommandFallback, handleFeedback, feedbackKeys);
        }
    }

    public static class Sound extends Outcome {
        public boolean clientSide;
        public String sound;
        public String target;
        public Double volume;
        public Double pitch;
        public String category;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (sound == null) return false;
            return rt.bridge().sound(clientSide, Vars.insert(sound, ctx),
                    target == null ? null : Vars.insert(target, ctx),
                    volume == null ? 1 : volume, pitch == null ? 1 : pitch, category);
        }
    }

    public static class Narrator extends Outcome {
        public String message;
        public boolean interrupts;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (message == null) return false;
            return rt.bridge().narrate(Vars.insert(message, ctx), interrupts);
        }
    }

    public static class GameSettings extends Outcome {
        public String key;
        public String value;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (key == null || value == null) return false;
            return rt.bridge().gameSettingSet(Vars.insert(key, ctx), Vars.insert(value, ctx));
        }
    }

    public static class KeyPress extends Outcome {
        public String keyName;
        public String inputName;
        public String rawInput;
        public Boolean pressed;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            return rt.bridge().keyPress(keyName, inputName, rawInput, pressed);
        }
    }

    public static class Popup extends Outcome {
        public String text;
        public Outcome postOkOutcome;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (text == null) return false;
            EventContext snapshot = ctx.copy();
            rt.bridge().popup(Vars.insert(text, ctx),
                    postOkOutcome == null ? null : () -> postOkOutcome.run(rt, snapshot));
            return true;
        }
    }

    public static class Prompt extends Outcome {
        public String text;
        public String yesOverride;
        public String noOverride;
        public Outcome yesOutcome;
        public Outcome noOutcome;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (text == null) return false;
            EventContext snapshot = ctx.copy();
            rt.bridge().prompt(Vars.insert(text, ctx), yesOverride, noOverride,
                    yesOutcome == null ? null : () -> yesOutcome.run(rt, snapshot),
                    noOutcome == null ? null : () -> noOutcome.run(rt, snapshot));
            return true;
        }
    }

    public static class Input extends Outcome {
        public String text;
        public String defaultInput;
        public String variableInput;
        public Outcome postInputOutcome;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (text == null || variableInput == null) return false;
            EventContext snapshot = ctx.copy();
            rt.bridge().input(Vars.insert(text, ctx), defaultInput, value -> {
                snapshot.set(Vars.insert(variableInput, snapshot), value);
                if (postInputOutcome != null) postInputOutcome.run(rt, snapshot);
            });
            return true;
        }
    }

    public static class Food extends Outcome {
        public String target;
        public String level;
        public String saturation;
        public String exhaustion;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            return rt.bridge().food(Vars.insert(target, ctx),
                    level == null ? null : Vars.toDouble(level, ctx, 0),
                    saturation == null ? null : Vars.toDouble(saturation, ctx, 0),
                    exhaustion == null ? null : Vars.toDouble(exhaustion, ctx, 0));
        }
    }

    public static class Health extends Outcome {
        public String target;
        public String amount;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (target == null || amount == null) return false;
            return rt.bridge().health(Vars.insert(target, ctx), Vars.toDouble(amount, ctx, 0));
        }
    }

    public static class Motion extends Outcome {
        public String target;
        public String amount;
        public String axis;
        public boolean relative;
        public Boolean doOnPlayer;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            return rt.bridge().motion(target == null ? null : Vars.insert(target, ctx),
                    Vars.toDouble(amount, ctx, 0), axis, relative, doOnPlayer != null && doOnPlayer);
        }
    }

    public static class Mount extends Outcome {
        public String riderTarget;
        public String riddenTarget;
        public boolean force;
        public boolean forceToTop;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (riderTarget == null || riddenTarget == null) return false;
            return rt.bridge().mount(Vars.insert(riderTarget, ctx), Vars.insert(riddenTarget, ctx), force, forceToTop);
        }
    }

    public static class Inventory extends Outcome {
        public String target;
        public String funcType;
        public String index;
        public String additionalArgs;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            if (funcType == null) return false;
            return rt.bridge().inventoryOp(target == null ? null : Vars.insert(target, ctx),
                    funcType, (int) Vars.toDouble(index, ctx, -1),
                    additionalArgs == null ? null : Vars.insert(additionalArgs, ctx));
        }
    }

    public static class GameCrash extends Outcome {
        public String crashType;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            rt.bridge().crash((int) Vars.toDouble(crashType, ctx, 1));
            return true;
        }
    }

    public static class FakeCrash extends Outcome {
        public String crashType;
        public String crashDuration;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            rt.bridge().fakeCrash((int) Vars.toDouble(crashType, ctx, 1),
                    (int) Vars.toDouble(crashDuration, ctx, 100));
            return true;
        }
    }

    public static class Imc extends Outcome {
        public String modId;
        public String subject;
        public String message;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            // Forge-style inter-mod comms has no Fabric equivalent; log for visibility.
            EngineRuntime.LOGGER.info("IMC outcome ignored on Fabric (mod {}, subject {})", modId, subject);
            return false;
        }
    }

    public static class ObjectAccessor extends Outcome {
        public List<Object> accessors = new ArrayList<>();
        public boolean isServerSide;

        @Override
        protected boolean execute(EngineRuntime rt, EventContext ctx) {
            // Reflection-driven accessors are part of the expert gamehooks system (stretch goal).
            EngineRuntime.LOGGER.warn("objectAccessor outcomes are not supported yet");
            return false;
        }
    }
}
