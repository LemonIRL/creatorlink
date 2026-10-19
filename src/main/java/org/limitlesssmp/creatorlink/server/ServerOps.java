package org.limitlesssmp.creatorlink.server;

import com.mojang.brigadier.StringReader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.limitlesssmp.creatorlink.CreatorLink;
import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.net.Payloads;

import java.util.List;
import java.util.Optional;

/**
 * Server-side entity manipulation for the health/food/motion/mount/inventory
 * outcomes, using real command target selectors on behalf of a whitelisted player.
 */
public final class ServerOps {
    private ServerOps() {}

    public static void handle(MinecraftServer server, ServerPlayer sender, Payloads.ServerOpPayload p) {
        if (!CreatorLink.isAllowedOnServer(server, sender)) {
            sender.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "[CreatorLink] You are not whitelisted on this server."));
            return;
        }
        try {
            switch (p.op()) {
                case "health" -> {
                    double amount = parse(p.a(), 0);
                    for (Entity e : resolve(sender, p.target())) {
                        if (e instanceof LivingEntity living) {
                            if (amount >= 0) {
                                living.heal((float) amount);
                            } else {
                                living.hurtServer((ServerLevel) living.level(),
                                        living.level().damageSources().generic(), (float) -amount);
                            }
                        }
                    }
                }
                case "food" -> {
                    for (Entity e : resolve(sender, p.target())) {
                        if (e instanceof Player player) {
                            if (!p.a().isEmpty()) {
                                player.getFoodData().setFoodLevel(Math.max(0, Math.min(20,
                                        player.getFoodData().getFoodLevel() + (int) parse(p.a(), 0))));
                            }
                            if (!p.b().isEmpty()) {
                                player.getFoodData().setSaturation((float) parse(p.b(), 0));
                            }
                            if (!p.c().isEmpty()) {
                                player.getFoodData().addExhaustion((float) parse(p.c(), 0));
                            }
                        }
                    }
                }
                case "motion" -> {
                    double amount = parse(p.a(), 0);
                    String axis = p.b();
                    boolean relative = Boolean.parseBoolean(p.c());
                    for (Entity e : resolve(sender, p.target())) {
                        Vec3 d = e.getDeltaMovement();
                        Vec3 next = switch (axis) {
                            case "x" -> new Vec3(relative ? d.x + amount : amount, d.y, d.z);
                            case "z" -> new Vec3(d.x, d.y, relative ? d.z + amount : amount);
                            default -> new Vec3(d.x, relative ? d.y + amount : amount, d.z);
                        };
                        e.setDeltaMovement(next);
                        e.hurtMarked = true; // sync velocity to clients
                    }
                }
                case "mount" -> {
                    List<? extends Entity> riders = resolve(sender, p.target());
                    List<? extends Entity> ridden = resolve(sender, p.a());
                    boolean force = Boolean.parseBoolean(p.b());
                    boolean forceToTop = Boolean.parseBoolean(p.c());
                    if (!riders.isEmpty() && !ridden.isEmpty()) {
                        Entity mountTarget = ridden.get(0);
                        if (forceToTop) {
                            while (!mountTarget.getPassengers().isEmpty()) {
                                mountTarget = mountTarget.getPassengers().get(0);
                            }
                        }
                        riders.get(0).startRiding(mountTarget, force, true);
                    }
                }
                case "inventory" -> {
                    String funcType = p.a();
                    int index = (int) parse(p.b(), -1);
                    String args = p.c();
                    for (Entity e : resolve(sender, p.target())) {
                        if (e instanceof Player player) inventoryOp(player, funcType, index, args);
                    }
                }
                default -> EngineRuntime.LOGGER.warn("Unknown server op {}", p.op());
            }
        } catch (Exception e) {
            EngineRuntime.LOGGER.warn("Server op {} failed: {}", p.op(), e.toString());
        }
    }

    private static void inventoryOp(Player player, String funcType, int index, String args) {
        var inv = player.getInventory();
        switch (funcType) {
            case "drop" -> {
                if (index >= 0 && index < inv.getContainerSize()) {
                    ItemStack stack = inv.getItem(index).copy();
                    inv.setItem(index, ItemStack.EMPTY);
                    if (!stack.isEmpty()) player.drop(stack, true);
                }
            }
            case "delete" -> {
                if (index >= 0 && index < inv.getContainerSize()) inv.setItem(index, ItemStack.EMPTY);
            }
            case "amount" -> {
                if (index >= 0 && index < inv.getContainerSize() && !inv.getItem(index).isEmpty()) {
                    inv.getItem(index).setCount(Math.max(1, (int) parse(args, 1)));
                }
            }
            case "add" -> {
                String[] parts = args.trim().split(" ");
                Identifier id = Identifier.tryParse(parts[0]);
                if (id == null) return;
                Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
                if (item.isEmpty()) return;
                int count = parts.length > 1 ? (int) parse(parts[1], 1) : 1;
                ItemStack stack = new ItemStack(item.get(), count);
                if (!player.getInventory().add(stack)) player.drop(stack, true);
            }
            case "swap" -> {
                int other = (int) parse(args, -1);
                if (index >= 0 && other >= 0 && index < inv.getContainerSize() && other < inv.getContainerSize()) {
                    ItemStack tmp = inv.getItem(index);
                    inv.setItem(index, inv.getItem(other));
                    inv.setItem(other, tmp);
                }
            }
            case "playerSwap" -> {
                ItemStack main = player.getMainHandItem().copy();
                ItemStack off = player.getOffhandItem().copy();
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, off);
                player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, main);
            }
            default -> EngineRuntime.LOGGER.warn("Unknown inventory func {}", funcType);
        }
    }

    private static List<? extends Entity> resolve(ServerPlayer sender, String selector) throws Exception {
        CommandSourceStack source = sender.createCommandSourceStack()
                .withPermission(PermissionSet.ALL_PERMISSIONS).withSuppressedOutput();
        return EntityArgument.entities().parse(new StringReader(selector)).findEntities(source);
    }

    private static double parse(String s, double fallback) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
