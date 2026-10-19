package org.limitlesssmp.creatorlink.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.limitlesssmp.creatorlink.client.editor.InputScreen;
import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.net.Payloads;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * GameBridge backed by the running client. Commands and entity manipulation
 * prefer the server path (whitelist + selectors); world queries run against
 * the client level.
 */
public final class ClientBridge implements org.limitlesssmp.creatorlink.engine.GameBridge {
    private static final int CHAT_LIMIT = 256;

    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public String playerName() {
        LocalPlayer player = mc.player;
        if (player != null) return player.getName().getString();
        return mc.getUser().getName();
    }

    @Override
    public String mcVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
    }

    @Override
    public boolean isInWorld() {
        return mc.player != null && mc.level != null;
    }

    @Override
    public boolean canSendServerOutcomes() {
        return mc.player != null && ClientPlayNetworking.canSend(Payloads.CommandPayload.TYPE);
    }

    @Override
    public String localize(String key) {
        return I18n.get(key);
    }

    @Override
    public boolean toast(int toastType, String title, String subtitle, String titleColor, String subtitleColor) {
        MutableComponent titleC = colored(title, titleColor);
        Component subC = subtitle == null ? null : colored(subtitle, subtitleColor);
        mc.execute(() -> mc.gui.toastManager().addToast(
                new SystemToast(SystemToast.SystemToastId.PERIODIC_NOTIFICATION, titleC, subC)));
        return true;
    }

    private static MutableComponent colored(String text, String color) {
        MutableComponent c = Component.literal(text);
        if (color != null && !color.isEmpty()) {
            try {
                Optional<TextColor> parsed = TextColor.parseColor(color).result();
                parsed.ifPresent(tc -> c.withStyle(style -> style.withColor(tc)));
            } catch (Exception ignored) {}
        }
        return c;
    }

    @Override
    public boolean chat(String message, boolean inActionBar, boolean sendToServer) {
        LocalPlayer player = mc.player;
        if (player == null) return false;
        if (sendToServer) {
            String out = message.length() > CHAT_LIMIT ? message.substring(0, CHAT_LIMIT) : message;
            mc.execute(() -> player.connection.sendChat(out));
        } else if (inActionBar) {
            mc.execute(() -> player.sendOverlayMessage(Component.literal(message)));
        } else {
            mc.execute(() -> player.sendSystemMessage(Component.literal(message)));
        }
        return true;
    }

    @Override
    public boolean command(String command, boolean executeAsSelf, boolean disableChatCommandFallback,
                           String handleFeedback, List<String> feedbackKeys) {
        LocalPlayer player = mc.player;
        if (player == null) return false;
        // null handleFeedback silences feedback; non-empty captures it into a global variable
        boolean silent = handleFeedback == null;
        if (handleFeedback != null && !handleFeedback.isEmpty()) {
            FeedbackCapture.arm(handleFeedback, feedbackKeys);
        }
        String full = command.startsWith("/") ? command : "/" + command;
        if (canSendServerOutcomes()) {
            if (full.length() > 32000) return false;
            mc.execute(() -> ClientPlayNetworking.send(new Payloads.CommandPayload(full, executeAsSelf, silent)));
            return true;
        }
        if (executeAsSelf && !disableChatCommandFallback) {
            if (full.length() > CHAT_LIMIT) {
                EngineRuntime.LOGGER.warn("Command too long for chat fallback ({} chars); server-side CreatorLink required", full.length());
                toast(3, "CreatorLink", "Command too long (" + full.length()
                        + " chars) for a vanilla server — install CreatorLink server-side", null, null);
                return false;
            }
            mc.execute(() -> player.connection.sendCommand(full.substring(1)));
            return true;
        }
        return false;
    }

    @Override
    public boolean sound(boolean clientSide, String sound, String target, double volume, double pitch, String category) {
        if (clientSide || !canSendServerOutcomes()) {
            Identifier id = Identifier.tryParse(sound);
            if (id == null) return false;
            Optional<SoundEvent> event = BuiltInRegistries.SOUND_EVENT.getOptional(id);
            if (event.isEmpty()) return false;
            mc.execute(() -> mc.getSoundManager().play(SimpleSoundInstance.forUI(event.get(), (float) pitch, (float) volume)));
            return true;
        }
        String cat = category == null || category.isEmpty() ? "master" : category;
        String tgt = target == null || target.isEmpty() ? playerName() : target;
        return command("/playsound " + sound + " " + cat + " " + tgt + " ~ ~ ~ " + volume + " " + pitch,
                true, false, null, null);
    }

    @Override
    public boolean narrate(String message, boolean interrupts) {
        mc.execute(() -> {
            if (interrupts) {
                mc.getNarrator().saySystemNow(message);
            } else {
                mc.getNarrator().saySystemQueued(Component.literal(message));
            }
        });
        return true;
    }

    // ------------------------------------------------------------- settings

    private Path optionsFile() {
        return FabricLoader.getInstance().getGameDir().resolve("options.txt");
    }

    @Override
    public String gameSettingGet(String key) {
        try {
            for (String line : Files.readAllLines(optionsFile())) {
                int colon = line.indexOf(':');
                if (colon > 0 && line.substring(0, colon).equals(key)) {
                    return line.substring(colon + 1);
                }
            }
        } catch (IOException ignored) {}
        return null;
    }

    @Override
    public boolean gameSettingSet(String key, String value) {
        try {
            Path file = optionsFile();
            List<String> lines = new ArrayList<>(Files.readAllLines(file));
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                int colon = lines.get(i).indexOf(':');
                if (colon > 0 && lines.get(i).substring(0, colon).equals(key)) {
                    lines.set(i, key + ":" + value);
                    found = true;
                    break;
                }
            }
            if (!found) lines.add(key + ":" + value);
            Files.write(file, lines);
            mc.execute(() -> mc.options.load());
            return true;
        } catch (IOException e) {
            EngineRuntime.LOGGER.warn("gameSettings set failed: {}", e.toString());
            return false;
        }
    }

    @Override
    public boolean keyPress(String keyName, String inputName, String rawInput, Boolean pressed) {
        if (keyName != null && !keyName.isEmpty()) {
            for (KeyMapping mapping : mc.options.keyMappings) {
                if (mapping.getName().equals(keyName)) {
                    boolean down = pressed != null && pressed;
                    mc.execute(() -> mapping.setDown(down));
                    return true;
                }
            }
            return false;
        }
        if (inputName != null && !inputName.isEmpty()) {
            try {
                InputConstants.Key key = InputConstants.getKey(inputName);
                mc.execute(() -> KeyMapping.click(key));
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        EngineRuntime.LOGGER.warn("keyPress rawInput is not supported");
        return false;
    }

    // ------------------------------------------------------- state queries

    @Override
    public Boolean playerState(String state) {
        LocalPlayer player = mc.player;
        if (state == null) return null;
        if (state.equals("exists")) return player != null;
        if (player == null) return false;
        return switch (state) {
            case "alive" -> player.isAlive();
            case "invisible" -> player.isInvisible();
            case "onFire" -> player.isOnFire();
            case "onGround" -> player.onGround();
            case "sleeping" -> player.isSleeping();
            case "sneaking" -> player.isShiftKeyDown();
            case "swimming" -> player.isSwimming();
            case "isInWater" -> player.isInWater();
            case "usingItem" -> player.isUsingItem();
            case "poisoned" -> player.hasEffect(MobEffects.POISON);
            case "withering" -> player.hasEffect(MobEffects.WITHER);
            default -> null;
        };
    }

    @Override
    public String target(String selector, String argument) {
        return TargetResolver.query(TargetResolver.resolve(selector), argument);
    }

    @Override
    public boolean targetExists(String selector) {
        return TargetResolver.resolve(selector) != null;
    }

    @Override
    public String freeSpace(String target, double horiRadius, double vertRadius, int tries,
                            double customHeight, double customWidth, double minHoriDistance, boolean requireSolidBlock) {
        return TargetResolver.freeSpace(TargetResolver.resolve(target), horiRadius, vertRadius, tries,
                customHeight, customWidth, minHoriDistance, requireSolidBlock);
    }

    @Override
    public String inventoryQuery(String target, int index) {
        LocalPlayer player = mc.player;
        if (player == null) return null;
        var inv = player.getInventory();
        if (index < 0 || index >= inv.getContainerSize()) return null;
        var stack = inv.getItem(index);
        return stack.isEmpty() ? "minecraft:air"
                : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    @Override
    public Long statistic(String statKey) {
        LocalPlayer player = mc.player;
        if (player == null || statKey == null) return null;
        Identifier id = Identifier.tryParse(statKey.trim());
        if (id == null || BuiltInRegistries.CUSTOM_STAT.getOptional(id).isEmpty()) return null;
        try {
            return (long) player.getStats().getValue(Stats.CUSTOM.get(id));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void requestStatistics() {
        if (mc.getConnection() != null) {
            mc.execute(() -> mc.getConnection().send(
                    new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS)));
        }
    }

    // ------------------------------------------------- entity manipulation

    private boolean serverOp(String op, String target, String a, String b, String c) {
        if (!canSendServerOutcomes()) {
            EngineRuntime.LOGGER.warn("{} outcome requires server-side CreatorLink", op);
            return false;
        }
        mc.execute(() -> ClientPlayNetworking.send(new Payloads.ServerOpPayload(
                op, target == null ? "@s" : target, a == null ? "" : a, b == null ? "" : b, c == null ? "" : c)));
        return true;
    }

    @Override
    public boolean health(String target, double amount) {
        return serverOp("health", target, String.valueOf(amount), "", "");
    }

    @Override
    public boolean food(String target, Double level, Double saturation, Double exhaustion) {
        return serverOp("food", target,
                level == null ? "" : String.valueOf(level),
                saturation == null ? "" : String.valueOf(saturation),
                exhaustion == null ? "" : String.valueOf(exhaustion));
    }

    @Override
    public boolean motion(String target, double amount, String axis, boolean relative, boolean doOnPlayer) {
        if (doOnPlayer) {
            LocalPlayer player = mc.player;
            if (player == null) return false;
            String ax = axis == null ? "y" : axis;
            mc.execute(() -> {
                Vec3 d = player.getDeltaMovement();
                player.setDeltaMovement(switch (ax) {
                    case "x" -> new Vec3(relative ? d.x + amount : amount, d.y, d.z);
                    case "z" -> new Vec3(d.x, d.y, relative ? d.z + amount : amount);
                    default -> new Vec3(d.x, relative ? d.y + amount : amount, d.z);
                });
            });
            return true;
        }
        return serverOp("motion", target, String.valueOf(amount), axis == null ? "y" : axis, String.valueOf(relative));
    }

    @Override
    public boolean mount(String riderTarget, String riddenTarget, boolean force, boolean forceToTop) {
        return serverOp("mount", riderTarget, riddenTarget, String.valueOf(force), String.valueOf(forceToTop));
    }

    @Override
    public boolean inventoryOp(String target, String funcType, int index, String additionalArgs) {
        return serverOp("inventory", target, funcType, String.valueOf(index),
                additionalArgs == null ? "" : additionalArgs);
    }

    // --------------------------------------------------------- popups etc.

    @Override
    public void popup(String text, Runnable postOk) {
        mc.execute(() -> {
            Screen previous = mc.gui.screen();
            mc.gui.setScreen(new AlertScreen(() -> {
                mc.gui.setScreen(previous);
                if (postOk != null) postOk.run();
            }, Component.literal("CreatorLink"), Component.literal(text)));
        });
    }

    @Override
    public void prompt(String text, String yesOverride, String noOverride, Runnable yes, Runnable no) {
        mc.execute(() -> {
            Screen previous = mc.gui.screen();
            it.unimi.dsi.fastutil.booleans.BooleanConsumer response = result -> {
                mc.gui.setScreen(previous);
                if (result && yes != null) yes.run();
                if (!result && no != null) no.run();
            };
            Component title = Component.literal("CreatorLink");
            Component message = Component.literal(text);
            if (yesOverride != null || noOverride != null) {
                mc.gui.setScreen(new ConfirmScreen(response, title, message,
                        Component.literal(yesOverride == null ? "Yes" : yesOverride),
                        Component.literal(noOverride == null ? "No" : noOverride)));
            } else {
                mc.gui.setScreen(new ConfirmScreen(response, title, message));
            }
        });
    }

    @Override
    public void input(String text, String defaultInput, Consumer<String> done) {
        mc.execute(() -> {
            Screen previous = mc.gui.screen();
            mc.gui.setScreen(new InputScreen(previous, text, defaultInput, done));
        });
    }

    // ------------------------------------------------------------- crashes

    @Override
    public void crash(int crashType) {
        EngineRuntime.LOGGER.warn("GameCrashOutcome triggered (type {})", crashType);
        switch (crashType) {
            case 1 -> mc.execute(() -> System.exit(0));
            case 2 -> Runtime.getRuntime().halt(-1);
            case 4 -> mc.execute(ClientBridge::overflow);
            case 5 -> mc.execute(() -> {
                while (true) {
                    java.util.concurrent.locks.LockSupport.parkNanos(1_000_000_000L);
                }
            });
            default -> mc.execute(() -> {
                throw new IllegalStateException("CreatorLink GameCrashOutcome");
            });
        }
    }

    private static void overflow() {
        overflow();
    }

    @Override
    public void fakeCrash(int crashType, int durationTicks) {
        long millis = Math.max(1, durationTicks) * 50L;
        mc.execute(() -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ignored) {}
        });
    }

    @Override
    public boolean sendToPlayer(String player, String outcomeJson, boolean silenceErrors) {
        if (!canSendServerOutcomes()) return false;
        mc.execute(() -> ClientPlayNetworking.send(new Payloads.SendToPlayerPayload(player, outcomeJson, silenceErrors)));
        return true;
    }
}
