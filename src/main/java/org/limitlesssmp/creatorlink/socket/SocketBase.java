package org.limitlesssmp.creatorlink.socket;

import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.EventProcessor;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for all stream-service connections. Sockets run on background
 * threads; events hop to the game thread through the engine scheduler.
 */
public abstract class SocketBase {
    public enum Status { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    protected static final ScheduledExecutorService EXECUTOR =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "creatorlink-socket");
                t.setDaemon(true);
                return t;
            });

    /** Raw socket_event logging (logTypes = ["socket_event"]) into config/creatorlink/logs/. */
    private static volatile boolean logSocketEvents;
    private static volatile java.nio.file.Path logDir;

    public static void configureEventLogging(boolean enabled, java.nio.file.Path dir) {
        logSocketEvents = enabled;
        logDir = dir;
    }

    protected final void logEvent(String raw) {
        if (!logSocketEvents || logDir == null) return;
        try {
            java.nio.file.Files.createDirectories(logDir);
            String file = java.time.LocalDate.now() + ".log";
            String line = "Event " + name() + ": " + raw + System.lineSeparator();
            synchronized (SocketBase.class) {
                java.nio.file.Files.writeString(logDir.resolve(file), line,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            }
        } catch (Exception ignored) {}
    }

    protected final EngineRuntime rt;
    protected final EventProcessor processor;
    private final String name;
    private volatile Status status = Status.DISCONNECTED;
    private volatile String statusDetail = "";
    protected volatile int maxReconnects = 3;
    private volatile int reconnectAttempts;
    private volatile boolean closed;
    private volatile boolean everConnected;
    /**
     * Bumped on every start/stop/reconnect. A transport captures the value it
     * was opened under and hands it back with its callbacks; anything from an
     * older attempt is ignored, so a superseded connection can never toast,
     * reconnect, or (via the subclass) dispatch events on top of the live one.
     */
    private final AtomicInteger attempt = new AtomicInteger();

    protected SocketBase(String name, EngineRuntime rt, EventProcessor processor) {
        this.name = name;
        this.rt = rt;
        this.processor = processor;
    }

    public String name() {
        return name;
    }

    public Status status() {
        return status;
    }

    public String statusDetail() {
        return statusDetail;
    }

    public final void start() {
        closed = false;
        reconnectAttempts = 0;
        connectAsync(0);
    }

    public final void stop() {
        closed = true;
        attempt.incrementAndGet(); // orphan any connect still in flight
        setStatus(Status.DISCONNECTED, "stopped");
        try {
            doDisconnect();
        } catch (Exception ignored) {}
    }

    public final void restart() {
        stop();
        start();
    }

    protected final boolean isClosed() {
        return closed;
    }

    /** True while {@code attempt} is the connection we currently want. */
    protected final boolean isCurrent(int attempt) {
        return !closed && attempt == this.attempt.get();
    }

    private void connectAsync(long delaySeconds) {
        int mine = attempt.incrementAndGet();
        setStatus(Status.CONNECTING, "");
        EXECUTOR.schedule(() -> {
            if (!isCurrent(mine)) return;
            try {
                doConnect(mine);
            } catch (Exception e) {
                onError(mine, e);
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    /**
     * Claims {@code attempt} for a lifecycle transition. Only the first
     * caller wins; the losers are stale callbacks from a transport we already
     * moved past (or a second callback from the same failure) and must bail.
     */
    private boolean claim(int attempt) {
        return !closed && this.attempt.compareAndSet(attempt, attempt + 1);
    }

    /** Best-effort teardown of the current transport before opening another. */
    private void teardown() {
        try {
            doDisconnect();
        } catch (Exception ignored) {}
    }

    /** Attempt-aware disconnect: ignores stale transports, closes ours before reconnecting. */
    protected final void onDisconnected(int attempt, String reason) {
        if (!claim(attempt)) return;
        teardown();
        onDisconnected(reason);
    }

    /** Attempt-aware error: ignores stale transports, closes ours before reconnecting. */
    protected final void onError(int attempt, Throwable t) {
        if (!claim(attempt)) return;
        teardown();
        onError(t);
    }

    /**
     * Attempt-aware connected. Returns false when this connect was superseded
     * (stopped or restarted while it was still opening) — the caller must
     * close the transport it just opened rather than announce it.
     */
    protected final boolean onConnected(int attempt) {
        if (!isCurrent(attempt)) return false;
        onConnected();
        return true;
    }

    /** Called by transports when the connection dies; applies reconnect backoff. */
    protected final void onDisconnected(String reason) {
        if (closed) return;
        boolean wasConnected = status == Status.CONNECTED;
        setStatus(Status.DISCONNECTED, reason);
        if (wasConnected) {
            rt.bridge().toast(3, "CreatorLink", name + " disconnected"
                    + (reason == null || reason.isEmpty() ? "" : " (" + reason + ")"), null, "red");
        }
        scheduleReconnect();
    }

    protected final void onError(Throwable t) {
        if (closed) return;
        boolean wasConnected = status == Status.CONNECTED;
        EngineRuntime.LOGGER.warn("[{}] error: {}", name, t.toString());
        setStatus(Status.ERROR, t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
        if (wasConnected) {
            rt.bridge().toast(3, "CreatorLink", name + " disconnected (" + statusDetail + ")", null, "red");
        }
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (closed) return;
        if (reconnectAttempts >= maxReconnects) {
            EngineRuntime.LOGGER.warn("[{}] giving up after {} reconnect attempts", name, reconnectAttempts);
            rt.bridge().toast(3, "CreatorLink", name + " couldn't reconnect after " + reconnectAttempts
                    + " attempts — run /creatorlink reconnect", null, "red");
            return;
        }
        reconnectAttempts++;
        long delay = Math.min(5L * reconnectAttempts, 30);
        EngineRuntime.LOGGER.info("[{}] reconnecting in {}s (attempt {}/{})", name, delay, reconnectAttempts, maxReconnects);
        rt.bridge().toast(3, "CreatorLink", name + " reconnecting in " + delay + "s (attempt "
                + reconnectAttempts + "/" + maxReconnects + ")", null, "yellow");
        connectAsync(delay);
    }

    protected final void onConnected() {
        boolean reconnected = everConnected;
        everConnected = true;
        reconnectAttempts = 0;
        setStatus(Status.CONNECTED, "");
        rt.bridge().toast(3, "CreatorLink", name + (reconnected ? " reconnected" : " connected"), null, "green");
    }

    protected void setStatus(Status s, String detail) {
        this.status = s;
        this.statusDetail = detail == null ? "" : detail;
        EngineRuntime.LOGGER.info("[{}] {} {}", name, s, statusDetail);
    }

    /** Dispatches an event into the pipeline on the next game tick. */
    protected final void dispatch(String socketType, String platform, String eventType, Map<String, Object> vars) {
        rt.scheduler.schedule(0, () -> processor.handle(socketType, platform, eventType, vars));
    }

    /**
     * Opens the transport. Implementations should capture {@code attempt} and
     * route their callbacks through {@link #onConnected(int)},
     * {@link #onDisconnected(int, String)} and {@link #onError(int, Throwable)}
     * so a connection that has been replaced can't act on the socket's behalf.
     * The plain overloads remain for transports that police their own lifecycle.
     */
    protected abstract void doConnect(int attempt) throws Exception;

    protected abstract void doDisconnect() throws Exception;
}
