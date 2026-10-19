package org.limitlesssmp.creatorlink.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Native game-event source: dispatches client game events into a "gamehooks"
 * event configuration (config/creatorlink/<profile>/gamehooks.json).
 * Event types: joinWorld, leaveWorld, death, respawn, attackBlock, useItem, chatSent, commandSent.
 */
public final class GameHooks {
    private static boolean wasAlive = true;

    private GameHooks() {}

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                dispatch("joinWorld", Map.of()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                dispatch("leaveWorld", Map.of()));

        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (level.isClientSide() && player == net.minecraft.client.Minecraft.getInstance().player) {
                Map<String, Object> vars = new LinkedHashMap<>();
                vars.put("block", BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
                vars.put("x", (long) pos.getX());
                vars.put("y", (long) pos.getY());
                vars.put("z", (long) pos.getZ());
                dispatch("attackBlock", vars);
            }
            return InteractionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level.isClientSide() && player == net.minecraft.client.Minecraft.getInstance().player) {
                Map<String, Object> vars = new LinkedHashMap<>();
                vars.put("item", BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).getItem()).toString());
                dispatch("useItem", vars);
            }
            return InteractionResult.PASS;
        });

        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.CHAT.register(message ->
                dispatch("chatSent", Map.of("message", message)));
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.COMMAND.register(command ->
                dispatch("commandSent", Map.of("command", command)));
    }

    /** Called every client tick to detect death/respawn transitions. */
    public static void tick(net.minecraft.client.Minecraft mc) {
        boolean alive = mc.player == null || !mc.player.isDeadOrDying();
        if (wasAlive && !alive) {
            dispatch("death", Map.of());
        } else if (!wasAlive && alive && mc.player != null) {
            dispatch("respawn", Map.of());
        }
        wasAlive = alive;
    }

    private static void dispatch(String type, Map<String, Object> vars) {
        // gamehooks are opt-in: enableGameHooks in creatorlink.toml (or the Settings screen)
        if (CreatorLinkClient.config == null || !CreatorLinkClient.config.enableGameHooks) return;
        CreatorLinkClient.RUNTIME.scheduler.schedule(0, () ->
                CreatorLinkClient.processor.handle("gamehooks", null, type, vars));
    }
}
