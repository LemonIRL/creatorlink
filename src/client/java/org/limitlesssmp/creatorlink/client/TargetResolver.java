package org.limitlesssmp.creatorlink.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * Client-side target resolution for conditions. Supports @s/@p/player name/
 * entity name/UUID against the loaded client level, and the documented
 * query arguments (name, health, location, biome, dim, time, ...).
 */
public final class TargetResolver {
    private TargetResolver() {}

    public static Entity resolve(String selector) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || selector == null || selector.isEmpty()) return null;
        String s = selector.trim();
        if (s.equals("@s") || s.equals("@p") || s.equalsIgnoreCase("streamer")
                || (mc.player != null && s.equalsIgnoreCase(mc.player.getName().getString()))) {
            return mc.player;
        }
        UUID uuid = null;
        try {
            uuid = UUID.fromString(s);
        } catch (IllegalArgumentException ignored) {}
        Entity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            boolean match = uuid != null ? e.getUUID().equals(uuid)
                    : e.getName().getString().equalsIgnoreCase(s)
                      || (e.getCustomName() != null && e.getCustomName().getString().equalsIgnoreCase(s));
            if (!match) continue;
            double dist = mc.player == null ? 0 : e.distanceToSqr(mc.player);
            if (dist < nearestDist) {
                nearest = e;
                nearestDist = dist;
            }
        }
        return nearest;
    }

    public static String query(Entity e, String argument) {
        if (e == null || argument == null) return null;
        Minecraft mc = Minecraft.getInstance();
        return switch (argument) {
            case "name" -> e.getName().getString();
            case "uuid" -> e.getUUID().toString();
            case "health" -> e instanceof LivingEntity l ? String.valueOf(l.getHealth()) : null;
            case "maxhealth" -> e instanceof LivingEntity l ? String.valueOf(l.getMaxHealth()) : null;
            case "armor" -> e instanceof LivingEntity l ? String.valueOf(l.getArmorValue()) : null;
            case "locationx" -> String.valueOf(e.getX());
            case "locationy" -> String.valueOf(e.getY());
            case "locationz" -> String.valueOf(e.getZ());
            case "rotationyaw" -> String.valueOf(e.getYRot());
            case "rotationpitch" -> String.valueOf(e.getXRot());
            case "biome" -> e.level().getBiome(e.blockPosition()).unwrapKey()
                    .map(k -> k.identifier().toString()).orElse(null);
            case "dim", "dimension" -> e.level().dimension().identifier().toString();
            case "light", "lightblock" -> String.valueOf(e.level().getMaxLocalRawBrightness(e.blockPosition()));
            case "diff", "difficulty" -> e.level().getDifficulty().getSerializedName();
            case "time" -> String.valueOf(e.level().getOverworldClockTime() % 24000L);
            case "day" -> String.valueOf(e.level().getOverworldClockTime() / 24000L);
            default -> null;
        };
    }

    /** Random search for a clear space near the target; returns "x y z" or null. */
    public static String freeSpace(Entity around, double horiRadius, double vertRadius, int tries,
                                   double height, double width, double minHoriDistance, boolean requireSolidBlock) {
        if (around == null || around.level() == null) return null;
        var level = around.level();
        var random = around.level().getRandom();
        int h = Math.max(1, (int) Math.ceil(height));
        int w = Math.max(1, (int) Math.ceil(width));
        for (int attempt = 0; attempt < Math.max(1, tries); attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = minHoriDistance + random.nextDouble() * Math.max(0.01, horiRadius - minHoriDistance);
            int x = (int) Math.floor(around.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(around.getZ() + Math.sin(angle) * dist);
            int yBase = (int) Math.floor(around.getY() + (random.nextDouble() * 2 - 1) * vertRadius);
            candidate:
            for (int y = yBase; y >= yBase - (int) vertRadius; y--) {
                for (int dy = 0; dy < h; dy++) {
                    for (int dx = 0; dx < w; dx++) {
                        for (int dz = 0; dz < w; dz++) {
                            if (!level.getBlockState(new BlockPos(x + dx, y + dy, z + dz)).isAir()) {
                                continue candidate;
                            }
                        }
                    }
                }
                if (requireSolidBlock && level.getBlockState(new BlockPos(x, y - 1, z)).isAir()) {
                    continue;
                }
                return x + " " + y + " " + z;
            }
        }
        return null;
    }
}
