package org.limitlesssmp.creatorlink.config;

import org.limitlesssmp.creatorlink.engine.ConfigIO;
import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.EventProcessor;
import org.limitlesssmp.creatorlink.engine.config.EventConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Loads a profile folder (config/creatorlink/&lt;profile&gt;/) into the engine.
 * Every *.json except constants/notes/stats is an EventConfiguration whose
 * socket type is the file's base name (chat, twitcheventsub, streamlabs, ...).
 */
public final class ProfileManager {
    private final Path rootDir;

    public ProfileManager(Path configRoot) {
        this.rootDir = configRoot.resolve("creatorlink");
    }

    public Path rootDir() {
        return rootDir;
    }

    public Path profileDir(String profile) {
        return rootDir.resolve(profile == null || profile.isEmpty() ? "default" : profile);
    }

    /**
     * One-time migration: copies profile JSONs and notes from an existing CCI
     * install (config/contentcreatorintegration/) if we have no profiles yet.
     */
    public void migrateFromCci(Path configRoot) {
        Path cci = configRoot.resolve("contentcreatorintegration");
        if (Files.exists(rootDir) || !Files.isDirectory(cci)) return;
        try (Stream<Path> dirs = Files.list(cci)) {
            dirs.filter(Files::isDirectory).filter(d -> !d.getFileName().toString().equals("logs")).forEach(profile -> {
                Path target = rootDir.resolve(profile.getFileName().toString());
                try (Stream<Path> files = Files.list(profile)) {
                    Files.createDirectories(target);
                    files.filter(f -> f.getFileName().toString().endsWith(".json")).forEach(f -> {
                        try {
                            Files.copy(f, target.resolve(f.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            EngineRuntime.LOGGER.warn("Could not migrate {}: {}", f, e.toString());
                        }
                    });
                    EngineRuntime.LOGGER.info("Migrated CCI profile '{}' from {}", profile.getFileName(), cci);
                } catch (IOException e) {
                    EngineRuntime.LOGGER.warn("Could not migrate profile {}: {}", profile, e.toString());
                }
            });
        } catch (IOException e) {
            EngineRuntime.LOGGER.warn("CCI migration scan failed: {}", e.toString());
        }
    }

    /**
     * Imports a single CCI/CreatorLink config JSON into {@code profileDir}
     * (the formats are identical). If the file's {@code type} field names a
     * socket, the copy is renamed to {@code <type>.json} so arbitrary
     * filenames still land on the right socket. Returns the stored base name,
     * or null if the file didn't parse.
     */
    public static String importConfigFile(Path file, Path profileDir) {
        String name = file.getFileName().toString();
        if (!name.endsWith(".json")) return null;
        String base = name.replaceFirst("\\.json$", "");
        if (!base.equals("constants") && !base.equals("notes") && !base.equals("stats")) {
            EventConfiguration cfg = ConfigIO.loadEventConfiguration(file);
            if (cfg == null) return null;
            if (cfg.type != null && !cfg.type.isEmpty()) {
                base = cfg.type;
            } else {
                // CCI files carry no "type" field; a shared/re-downloaded copy is
                // often renamed ("streamelements (1).json", "My streamelements.json")
                // and would land on a socket name that never fires — snap it back.
                String cleaned = base.toLowerCase(java.util.Locale.ROOT)
                        .replaceAll("\\s*\\(\\d+\\)$", "").replaceAll("\\s*-\\s*copy$", "").trim();
                for (String known : new String[] {"twitcheventsub", "streamelements", "streamlabs",
                        "donationalerts", "gamehooks", "chat"}) {
                    if (cleaned.equals(known) || cleaned.contains(known)) {
                        base = known;
                        break;
                    }
                }
            }
        }
        try {
            Files.createDirectories(profileDir);
            Files.copy(file, profileDir.resolve(base + ".json"), StandardCopyOption.REPLACE_EXISTING);
            EngineRuntime.LOGGER.info("Imported config '{}' from {}", base, file);
            return base;
        } catch (IOException e) {
            EngineRuntime.LOGGER.warn("Import failed for {}: {}", file, e.toString());
            return null;
        }
    }

    /**
     * Imports every profile folder from a CCI install dir (or any folder of
     * profile subfolders), overwriting same-named files. Unlike
     * {@link #migrateFromCci} this runs on demand, even after first launch.
     * Returns the number of profiles that contributed at least one config.
     */
    public int importCciInstall(Path cci) {
        if (!Files.isDirectory(cci)) return 0;
        int count = 0;
        try (Stream<Path> dirs = Files.list(cci)) {
            for (Path profile : dirs.filter(Files::isDirectory).toList()) {
                String pname = profile.getFileName().toString();
                if (pname.equals("logs")) continue;
                Path target = rootDir.resolve(pname);
                boolean any = false;
                try (Stream<Path> files = Files.list(profile)) {
                    for (Path f : files.filter(f -> f.getFileName().toString().endsWith(".json")).toList()) {
                        if (importConfigFile(f, target) != null) any = true;
                    }
                } catch (IOException e) {
                    EngineRuntime.LOGGER.warn("Could not import profile {}: {}", profile, e.toString());
                }
                if (any) count++;
            }
        } catch (IOException e) {
            EngineRuntime.LOGGER.warn("CCI import scan failed: {}", e.toString());
        }
        return count;
    }

    /** Loads (or reloads) a profile into the runtime + processor. */
    public void loadProfile(String profile, EngineRuntime rt, EventProcessor processor) {
        Path dir = profileDir(profile);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            EngineRuntime.LOGGER.error("Cannot create profile dir {}", dir, e);
            return;
        }
        rt.setProfileDir(dir);
        rt.constants.set(ConfigIO.loadConstants(dir.resolve("constants.json")));
        processor.clear();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(f -> f.getFileName().toString().endsWith(".json")).forEach(f -> {
                String base = f.getFileName().toString().replaceFirst("\\.json$", "");
                if (base.equals("constants") || base.equals("notes") || base.equals("stats") || base.equals("dump")) return;
                EventConfiguration cfg = ConfigIO.loadEventConfiguration(f);
                if (cfg != null) {
                    processor.load(base, cfg);
                    EngineRuntime.LOGGER.info("Loaded event configuration '{}' ({} configurations)", base,
                            cfg.configs == null ? 0 : cfg.configs.size());
                }
            });
        } catch (IOException e) {
            EngineRuntime.LOGGER.error("Failed to scan profile {}", dir, e);
        }
    }
}
