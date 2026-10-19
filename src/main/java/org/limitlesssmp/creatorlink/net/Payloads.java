package org.limitlesssmp.creatorlink.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.limitlesssmp.creatorlink.CreatorLink;

/** Custom payloads: client→server command execution and server→client outcomes. */
public final class Payloads {
    private Payloads() {}

    /** C2S: run a command server-side for the sending (whitelisted) player. */
    public record CommandPayload(String command, boolean asSelf, boolean silent) implements CustomPacketPayload {
        public static final Type<CommandPayload> TYPE = new Type<>(CreatorLink.id("command"));
        public static final StreamCodec<io.netty.buffer.ByteBuf, CommandPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, CommandPayload::command,
                ByteBufCodecs.BOOL, CommandPayload::asSelf,
                ByteBufCodecs.BOOL, CommandPayload::silent,
                CommandPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * C2S: entity-manipulation outcome (health/food/motion/mount/inventory)
     * executed server-side with real target selectors. Args are op-specific.
     */
    public record ServerOpPayload(String op, String target, String a, String b, String c) implements CustomPacketPayload {
        public static final Type<ServerOpPayload> TYPE = new Type<>(CreatorLink.id("server_op"));
        public static final StreamCodec<io.netty.buffer.ByteBuf, ServerOpPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ServerOpPayload::op,
                ByteBufCodecs.STRING_UTF8, ServerOpPayload::target,
                ByteBufCodecs.STRING_UTF8, ServerOpPayload::a,
                ByteBufCodecs.STRING_UTF8, ServerOpPayload::b,
                ByteBufCodecs.STRING_UTF8, ServerOpPayload::c,
                ServerOpPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S: relay an outcome to another whitelisted player (cciSendToPlayer). */
    public record SendToPlayerPayload(String player, String outcomeJson, boolean silenceErrors) implements CustomPacketPayload {
        public static final Type<SendToPlayerPayload> TYPE = new Type<>(CreatorLink.id("send_to_player"));
        public static final StreamCodec<io.netty.buffer.ByteBuf, SendToPlayerPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SendToPlayerPayload::player,
                ByteBufCodecs.STRING_UTF8, SendToPlayerPayload::outcomeJson,
                ByteBufCodecs.BOOL, SendToPlayerPayload::silenceErrors,
                SendToPlayerPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** S2C: an outcome (JSON) for the receiving client to run. */
    public record OutcomePayload(String outcomeJson) implements CustomPacketPayload {
        public static final Type<OutcomePayload> TYPE = new Type<>(CreatorLink.id("outcome"));
        public static final StreamCodec<io.netty.buffer.ByteBuf, OutcomePayload> CODEC =
                ByteBufCodecs.STRING_UTF8.map(OutcomePayload::new, OutcomePayload::outcomeJson);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(CommandPayload.TYPE, CommandPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SendToPlayerPayload.TYPE, SendToPlayerPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerOpPayload.TYPE, ServerOpPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OutcomePayload.TYPE, OutcomePayload.CODEC);
    }
}
