package org.limitlesssmp.creatorlink.engine;

import org.limitlesssmp.creatorlink.engine.config.ConfigEvent;
import org.limitlesssmp.creatorlink.engine.config.Configuration;
import org.limitlesssmp.creatorlink.engine.config.EventConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The documented pipeline:
 * Stream Event → Event → EventConfiguration → Configuration → Config Event → Conditions & Outcomes.
 * Top-to-bottom testing with short circuiting unless allowsOtherEventsToTrigger.
 */
public final class EventProcessor implements EngineRuntime.EventDispatcher {
    private final EngineRuntime rt;
    private final Map<String, EventConfiguration> configs = new LinkedHashMap<>();

    public EventProcessor(EngineRuntime rt) {
        this.rt = rt;
        rt.dispatcher = this;
    }

    public synchronized void clear() {
        configs.clear();
    }

    public synchronized void load(String socketType, EventConfiguration config) {
        if (config == null) return;
        configs.put(socketType, config);
        runInit(config);
    }

    public synchronized EventConfiguration get(String socketType) {
        return configs.get(socketType);
    }

    /** init events run on load with globals only; allowsOtherEventsToTrigger is ignored. */
    private void runInit(EventConfiguration config) {
        if (config.init == null) return;
        for (ConfigEvent e : config.init) {
            if (e == null || e.disabled) continue;
            EventContext ctx = rt.newContext("init", null, "init", null);
            ConfigEvent eff = e.effective(rt, ctx);
            if (eff != null && eff.testConditions(rt, ctx)) {
                eff.triggerOutcomes(rt, ctx);
            }
        }
    }

    @Override
    public void dispatch(String socketType, String platform, String eventType, Map<String, Object> vars) {
        handle(socketType, platform, eventType, vars);
    }

    /** Returns how many config events triggered (0 = nothing matched or all conditions failed). */
    public int handle(String socketType, String platform, String eventType, Map<String, Object> eventVars) {
        EventConfiguration config;
        synchronized (this) {
            config = configs.get(socketType);
        }
        if (config == null || config.configs == null) return 0;

        EventContext ctx = rt.newContext(socketType, platform, eventType, eventVars);

        // Platform filter: first Configuration whose "for" is null or matches wins.
        Configuration matched = null;
        for (Configuration c : config.configs) {
            if (c == null) continue;
            if (c.forPlatform == null || c.forPlatform.equalsIgnoreCase(String.valueOf(platform))) {
                matched = c;
                break;
            }
        }
        if (matched == null) return 0;

        if (matched.ignoredTypes != null && matched.ignoredTypes.contains(eventType)) return 0;
        rt.cacheEvent(ctx);
        rt.bumpStat(socketType + "/" + eventType);

        // "all" events are tested first, in addition to the event type's own.
        List<ConfigEvent> events = new ArrayList<>();
        List<ConfigEvent> all = matched.types == null ? null : matched.types.get("all");
        if (all != null) events.addAll(all);
        List<ConfigEvent> typed = matched.types == null ? null : matched.types.get(eventType);
        if (typed != null) events.addAll(typed);

        int triggered = 0;
        for (ConfigEvent event : events) {
            if (event == null || event.disabled) continue;
            ConfigEvent effective = event.effective(rt, ctx);
            if (effective == null || !effective.valid()) continue;
            if (!effective.testConditions(rt, ctx)) continue;

            long now = rt.scheduler.now();
            int cooldownTicks = Vars.toTicks(event.cooldown, ctx, 0);
            if (cooldownTicks > 0 && now < event.cooldownUntilTick) {
                // On cooldown after passing: short circuits unless disabled or the event allows others.
                if (!event.allowsOtherEventsToTrigger && !event.disableShortCircuitWhenOnCooldown) break;
                continue;
            }
            if (cooldownTicks > 0) {
                event.cooldownUntilTick = now + cooldownTicks;
            }

            dispatchTrigger(matched, event, effective, ctx);
            triggered++;

            if (!event.allowsOtherEventsToTrigger) break;
        }
        return triggered;
    }

    /** Applies delay, then queue membership (playTime >= 0), then fires outcomes. */
    private void dispatchTrigger(Configuration cfg, ConfigEvent outer, ConfigEvent effective, EventContext ctx) {
        EventContext snapshot = ctx.copy();
        int playTicks = Vars.toTicks(outer.playTime, ctx, -1);
        String queueName = cfg.queueFor(ctx.eventType);
        int delayTicks = Vars.toTicks(outer.delay, ctx, 0);

        Runnable fire = () -> {
            rt.refreshGlobals(snapshot);
            effective.triggerOutcomes(rt, snapshot);
        };

        if (queueName != null && playTicks >= 0) {
            Scheduler.PlayTicker ticker = null;
            ConfigEvent playEvent = outer.playTimeEvent != null ? outer.playTimeEvent : effective.playTimeEvent;
            if (playEvent != null) {
                ticker = (current, total) -> {
                    EventContext tctx = snapshot.copy();
                    tctx.set("currentPlayTime", (long) current);
                    tctx.set("totalPlayTime", (long) total);
                    ConfigEvent eff = playEvent.effective(rt, tctx);
                    if (eff != null && eff.testConditions(rt, tctx)) {
                        eff.triggerOutcomes(rt, tctx);
                    }
                };
            }
            Scheduler.QueueEntry entry = new Scheduler.QueueEntry(fire, ticker, playTicks);
            if (delayTicks > 0) {
                rt.scheduler.schedule(delayTicks, () -> rt.scheduler.enqueue(queueName, entry));
            } else {
                rt.scheduler.enqueue(queueName, entry);
            }
        } else if (delayTicks > 0) {
            rt.scheduler.schedule(delayTicks, fire);
        } else {
            fire.run();
        }
    }
}
