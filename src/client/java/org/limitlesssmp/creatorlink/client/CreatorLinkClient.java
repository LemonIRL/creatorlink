package org.limitlesssmp.creatorlink.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.limitlesssmp.creatorlink.CreatorLink;
import org.limitlesssmp.creatorlink.config.ClientConfig;
import org.limitlesssmp.creatorlink.config.ProfileManager;
import org.limitlesssmp.creatorlink.engine.ConfigIO;
import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.EventContext;
import org.limitlesssmp.creatorlink.engine.EventProcessor;
import org.limitlesssmp.creatorlink.engine.outcome.Outcome;
import org.limitlesssmp.creatorlink.net.Payloads;
import org.limitlesssmp.creatorlink.socket.SocketBase;
import org.limitlesssmp.creatorlink.socket.SocketManager;

import java.nio.file.Files;
import java.nio.file.Path;

public class CreatorLinkClient implements ClientModInitializer {
	public static final EngineRuntime RUNTIME = new EngineRuntime();
	public static EventProcessor processor;
	public static SocketManager sockets;
	public static ClientConfig config;
	public static ProfileManager profiles;
	private static Path configFile;
	private static String activeProfile;

	@Override
	public void onInitializeClient() {
		Path configRoot = FabricLoader.getInstance().getConfigDir();
		configFile = configRoot.resolve("creatorlink/creatorlink.toml");

		// One-time migration from an existing CCI install (config + profiles).
		Path cciToml = configRoot.resolve("contentcreatorintegration/contentcreatorintegration.toml");
		if (!Files.exists(configFile) && Files.exists(cciToml)) {
			config = ClientConfig.load(cciToml);
			config.save(configFile);
			CreatorLink.LOGGER.info("Migrated settings from contentcreatorintegration.toml");
		} else {
			config = ClientConfig.load(configFile);
		}

		processor = new EventProcessor(RUNTIME);
		sockets = new SocketManager(RUNTIME, processor);
		profiles = new ProfileManager(configRoot);
		profiles.migrateFromCci(configRoot);

		RUNTIME.setBridge(new ClientBridge());
		RUNTIME.maxEventCache = config.maxEventCache;
		RUNTIME.initGlobals(config.streamerName);
		RUNTIME.profileChanger = (profile, disableToast) -> {
			loadProfile(profile);
			if (!disableToast) {
				RUNTIME.bridge().toast(3, "CreatorLink", "Switched to profile: " + profile, null, null);
			}
		};

		loadProfile(config.defaultProfile);
		sockets.start(config, configFile);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			RUNTIME.scheduler.tick();
			GameHooks.tick(client);
		});
		GameHooks.register();
		net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register(
				(message, overlay) -> {
					if (!overlay) FeedbackCapture.offer(message);
				});

		// Dev/test hook: auto-open a screen shortly after boot.
		String autoOpen = System.getenv("CREATORLINK_AUTO_OPEN");
		if (autoOpen != null) {
			RUNTIME.scheduler.schedule(100, () -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(
					switch (autoOpen) {
						case "settings" -> new org.limitlesssmp.creatorlink.client.editor.SettingsScreen(null);
						case "events" -> new org.limitlesssmp.creatorlink.client.editor.EventViewerScreen(null);
						case "simple" -> new org.limitlesssmp.creatorlink.client.editor.SimpleSetupScreen(null);
						case "connections" -> new org.limitlesssmp.creatorlink.client.editor.ConnectionsScreen(null);
						default -> new org.limitlesssmp.creatorlink.client.editor.EditorScreen(null, activeProfileDir(),
								autoOpen.startsWith("editor:") ? autoOpen.substring(7) : "twitcheventsub");
					}));
		}

		ClientPlayNetworking.registerGlobalReceiver(Payloads.OutcomePayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					try {
						Outcome outcome = ConfigIO.GSON.fromJson(payload.outcomeJson(), Outcome.class);
						if (outcome != null) {
							EventContext ctx = RUNTIME.newContext("sendToPlayer", null, "outcome", null);
							outcome.run(RUNTIME, ctx);
						}
					} catch (Exception e) {
						EngineRuntime.LOGGER.warn("Bad sendToPlayer outcome: {}", e.toString());
					}
				}));

		registerClientCommands();
		CreatorLink.LOGGER.info("CreatorLink initialised (client), profile '{}'", activeProfile);
	}

	private static void loadProfile(String profile) {
		activeProfile = profile == null || profile.isEmpty() ? "default" : profile;
		profiles.loadProfile(activeProfile, RUNTIME, processor);
		loadOnlineConfigs();
	}

	/** onlineConfigs URLs override local configs of the same type once fetched. */
	private static void loadOnlineConfigs() {
		if (config == null || config.onlineConfigs == null) return;
		for (String url : config.onlineConfigs) {
			if (url == null || url.isEmpty()) continue;
			java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
					.timeout(java.time.Duration.ofSeconds(15)).GET().build();
			org.limitlesssmp.creatorlink.socket.Ws.HTTP.sendAsync(req, java.net.http.HttpResponse.BodyHandlers.ofString())
					.whenComplete((res, err) -> {
						if (err != null || res.statusCode() != 200) {
							EngineRuntime.LOGGER.warn("Online config {} failed: {}", url,
									err != null ? err.toString() : "HTTP " + res.statusCode());
							return;
						}
						try {
							var cfg = ConfigIO.GSON.fromJson(res.body(),
									org.limitlesssmp.creatorlink.engine.config.EventConfiguration.class);
							if (cfg != null && cfg.type != null) {
								cfg.from = url;
								RUNTIME.scheduler.schedule(0, () -> {
									processor.load(cfg.type, cfg);
									EngineRuntime.LOGGER.info("Loaded online config '{}' from {}", cfg.type, url);
								});
							}
						} catch (Exception e) {
							EngineRuntime.LOGGER.warn("Online config {} parse failed: {}", url, e.toString());
						}
					});
		}
	}

	/** Used by the editor's Save button to apply changes immediately. */
	public static void reloadActiveProfile() {
		loadProfile(activeProfile);
	}

	/**
	 * Replays a cached event through the pipeline on the next tick — the same
	 * scheduler hop live socket events take. Reports what happened via toast,
	 * local chat line, and the log, so a silent failure is impossible.
	 */
	public static void replayCached(EventContext cached) {
		EventContext copy = cached.copy();
		String label = copy.socketType + (copy.platform == null ? "" : "/" + copy.platform) + " → " + copy.eventType;
		EngineRuntime.LOGGER.info("[replay] dispatching {}", label);
		RUNTIME.scheduler.schedule(1, () -> {
			int fired = processor.handle(copy.socketType, copy.platform, copy.eventType, copy.varsView());
			String msg = fired > 0
					? "Replayed " + label + " — " + fired + " config event(s) fired"
					: "Replay of " + label + " matched no config event";
			EngineRuntime.LOGGER.info("[replay] {}", msg);
			RUNTIME.bridge().toast(3, "CreatorLink", msg, null, fired > 0 ? "green" : "red");
			RUNTIME.bridge().chat("§6[CreatorLink]§r " + msg, false, false);
		});
	}

	private static void replayFromCommand(
			net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, int index) {
		java.util.List<EventContext> events = RUNTIME.cachedEvents();
		if (events.isEmpty()) {
			source.sendFeedback(Component.literal("§6[CreatorLink]§r no events cached yet — trigger something on stream"));
			return;
		}
		if (index > events.size()) {
			source.sendFeedback(Component.literal("§6[CreatorLink]§c only " + events.size()
					+ " event(s) cached — see /creatorlink replay list"));
			return;
		}
		replayCached(events.get(index - 1));
	}

	/**
	 * Drag-and-drop / manual import of CCI (or CreatorLink) configs. Single
	 * .json files go into the active profile; a dropped folder either imports
	 * its .json files, or — when it looks like a whole CCI install (profile
	 * subfolders) — imports every profile. Reloads and toasts a summary.
	 */
	public static void importDropped(java.util.List<Path> dropped) {
		int[] c = new int[5]; // configs, skipped, profiles, tokens, rar
		importPaths(dropped, c);
		reloadActiveProfile();
		StringBuilder msg = new StringBuilder();
		if (c[2] > 0) msg.append(c[2]).append(" profile(s)");
		if (c[0] > 0) msg.append(msg.length() > 0 ? ", " : "").append(c[0]).append(" config(s)");
		if (c[3] > 0) msg.append(msg.length() > 0 ? ", " : "").append(c[3]).append(" token/setting(s)");
		if (msg.length() == 0) msg.append("nothing imported");
		else msg.insert(0, "Imported ");
		if (c[4] > 0) msg.append(" — .rar isn't supported, extract it or use .zip");
		else if (c[1] > 0) msg.append(" — ").append(c[1]).append(" file(s) skipped (not valid configs)");
		RUNTIME.bridge().toast(3, "CreatorLink", msg.toString(), null, null);
	}

	private static void importPaths(java.util.List<Path> paths, int[] c) {
		for (Path p : paths) {
			String lower = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
			if (Files.isDirectory(p)) {
				if (Files.exists(p.resolve("contentcreatorintegration.toml")) || looksLikeInstall(p)) {
					c[2] += profiles.importCciInstall(p);
					c[3] += mergeSettings(p.resolve("contentcreatorintegration.toml"));
					continue;
				}
				try (var files = Files.list(p)) {
					for (Path f : files.filter(x -> x.getFileName().toString().endsWith(".json")).toList()) {
						if (ProfileManager.importConfigFile(f, activeProfileDir()) != null) c[0]++;
						else c[1]++;
					}
				} catch (java.io.IOException e) {
					c[1]++;
				}
			} else if (lower.endsWith(".json")) {
				if (ProfileManager.importConfigFile(p, activeProfileDir()) != null) c[0]++;
				else c[1]++;
			} else if (lower.endsWith(".toml")) {
				c[3] += mergeSettings(p);
			} else if (lower.endsWith(".zip")) {
				// creators usually share their whole CCI folder zipped
				try {
					Path tmp = Files.createTempDirectory("creatorlink-import");
					unzip(p, tmp);
					java.util.List<Path> children;
					try (var s = Files.list(tmp)) {
						children = s.toList();
					}
					importPaths(children, c);
				} catch (Exception e) {
					EngineRuntime.LOGGER.warn("Zip import failed for {}: {}", p, e.toString());
					c[1]++;
				}
			} else if (lower.endsWith(".rar")) {
				c[4]++;
			} else {
				c[1]++;
			}
		}
	}

	private static void unzip(Path zip, Path dest) throws java.io.IOException {
		try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(Files.newInputStream(zip))) {
			java.util.zip.ZipEntry entry;
			while ((entry = in.getNextEntry()) != null) {
				String name = entry.getName();
				if (name.contains("logs/") || name.contains("libs/")) continue;
				Path out = dest.resolve(name).normalize();
				if (!out.startsWith(dest)) continue; // zip-slip guard
				if (entry.isDirectory()) {
					Files.createDirectories(out);
				} else {
					Files.createDirectories(out.getParent());
					Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	/**
	 * Merges tokens/settings from another toml (CCI or CreatorLink) into ours:
	 * list entries we don't have get appended, streamerName fills only if
	 * empty — existing working tokens are never overwritten. Restarts sockets
	 * when anything was added. Returns how many values were merged.
	 */
	public static int mergeSettings(Path toml) {
		if (!Files.exists(toml)) return 0;
		ClientConfig other = ClientConfig.load(toml);
		int added = 0;
		added += mergeList(config.twitchChats, other.twitchChats);
		added += mergeList(config.eventsubOauthTokens, other.eventsubOauthTokens);
		added += mergeList(config.eventsubRefreshTokens, other.eventsubRefreshTokens);
		added += mergeList(config.eventsubClientIDs, other.eventsubClientIDs);
		added += mergeList(config.streamlabsTokens, other.streamlabsTokens);
		added += mergeList(config.streamElementsTokens, other.streamElementsTokens);
		added += mergeList(config.donationAlertsTokens, other.donationAlertsTokens);
		added += mergeList(config.youtubeApiKeys, other.youtubeApiKeys);
		added += mergeList(config.youtubeChannelIds, other.youtubeChannelIds);
		if (config.streamerName.isEmpty() && !other.streamerName.isEmpty()) {
			config.streamerName = other.streamerName;
			added++;
		}
		if (added > 0) {
			config.save(configFile);
			RUNTIME.initGlobals(config.streamerName);
			sockets.start(config, configFile);
		}
		return added;
	}

	private static int mergeList(java.util.List<String> mine, java.util.List<String> theirs) {
		int added = 0;
		for (String s : theirs) {
			if (s != null && !s.isEmpty() && !mine.contains(s)) {
				mine.add(s);
				added++;
			}
		}
		return added;
	}

	/** A folder of profile subfolders (each holding .json) with no loose .json of its own. */
	private static boolean looksLikeInstall(Path dir) {
		boolean topJson = false, subProfiles = false;
		try (var files = Files.list(dir)) {
			for (Path f : files.toList()) {
				if (Files.isDirectory(f)) {
					try (var sub = Files.list(f)) {
						if (sub.anyMatch(x -> x.getFileName().toString().endsWith(".json"))) subProfiles = true;
					}
				} else if (f.getFileName().toString().endsWith(".json")) {
					topJson = true;
				}
			}
		} catch (java.io.IOException e) {
			return false;
		}
		return subProfiles && !topJson;
	}

	/** Used by the Connections screen and /creatorlink reconnect: re-read the toml and restart sockets. */
	public static void reloadConfigAndReconnect() {
		config = ClientConfig.load(configFile);
		sockets.start(config, configFile);
	}

	/** Used by the Settings screen: persist creatorlink.toml and restart sockets. */
	public static void saveConfigAndReconnect() {
		config.save(configFile);
		RUNTIME.maxEventCache = config.maxEventCache;
		RUNTIME.initGlobals(config.streamerName);
		sockets.start(config, configFile);
	}

	public static java.nio.file.Path activeProfileDir() {
		return profiles.profileDir(activeProfile);
	}

	/**
	 * Sends a command straight to the server dispatcher. Must send the raw
	 * packet: connection.sendCommand() is intercepted by the client command
	 * API, which would re-match our own /creatorlink tree and recurse forever.
	 */
	private static void sendServerCommand(String command) {
		var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
		if (connection != null) {
			connection.send(new net.minecraft.network.protocol.game.ServerboundChatCommandPacket(command));
		}
	}

	/** Opens a screen one tick later so the chat screen closing doesn't cancel it. */
	private static void openScreenDelayed(java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> screen) {
		RUNTIME.scheduler.schedule(1, () ->
				net.minecraft.client.Minecraft.getInstance().gui.setScreen(screen.get()));
	}

	private void registerClientCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommands.literal("creatorlink")
						// bare /creatorlink opens the editor, like CCI's /cci
						.executes(ctx -> {
							openScreenDelayed(() -> new org.limitlesssmp.creatorlink.client.editor.EditorScreen(
									null, activeProfileDir(), "twitcheventsub"));
							return Command.SINGLE_SUCCESS;
						})
						.then(ClientCommands.literal("status").executes(ctx -> {
							ctx.getSource().sendFeedback(Component.literal("§6[CreatorLink]§r profile: " + activeProfile));
							if (sockets.sockets().isEmpty()) {
								ctx.getSource().sendFeedback(Component.literal(" §7no sockets configured — edit config/creatorlink/creatorlink.toml"));
							}
							for (SocketBase s : sockets.sockets()) {
								String color = switch (s.status()) {
									case CONNECTED -> "§a";
									case CONNECTING -> "§e";
									case ERROR -> "§c";
									case DISCONNECTED -> "§7";
								};
								String detail = s.statusDetail().isEmpty() ? "" : " §8(" + s.statusDetail() + ")";
								ctx.getSource().sendFeedback(Component.literal(" " + s.name() + ": " + color + s.status() + "§r" + detail));
							}
							return Command.SINGLE_SUCCESS;
						}))
						.then(ClientCommands.literal("reconnect").executes(ctx -> {
							reloadConfigAndReconnect();
							ctx.getSource().sendFeedback(Component.literal("§6[CreatorLink]§r restarting all sockets..."));
							return Command.SINGLE_SUCCESS;
						}))
						.then(ClientCommands.literal("connections").executes(ctx -> {
							openScreenDelayed(() -> new org.limitlesssmp.creatorlink.client.editor.ConnectionsScreen(null));
							return Command.SINGLE_SUCCESS;
						}))
						.then(ClientCommands.literal("importcci")
								.executes(ctx -> {
									Path cci = configFile.getParent().getParent().resolve("contentcreatorintegration");
									int n = profiles.importCciInstall(cci);
									int tokens = mergeSettings(cci.resolve("contentcreatorintegration.toml"));
									loadProfile(activeProfile);
									ctx.getSource().sendFeedback(Component.literal(n > 0 || tokens > 0
											? "§6[CreatorLink]§r imported " + n + " CCI profile(s), "
											+ tokens + " token/setting(s) from " + cci
											: "§6[CreatorLink]§r no CCI install found at " + cci
											+ " §7— or drag config files onto any CreatorLink screen"));
									return Command.SINGLE_SUCCESS;
								})
								.then(ClientCommands.argument("path", StringArgumentType.greedyString()).executes(ctx -> {
									Path p = Path.of(StringArgumentType.getString(ctx, "path"));
									if (!Files.exists(p)) {
										ctx.getSource().sendFeedback(Component.literal("§6[CreatorLink]§c not found: " + p));
										return Command.SINGLE_SUCCESS;
									}
									importDropped(java.util.List.of(p));
									return Command.SINGLE_SUCCESS;
								})))
						.then(ClientCommands.literal("reload").executes(ctx -> {
							config = ClientConfig.load(configFile);
							RUNTIME.maxEventCache = config.maxEventCache;
							RUNTIME.initGlobals(config.streamerName);
							loadProfile(activeProfile);
							ctx.getSource().sendFeedback(Component.literal("§6[CreatorLink]§r reloaded profile '" + activeProfile + "'"));
							return Command.SINGLE_SUCCESS;
						}))
						.then(ClientCommands.literal("editor").executes(ctx -> {
							openScreenDelayed(() -> new org.limitlesssmp.creatorlink.client.editor.EditorScreen(
									null, activeProfileDir(), "twitcheventsub"));
							return Command.SINGLE_SUCCESS;
						}))
						.then(ClientCommands.literal("events").executes(ctx -> {
							openScreenDelayed(() -> new org.limitlesssmp.creatorlink.client.editor.EventViewerScreen(null));
							return Command.SINGLE_SUCCESS;
						}))
						// /creatorlink replay [n] — replay the latest (or nth-latest) cached
						// event without the GUI; "replay list" prints the cache numbered
						.then(ClientCommands.literal("replay")
								.executes(ctx -> {
									replayFromCommand(ctx.getSource(), 1);
									return Command.SINGLE_SUCCESS;
								})
								.then(ClientCommands.literal("list").executes(ctx -> {
									var events = RUNTIME.cachedEvents();
									if (events.isEmpty()) {
										ctx.getSource().sendFeedback(Component.literal(
												"§6[CreatorLink]§r no events cached yet — trigger something on stream"));
										return Command.SINGLE_SUCCESS;
									}
									ctx.getSource().sendFeedback(Component.literal("§6[CreatorLink]§r cached events (1 = latest):"));
									for (int i = 0; i < events.size(); i++) {
										EventContext e = events.get(i);
										ctx.getSource().sendFeedback(Component.literal(" §e" + (i + 1) + "§r " + e.socketType
												+ (e.platform == null ? "" : "/" + e.platform) + " → " + e.eventType));
									}
									return Command.SINGLE_SUCCESS;
								}))
								.then(ClientCommands.argument("index", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
										.executes(ctx -> {
											replayFromCommand(ctx.getSource(),
													com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "index"));
											return Command.SINGLE_SUCCESS;
										})))
						.then(ClientCommands.literal("settings").executes(ctx -> {
							openScreenDelayed(() -> new org.limitlesssmp.creatorlink.client.editor.SettingsScreen(null));
							return Command.SINGLE_SUCCESS;
						}))
						.then(ClientCommands.literal("simple").executes(ctx -> {
							openScreenDelayed(() -> new org.limitlesssmp.creatorlink.client.editor.SimpleSetupScreen(null));
							return Command.SINGLE_SUCCESS;
						}))
						// the client root shadows the server's /creatorlink — forward the
						// server-side admin subcommands so they still work from a modded client
						.then(ClientCommands.literal("whitelist")
								.then(ClientCommands.argument("player", StringArgumentType.word()).executes(ctx -> {
									sendServerCommand("creatorlink whitelist " + StringArgumentType.getString(ctx, "player"));
									return Command.SINGLE_SUCCESS;
								})))
						.then(ClientCommands.literal("dewhitelist")
								.then(ClientCommands.argument("player", StringArgumentType.word()).executes(ctx -> {
									sendServerCommand("creatorlink dewhitelist " + StringArgumentType.getString(ctx, "player"));
									return Command.SINGLE_SUCCESS;
								})))
						.then(ClientCommands.literal("setBlacklist").executes(ctx -> {
							sendServerCommand("creatorlink setBlacklist");
							return Command.SINGLE_SUCCESS;
						}))
						.then(ClientCommands.literal("profile")
								.then(ClientCommands.argument("name", StringArgumentType.word())
										.executes(ctx -> {
											String name = StringArgumentType.getString(ctx, "name");
											loadProfile(name);
											config.defaultProfile = name;
											config.save(configFile);
											ctx.getSource().sendFeedback(Component.literal("§6[CreatorLink]§r switched to profile '" + name + "'"));
											return Command.SINGLE_SUCCESS;
										})))));
	}
}
