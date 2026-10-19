package org.limitlesssmp.creatorlink;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import org.limitlesssmp.creatorlink.config.CommonConfig;
import org.limitlesssmp.creatorlink.net.Payloads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreatorLink implements ModInitializer {
	public static final String MOD_ID = "creatorlink";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static CommonConfig commonConfig;

	@Override
	public void onInitialize() {
		commonConfig = CommonConfig.load(FabricLoader.getInstance().getConfigDir()
				.resolve("creatorlink/creatorlink-common.toml"));
		Payloads.register();
		registerServerReceivers();
		registerCommands();

		// Dev/test hook: run a command as the server once it's up and log the verdict.
		String testCmd = System.getenv("CREATORLINK_TEST_COMMAND");
		if (testCmd != null && !testCmd.isEmpty()) {
			net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
				String stripped = testCmd.startsWith("/") ? testCmd.substring(1) : testCmd;
				try {
					var parse = server.getCommands().getDispatcher().parse(stripped,
							server.createCommandSourceStack());
					int result = server.getCommands().getDispatcher().execute(parse);
					LOGGER.info("[test-command] OK (result {}): {}", result, testCmd);
				} catch (Exception e) {
					LOGGER.warn("[test-command] FAILED: {} — {}", testCmd,
							e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
				}
			});
		}

		LOGGER.info("CreatorLink initialised (common)");
	}

	private void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(Payloads.CommandPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> executeForPlayer(context.server(), player,
					payload.command(), payload.asSelf(), payload.silent()));
		});
		ServerPlayNetworking.registerGlobalReceiver(Payloads.ServerOpPayload.TYPE, (payload, context) ->
				context.server().execute(() ->
						org.limitlesssmp.creatorlink.server.ServerOps.handle(context.server(), context.player(), payload)));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.SendToPlayerPayload.TYPE, (payload, context) -> {
			ServerPlayer sender = context.player();
			context.server().execute(() -> {
				if (!isAllowedOnServer(context.server(), sender)) {
					if (!payload.silenceErrors()) {
						sender.sendSystemMessage(Component.literal("[CreatorLink] You are not whitelisted on this server."));
					}
					return;
				}
				ServerPlayer target = context.server().getPlayerList().getPlayerByName(payload.player());
				if (target == null) {
					if (!payload.silenceErrors()) {
						sender.sendSystemMessage(Component.literal("[CreatorLink] Player not found: " + payload.player()));
					}
					return;
				}
				ServerPlayNetworking.send(target, new Payloads.OutcomePayload(payload.outcomeJson()));
			});
		});
	}

	/** The singleplayer host is always allowed, like on an integrated server. */
	public static boolean isAllowedOnServer(MinecraftServer server, ServerPlayer player) {
		if (server.isSingleplayer()) return true;
		return commonConfig.isAllowed(player.getName().getString());
	}

	private void executeForPlayer(MinecraftServer server, ServerPlayer player, String command, boolean asSelf, boolean silent) {
		if (!isAllowedOnServer(server, player)) {
			player.sendSystemMessage(Component.literal("[CreatorLink] You are not whitelisted on this server."));
			return;
		}
		String root = command.startsWith("/") ? command.substring(1) : command;
		int space = root.indexOf(' ');
		String baseCommand = space < 0 ? root : root.substring(0, space);
		if (commonConfig.disallowedCommands.stream().anyMatch(c -> c.equalsIgnoreCase(baseCommand))) {
			player.sendSystemMessage(Component.literal("[CreatorLink] The command '" + baseCommand + "' is disallowed on this server."));
			return;
		}
		CommandSourceStack source = asSelf
				? player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS)
				: server.createCommandSourceStack();
		if (silent) source = source.withSuppressedOutput();
		// Execute through the dispatcher directly so failures can't vanish:
		// "silent" suppresses normal command feedback, but a command that
		// errors outright is logged AND reported back to the sending player.
		String stripped = command.startsWith("/") ? command.substring(1) : command;
		try {
			var parse = server.getCommands().getDispatcher().parse(stripped, source);
			server.getCommands().getDispatcher().execute(parse);
		} catch (Exception e) {
			String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			LOGGER.warn("Command from {} failed: {} — {}", player.getName().getString(), command, reason);
			player.sendSystemMessage(Component.literal("§c[CreatorLink] Command failed: §7" + reason));
		}
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("creatorlink")
						.requires(CreatorLink::hasConfiguredPermission)
						.then(Commands.literal("whitelist").then(Commands.argument("player", StringArgumentType.word())
								.executes(ctx -> {
									String name = StringArgumentType.getString(ctx, "player");
									if (commonConfig.whitelistedUsers.stream().noneMatch(u -> u.equalsIgnoreCase(name))) {
										commonConfig.whitelistedUsers.add(name);
										commonConfig.save();
									}
									ctx.getSource().sendSuccess(() -> Component.literal("[CreatorLink] Whitelisted " + name), true);
									return 1;
								})))
						.then(Commands.literal("dewhitelist").then(Commands.argument("player", StringArgumentType.word())
								.executes(ctx -> {
									String name = StringArgumentType.getString(ctx, "player");
									commonConfig.whitelistedUsers.removeIf(u -> u.equalsIgnoreCase(name));
									commonConfig.save();
									ctx.getSource().sendSuccess(() -> Component.literal("[CreatorLink] Removed " + name + " from the whitelist"), true);
									return 1;
								})))
						.then(Commands.literal("setBlacklist").executes(ctx -> {
							commonConfig.enableBlacklist = !commonConfig.enableBlacklist;
							commonConfig.save();
							ctx.getSource().sendSuccess(() -> Component.literal("[CreatorLink] List is now a "
									+ (commonConfig.enableBlacklist ? "blacklist" : "whitelist")), true);
							return 1;
						}))));
	}

	private static boolean hasConfiguredPermission(CommandSourceStack src) {
		Permission required = switch (commonConfig.commandPermissionLevel) {
			case 0 -> null;
			case 1 -> Permissions.COMMANDS_MODERATOR;
			case 2 -> Permissions.COMMANDS_GAMEMASTER;
			case 4 -> Permissions.COMMANDS_OWNER;
			default -> Permissions.COMMANDS_ADMIN;
		};
		return required == null || src.permissions().hasPermission(required);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
