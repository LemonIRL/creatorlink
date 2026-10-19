package org.limitlesssmp.creatorlink.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.limitlesssmp.creatorlink.engine.EngineRuntime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Client mod config (creatorlink.toml). Key names intentionally match CCI's
 * contentcreatorintegration.toml so an existing file can be migrated 1:1.
 */
public final class ClientConfig {
    // [general]
    public boolean firstRun = true;
    public String streamerName = "";
    public int maxAutomaticReconnects = 3;
    public boolean enableInformationToasts = true;
    public List<String> onlineConfigs = new ArrayList<>();
    public boolean allowOutcomesRequiringServerWait = true;
    public int maxEventCache = 20;
    public List<String> logTypes = new ArrayList<>();
    public String defaultProfile = "default";
    /** Game events (gamehooks.json) only dispatch when this is enabled. */
    public boolean enableGameHooks = false;

    // [socket]
    public List<String> streamlabsTokens = new ArrayList<>();
    public List<String> streamElementsTokens = new ArrayList<>();
    public List<String> donationAlertsTokens = new ArrayList<>();
    public List<String> twitchChats = new ArrayList<>();
    public List<String> eventsubOauthTokens = new ArrayList<>();
    public List<String> eventsubRefreshTokens = new ArrayList<>();
    public List<String> eventsubClientIDs = new ArrayList<>();
    public List<String> youtubeApiKeys = new ArrayList<>();
    public List<String> youtubeChannelIds = new ArrayList<>();

    public static ClientConfig load(Path file) {
        ClientConfig cfg = new ClientConfig();
        if (!Files.exists(file)) {
            cfg.save(file);
            return cfg;
        }
        try (CommentedFileConfig toml = CommentedFileConfig.builder(file).build()) {
            toml.load();
            cfg.firstRun = toml.getOrElse("general.firstRun", cfg.firstRun);
            cfg.streamerName = toml.getOrElse("general.streamerName", cfg.streamerName);
            cfg.maxAutomaticReconnects = toml.getIntOrElse("general.maxAutomaticReconnects", cfg.maxAutomaticReconnects);
            cfg.enableInformationToasts = toml.getOrElse("general.enableInformationToasts", cfg.enableInformationToasts);
            cfg.onlineConfigs = toml.getOrElse("general.onlineConfigs", cfg.onlineConfigs);
            cfg.allowOutcomesRequiringServerWait = toml.getOrElse("general.allowOutcomesRequiringServerWait", cfg.allowOutcomesRequiringServerWait);
            cfg.maxEventCache = toml.getIntOrElse("general.maxEventCache", cfg.maxEventCache);
            cfg.logTypes = toml.getOrElse("general.logTypes", cfg.logTypes);
            cfg.defaultProfile = toml.getOrElse("general.defaultProfile", cfg.defaultProfile);
            cfg.enableGameHooks = toml.getOrElse("general.enableGameHooks", cfg.enableGameHooks);

            cfg.streamlabsTokens = toml.getOrElse("socket.streamlabsTokens", cfg.streamlabsTokens);
            // CCI writes BOTH SE keys (new + legacy) and either may hold the JWT —
            // merge them; preferring one key loses tokens stored under the other
            List<String> seTokens = new ArrayList<>(
                    toml.getOrElse("socket.streamElementsWebsocketTokens", new ArrayList<>()));
            for (String t : toml.getOrElse("socket.streamElementsTokens", new ArrayList<String>())) {
                if (t != null && !t.isEmpty() && !seTokens.contains(t)) seTokens.add(t);
            }
            seTokens.removeIf(t -> t == null || t.isEmpty());
            if (!seTokens.isEmpty()) cfg.streamElementsTokens = seTokens;
            cfg.donationAlertsTokens = toml.getOrElse("socket.donationAlertsTokens", cfg.donationAlertsTokens);
            cfg.twitchChats = toml.getOrElse("socket.twitchChats", cfg.twitchChats);
            cfg.eventsubOauthTokens = toml.getOrElse("socket.eventsubOauthTokens", cfg.eventsubOauthTokens);
            cfg.eventsubRefreshTokens = toml.getOrElse("socket.eventsubRefreshTokens", cfg.eventsubRefreshTokens);
            cfg.eventsubClientIDs = toml.getOrElse("socket.eventsubClientIDs", cfg.eventsubClientIDs);
            cfg.youtubeApiKeys = toml.getOrElse("socket.youtubeApiKeys", cfg.youtubeApiKeys);
            cfg.youtubeChannelIds = toml.getOrElse("socket.youtubeChannelIds", cfg.youtubeChannelIds);
            // CCI compatibility: googleApiKey (single string) and youtubeChats channel list
            String googleKey = toml.getOrElse("socket.googleApiKey", "");
            if (!googleKey.isEmpty() && cfg.youtubeApiKeys.isEmpty()) cfg.youtubeApiKeys.add(googleKey);
            List<String> youtubeChats = toml.getOrElse("socket.youtubeChats", new ArrayList<>());
            if (cfg.youtubeChannelIds.isEmpty()) cfg.youtubeChannelIds.addAll(youtubeChats);
        } catch (Exception e) {
            EngineRuntime.LOGGER.error("Failed to load {}: {}", file, e.toString());
        }
        return cfg;
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (CommentedFileConfig toml = CommentedFileConfig.builder(file).build()) {
                if (Files.exists(file)) toml.load();
                toml.set("general.firstRun", firstRun);
                toml.setComment("general.streamerName", " Set this if your streamer name is different from your Minecraft name for the $streamer global variable.");
                toml.set("general.streamerName", streamerName);
                toml.set("general.maxAutomaticReconnects", maxAutomaticReconnects);
                toml.set("general.enableInformationToasts", enableInformationToasts);
                toml.set("general.onlineConfigs", onlineConfigs);
                toml.set("general.allowOutcomesRequiringServerWait", allowOutcomesRequiringServerWait);
                toml.set("general.maxEventCache", maxEventCache);
                toml.setComment("general.logTypes", " Available log types: cci, debug, event, socket_status, socket_event");
                toml.set("general.logTypes", logTypes);
                toml.set("general.defaultProfile", defaultProfile);
                toml.setComment("general.enableGameHooks", " Dispatch in-game events (gamehooks.json): join/death/blockBreak/chat etc. Off by default.");
                toml.set("general.enableGameHooks", enableGameHooks);

                toml.setComment("socket.twitchChats", " Twitch channels' chat to listen to. One channel per line, lowercase.");
                toml.set("socket.twitchChats", twitchChats);
                toml.setComment("socket.eventsubOauthTokens", " Twitch OAuth token(s) for EventSub. Generate at https://twitchtokengenerator.com/quick/l7oG2vcNOE");
                toml.set("socket.eventsubOauthTokens", eventsubOauthTokens);
                toml.setComment("socket.eventsubRefreshTokens", " Used to renew the access token through twitchtokengenerator.com (the service that issued it). Treat all tokens in this file like passwords: never share this file.");
                toml.set("socket.eventsubRefreshTokens", eventsubRefreshTokens);
                toml.setComment("socket.eventsubClientIDs", " Leave empty to use twitchtokengenerator.com's Client ID.");
                toml.set("socket.eventsubClientIDs", eventsubClientIDs);
                toml.set("socket.streamlabsTokens", streamlabsTokens);
                toml.setComment("socket.streamElementsTokens", " StreamElements JWT token(s): Dashboard -> profile -> Show secrets");
                toml.set("socket.streamElementsTokens", streamElementsTokens);
                toml.set("socket.donationAlertsTokens", donationAlertsTokens);
                toml.set("socket.youtubeApiKeys", youtubeApiKeys);
                toml.set("socket.youtubeChannelIds", youtubeChannelIds);
                toml.save();
                restrictToOwner(file);
            }
        } catch (Exception e) {
            EngineRuntime.LOGGER.error("Failed to save {}: {}", file, e.toString());
        }
    }

    /** The file holds tokens: make it owner-only where the filesystem supports POSIX permissions. */
    private static void restrictToOwner(Path file) {
        try {
            Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | java.io.IOException ignored) {
            // non-POSIX filesystem (e.g. Windows): leave the default ACLs
        }
    }
}
