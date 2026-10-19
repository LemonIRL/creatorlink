package org.limitlesssmp.creatorlink.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.limitlesssmp.creatorlink.engine.EngineRuntime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-side config (creatorlink-common.toml); key names match CCI's
 * contentcreatorintegration-common.toml.
 */
public final class CommonConfig {
    public List<String> whitelistedUsers = new ArrayList<>();
    public boolean enableBlacklist = false;
    public int commandPermissionLevel = 3;
    public List<String> disallowedCommands = new ArrayList<>();
    public List<String> logTypes = new ArrayList<>();

    private transient Path file;

    public static CommonConfig load(Path file) {
        CommonConfig cfg = new CommonConfig();
        cfg.file = file;
        if (!Files.exists(file)) {
            cfg.save();
            return cfg;
        }
        try (CommentedFileConfig toml = CommentedFileConfig.builder(file).build()) {
            toml.load();
            cfg.whitelistedUsers = toml.getOrElse("general.whitelistedUsers", cfg.whitelistedUsers);
            cfg.enableBlacklist = toml.getOrElse("general.enableBlacklist", cfg.enableBlacklist);
            cfg.commandPermissionLevel = toml.getIntOrElse("general.commandPermissionLevel", cfg.commandPermissionLevel);
            cfg.disallowedCommands = toml.getOrElse("general.disallowedCommands", cfg.disallowedCommands);
            cfg.logTypes = toml.getOrElse("general.logTypes", cfg.logTypes);
        } catch (Exception e) {
            EngineRuntime.LOGGER.error("Failed to load {}: {}", file, e.toString());
        }
        return cfg;
    }

    public void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            try (CommentedFileConfig toml = CommentedFileConfig.builder(file).build()) {
                if (Files.exists(file)) toml.load();
                toml.setComment("general.whitelistedUsers", " Whitelisted Users. These users will be able to trigger serverside outcomes.");
                toml.set("general.whitelistedUsers", whitelistedUsers);
                toml.setComment("general.enableBlacklist", " Converts the list of whitelisted users to be the list of blacklisted users.");
                toml.set("general.enableBlacklist", enableBlacklist);
                toml.set("general.commandPermissionLevel", commandPermissionLevel);
                toml.setComment("general.disallowedCommands", " Commands prevented from being executed by CommandOutcome, e.g. \"time\".");
                toml.set("general.disallowedCommands", disallowedCommands);
                toml.set("general.logTypes", logTypes);
                toml.save();
            }
        } catch (Exception e) {
            EngineRuntime.LOGGER.error("Failed to save {}: {}", file, e.toString());
        }
    }

    public boolean isAllowed(String playerName) {
        boolean listed = whitelistedUsers.stream().anyMatch(u -> u.equalsIgnoreCase(playerName));
        return enableBlacklist != listed;
    }
}
