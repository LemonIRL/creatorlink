package org.limitlesssmp.creatorlink.engine.config;

import java.util.List;

/**
 * Root of a socket's JSON config file (e.g. twitcheventsub.json, chat.json).
 */
public final class EventConfiguration {
    public List<Configuration> configs;
    public List<ConfigEvent> init;
    public String type;
    public String from;
}
