package org.limitlesssmp.creatorlink.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Shared state for event processing: global variables, notes, constants,
 * the scheduler, the game bridge, and the event cache.
 */
public final class EngineRuntime {
    public static final Logger LOGGER = LoggerFactory.getLogger("creatorlink");

    /** Re-dispatches a synthetic event into a socket's event configuration (cciSendEventToSocket). */
    public interface EventDispatcher {
        void dispatch(String socketType, String platform, String eventType, Map<String, Object> vars);
    }

    /** Switches the active profile (cciChangeProfile). */
    public interface ProfileChanger {
        void accept(String profile, boolean disableToast);
    }

    public volatile EventDispatcher dispatcher;
    public volatile ProfileChanger profileChanger;

    public final NoteStore notes = new NoteStore();
    public final ConstantsStore constants = new ConstantsStore();
    public final Scheduler scheduler = new Scheduler();
    public final Random random = new Random();

    private final Map<String, Object> globals = new LinkedHashMap<>();
    private final Deque<EventContext> eventCache = new ArrayDeque<>();
    private volatile GameBridge bridge = GameBridge.NOOP;
    private volatile Path profileDir;
    public volatile int maxEventCache = 20;

    public GameBridge bridge() {
        return bridge;
    }

    public void setBridge(GameBridge bridge) {
        this.bridge = bridge == null ? GameBridge.NOOP : bridge;
    }

    public Path profileDir() {
        return profileDir;
    }

    public void setProfileDir(Path dir) {
        this.profileDir = dir;
        notes.load(dir);
    }

    /** Sets up the documented startup globals: streamer, playerName, mcVersion. */
    public void initGlobals(String streamerName) {
        String player = bridge.playerName();
        setGlobal("streamer", streamerName == null || streamerName.isEmpty() ? player : streamerName);
        setGlobal("playerName", player);
        setGlobal("mcVersion", bridge.mcVersion());
    }

    public synchronized void setGlobal(String name, Object value) {
        globals.put(name, value);
    }

    public synchronized Object getGlobal(String name) {
        return globals.get(name);
    }

    public synchronized Object removeGlobal(String name) {
        return globals.remove(name);
    }

    public synchronized boolean hasGlobal(String name) {
        return globals.containsKey(name);
    }

    public synchronized Map<String, Object> globalsSnapshot() {
        return new LinkedHashMap<>(globals);
    }

    /** Creates a context with globals pre-inserted, then event variables on top. */
    public EventContext newContext(String socketType, String platform, String eventType, Map<String, Object> eventVars) {
        EventContext ctx = new EventContext(socketType, platform, eventType);
        ctx.setAll(globalsSnapshot());
        if (eventVars != null) ctx.setAll(eventVars);
        ctx.set("cci-type-socket", socketType);
        if (platform != null) ctx.set("cci-type-platform", platform);
        if (eventType != null) ctx.set("cci-type-event", eventType);
        return ctx;
    }

    /** Re-inserts globals (done just before queued/delayed events fire). */
    public void refreshGlobals(EventContext ctx) {
        ctx.setAll(globalsSnapshot());
    }

    // ---- local event statistics (stats.json in the profile folder) ----
    private final Map<String, Long> eventStats = new LinkedHashMap<>();
    private volatile long lastStatsSave;

    public void bumpStat(String key) {
        synchronized (eventStats) {
            eventStats.merge(key, 1L, Long::sum);
        }
        long now = System.currentTimeMillis();
        if (now - lastStatsSave > 15_000) {
            lastStatsSave = now;
            saveStats();
        }
    }

    private void saveStats() {
        Path dir = profileDir;
        if (dir == null) return;
        try {
            Map<String, Long> snapshot;
            synchronized (eventStats) {
                snapshot = new LinkedHashMap<>(eventStats);
            }
            java.nio.file.Files.writeString(dir.resolve("stats.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(snapshot));
        } catch (Exception e) {
            LOGGER.warn("Failed to save stats.json: {}", e.toString());
        }
    }

    public synchronized void cacheEvent(EventContext ctx) {
        eventCache.addFirst(ctx);
        while (eventCache.size() > Math.max(maxEventCache, 0)) {
            eventCache.removeLast();
        }
    }

    public synchronized List<EventContext> cachedEvents() {
        return List.copyOf(eventCache);
    }

    public Map<String, Object> globalsView() {
        return Collections.unmodifiableMap(globals);
    }
}
