package com.TrialsAutoSplitter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("autosplitter")
public interface TrialsAutoSplitterConfig extends Config {

    @ConfigItem(position = 2, keyName = "port", name = "Port", description = "Port for the LiveSplit server. (Restart required)")
    default int port() {
        return 16834;
    }

}
