package org.limitlesssmp.creatorlink.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tick-driven scheduling: delayed outcome triggers and play-time queues.
 * {@link #tick()} must be called once per game tick on the game thread.
 */
public final class Scheduler {
    /** Ticks a queued config event's playTimeEvent: accept(currentPlayTime, totalPlayTime). */
    public interface PlayTicker {
        void playTick(int currentPlayTime, int totalPlayTime);
    }

    public static final class QueueEntry {
        final Runnable fire;
        final PlayTicker playTicker; // nullable
        final int playTicks;

        public QueueEntry(Runnable fire, PlayTicker playTicker, int playTicks) {
            this.fire = fire;
            this.playTicker = playTicker;
            this.playTicks = playTicks;
        }
    }

    private static final class Task {
        final long at;
        final Runnable run;

        Task(long at, Runnable run) {
            this.at = at;
            this.run = run;
        }
    }

    private static final class PlayQueue {
        final Deque<QueueEntry> pending = new ArrayDeque<>();
        QueueEntry current;
        int elapsed;
    }

    private long tick;
    private final List<Task> tasks = new ArrayList<>();
    private final Map<String, PlayQueue> queues = new LinkedHashMap<>();

    public synchronized long now() {
        return tick;
    }

    public synchronized void schedule(int delayTicks, Runnable run) {
        tasks.add(new Task(tick + Math.max(delayTicks, 0), run));
    }

    public synchronized void enqueue(String queueName, QueueEntry entry) {
        queues.computeIfAbsent(queueName, k -> new PlayQueue()).pending.add(entry);
    }

    public synchronized void clearQueues() {
        for (PlayQueue q : queues.values()) {
            q.pending.clear();
            q.current = null;
            q.elapsed = 0;
        }
    }

    public void tick() {
        List<Runnable> due = new ArrayList<>();
        synchronized (this) {
            tick++;
            Iterator<Task> it = tasks.iterator();
            while (it.hasNext()) {
                Task t = it.next();
                if (tick >= t.at) {
                    due.add(t.run);
                    it.remove();
                }
            }
            for (PlayQueue q : queues.values()) {
                if (q.current != null) {
                    q.elapsed++;
                    QueueEntry cur = q.current;
                    int elapsed = q.elapsed;
                    if (cur.playTicker != null) {
                        due.add(() -> cur.playTicker.playTick(elapsed, cur.playTicks));
                    }
                    if (q.elapsed >= cur.playTicks) {
                        q.current = null;
                        q.elapsed = 0;
                    }
                }
                if (q.current == null && !q.pending.isEmpty()) {
                    QueueEntry next = q.pending.poll();
                    due.add(next.fire);
                    if (next.playTicks > 0) {
                        q.current = next;
                        q.elapsed = 0;
                    }
                }
            }
        }
        for (Runnable r : due) {
            try {
                r.run();
            } catch (Exception e) {
                EngineRuntime.LOGGER.error("Scheduled task failed", e);
            }
        }
    }
}
