package org.limitlesssmp.creatorlink.engine;

import java.util.List;
import java.util.function.Consumer;

/**
 * Everything the engine needs from the running game. The engine itself is pure
 * Java; the client mod supplies a real implementation. Defaults are safe no-ops
 * so configs can load (and unit tests run) without Minecraft.
 */
public interface GameBridge {
    default String playerName() { return "Player"; }
    default String mcVersion() { return "unknown"; }
    default boolean isInWorld() { return false; }
    /** Whether server-side outcomes can currently be sent (whitelisted on a modded server, or singleplayer). */
    default boolean canSendServerOutcomes() { return false; }

    default String localize(String key) { return key; }

    default boolean toast(int toastType, String title, String subtitle, String titleColor, String subtitleColor) { return false; }
    default boolean chat(String message, boolean inActionBar, boolean sendToServer) { return false; }
    default boolean command(String command, boolean executeAsSelf, boolean disableChatCommandFallback,
                            String handleFeedback, List<String> feedbackKeys) { return false; }
    default boolean sound(boolean clientSide, String sound, String target, double volume, double pitch, String category) { return false; }
    default boolean narrate(String message, boolean interrupts) { return false; }

    default String gameSettingGet(String key) { return null; }
    default boolean gameSettingSet(String key, String value) { return false; }
    default boolean keyPress(String keyName, String inputName, String rawInput, Boolean pressed) { return false; }

    /** playerState ids: alive, invisible, onFire, onGround, poisoned, sleeping, sneaking, swimming, isInWater, isInWaterOrRain, isInWaterOrBubble, isInWaterRainOrBubble, usingItem, withering, exists */
    default Boolean playerState(String state) { return null; }
    /** Target queries: name, uuid, health, maxhealth, armor, location*, rotation*, look*, radius*, biome, dim, light, diff, time, day */
    default String target(String selector, String argument) { return null; }
    default boolean targetExists(String selector) { return false; }
    default String inventoryQuery(String target, int index) { return null; }
    default boolean inventoryOp(String target, String funcType, int index, String additionalArgs) { return false; }
    default String freeSpace(String target, double horiRadius, double vertRadius, int tries,
                             double customHeight, double customWidth, double minHoriDistance, boolean requireSolidBlock) { return null; }
    default Long statistic(String statKey) { return null; }
    default void requestStatistics() {}

    default boolean health(String target, double amount) { return false; }
    default boolean food(String target, Double level, Double saturation, Double exhaustion) { return false; }
    default boolean motion(String target, double amount, String axis, boolean relative, boolean doOnPlayer) { return false; }
    default boolean mount(String riderTarget, String riddenTarget, boolean force, boolean forceToTop) { return false; }

    default void popup(String text, Runnable postOk) { if (postOk != null) postOk.run(); }
    default void prompt(String text, String yesOverride, String noOverride, Runnable yes, Runnable no) {}
    default void input(String text, String defaultInput, Consumer<String> done) {}

    default void crash(int crashType) {}
    default void fakeCrash(int crashType, int durationTicks) {}

    default boolean sendToPlayer(String player, String outcomeJson, boolean silenceErrors) { return false; }

    GameBridge NOOP = new GameBridge() {};
}
