package org.limitlesssmp.creatorlink.socket;

import org.limitlesssmp.creatorlink.config.ClientConfig;
import org.limitlesssmp.creatorlink.engine.EngineRuntime;
import org.limitlesssmp.creatorlink.engine.EventProcessor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Builds and owns all sockets configured in creatorlink.toml. */
public final class SocketManager {
    private final EngineRuntime rt;
    private final EventProcessor processor;
    private final List<SocketBase> sockets = new ArrayList<>();

    public SocketManager(EngineRuntime rt, EventProcessor processor) {
        this.rt = rt;
        this.processor = processor;
    }

    public synchronized void start(ClientConfig cfg, Path configFile) {
        stop();
        SocketBase.configureEventLogging(cfg.logTypes != null && cfg.logTypes.contains("socket_event"),
                configFile.getParent().resolve("logs"));

        if (!cfg.twitchChats.isEmpty()) {
            sockets.add(new TwitchChatSocket(rt, processor, cfg.twitchChats));
        }
        for (int i = 0; i < cfg.eventsubOauthTokens.size(); i++) {
            String access = cfg.eventsubOauthTokens.get(i);
            if (access == null || access.isEmpty()) continue;
            String refresh = i < cfg.eventsubRefreshTokens.size() ? cfg.eventsubRefreshTokens.get(i) : null;
            String clientId = i < cfg.eventsubClientIDs.size() ? cfg.eventsubClientIDs.get(i) : null;
            int index = i;
            sockets.add(new TwitchEventSubSocket(rt, processor, access, refresh, clientId, (newAccess, newRefresh) -> {
                // persist refreshed tokens so restarts keep working
                if (index < cfg.eventsubOauthTokens.size()) cfg.eventsubOauthTokens.set(index, newAccess);
                if (index < cfg.eventsubRefreshTokens.size() && newRefresh != null) {
                    cfg.eventsubRefreshTokens.set(index, newRefresh);
                }
                cfg.save(configFile);
            }));
        }
        for (String token : cfg.streamlabsTokens) {
            if (!token.isEmpty()) sockets.add(new StreamlabsSocket(rt, processor, token));
        }
        for (String token : cfg.streamElementsTokens) {
            if (!token.isEmpty()) sockets.add(new StreamElementsSocket(rt, processor, token));
        }
        for (String token : cfg.donationAlertsTokens) {
            if (!token.isEmpty()) sockets.add(new DonationAlertsSocket(rt, processor, token));
        }
        String apiKey = cfg.youtubeApiKeys.isEmpty() ? null : cfg.youtubeApiKeys.get(0);
        if (apiKey != null && !apiKey.isEmpty()) {
            for (String channel : cfg.youtubeChannelIds) {
                if (!channel.isEmpty()) sockets.add(new YouTubeChatSocket(rt, processor, apiKey, channel));
            }
        }

        for (SocketBase s : sockets) {
            s.maxReconnects = Math.max(cfg.maxAutomaticReconnects, 0);
            s.start();
        }
        EngineRuntime.LOGGER.info("Started {} socket(s)", sockets.size());
    }

    public synchronized void stop() {
        for (SocketBase s : sockets) s.stop();
        sockets.clear();
    }

    public synchronized void restartAll() {
        for (SocketBase s : sockets) s.restart();
    }

    public synchronized List<SocketBase> sockets() {
        return List.copyOf(sockets);
    }
}
