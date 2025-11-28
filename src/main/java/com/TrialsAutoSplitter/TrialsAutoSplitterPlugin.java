/*
TrialsAutoSplitterPlugin
Connects to LiveSplit Server and automatically does the splits for the Sailing Trials
Created by Vanik
Credit to molgoatkirby and SkyBouncer's AutoSplitters
Initial date: 27/11/2025
*/

package com.TrialsAutoSplitter;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.PrintWriter;

@Slf4j
@PluginDescriptor(
        name = "Trials AutoSplitter",
        enabledByDefault = false,
        description = "Automatically split for LiveSplit for Sailing Barracuda Trials"
)
public class TrialsAutoSplitterPlugin extends Plugin
{
    // LiveSplit interaction
    PrintWriter writer;

    // Sailing trial split state
    private static final int TOTAL_SPLITS = 10;
    private int currentSplit = 0;
    private int lastTimeStart = 0;
    private int lastCompletedCount = 0;
    private boolean trialRunning = false;

    // The Gwenith Glide Marlin
    private static final int VARP_SAILING_BT_TIME_GWENITH_GLIDE_MARLIN_START = 4987;
    private static final int VARP_SAILING_BT_TRIAL_GWENITH_GLIDE_MARLIN_COMPLETED = 5000;

    // Lost Supplies varbits range (18448–18543 inclusive = 96 total)
    private static final int VARBIT_SAILING_BT_OBJECTIVE_BASE = 18448;
    private static final int LOST_SUPPLIES_TOTAL = 96;

    // Lost Supplies tracking
    private boolean boxesSplit = false;
    private boolean finishSplit = false;
    private boolean lostSuppliesDone = false;
    private boolean sawAnySupplyThisRun = false;

    @Inject
    public Client client;

    @Inject
    private TrialsAutoSplitterConfig config;

    @Inject
    private ClientToolbar clientToolbar;

    // side panel
    private NavigationButton navButton;
    private TrialsAutoSplitterPanel panel;

    @Provides
    TrialsAutoSplitterConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(TrialsAutoSplitterConfig.class);
    }

    @Override
    protected void startUp()
    {
        // Load the icon
        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/inferno_split_icon.png");

        // Create the panel
        panel = new TrialsAutoSplitterPanel(client, writer, config, this);

        // Create the sidebar navigation button
        navButton = NavigationButton.builder()
                .tooltip("Trials Autosplit")
                .icon(icon)
                .priority(6)
                .panel(panel)
                .build();

        // Add the button
        clientToolbar.addNavigation(navButton);

        // Build the panel UI
        panel.startPanel();
    }

    @Override
    protected void shutDown()
    {
        // Remove the sidebar button
        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }

        // Close socket if panel exists
        if (panel != null)
        {
            panel.disconnect();
            panel = null;
        }

        writer = null;
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        int timeStart = client.getVarpValue(VARP_SAILING_BT_TIME_GWENITH_GLIDE_MARLIN_START);
        int completedCount = client.getVarpValue(VARP_SAILING_BT_TRIAL_GWENITH_GLIDE_MARLIN_COMPLETED);

        // Start of trial
        if (!trialRunning && lastTimeStart == 0 && timeStart != 0)
        {
            trialRunning = true;
            currentSplit = 0;

            boxesSplit = false;
            finishSplit = false;
            lostSuppliesDone = false;
            sawAnySupplyThisRun = false;

            lastCompletedCount = completedCount;

            // Fresh run in LiveSplit
            sendMessage("reset");
            sendMessage("starttimer");
        }

        if (trialRunning)
        {
            if (!lostSuppliesDone)
            {
                boolean anyActive = false;

                // Scan all 96 Lost Supplies varbits
                for (int i = 0; i < LOST_SUPPLIES_TOTAL; i++)
                {
                    int id = VARBIT_SAILING_BT_OBJECTIVE_BASE + i;
                    int curr = client.getVarbitValue(id);

                    if (curr == 1)
                    {
                        anyActive = true;
                        sawAnySupplyThisRun = true;
                        break;
                    }
                }

                if (sawAnySupplyThisRun && !anyActive)
                {
                    lostSuppliesDone = true;
                }
            }

            // Lost Supplies split
            if (lostSuppliesDone && !boxesSplit && currentSplit >= 8)
            {
                boxesSplit = true;

                if (currentSplit < TOTAL_SPLITS)
                {
                    currentSplit++;
                    sendMessage("split");
                }
            }

            // Finish split
            if (!finishSplit && completedCount > lastCompletedCount)
            {
                finishSplit = true;
                if (currentSplit < TOTAL_SPLITS)
                {
                    currentSplit++;
                    sendMessage("split");
                }
                lastCompletedCount = completedCount;
            }
        }

        // ----- End/reset of trial -----
        if (trialRunning && lastTimeStart != 0 && timeStart == 0)
        {
            trialRunning = false;
            currentSplit = 0;
            boxesSplit = false;
            finishSplit = false;
            lostSuppliesDone = false;
            sawAnySupplyThisRun = false;
        }

        lastTimeStart = timeStart;
        lastCompletedCount = completedCount;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        // Only care about normal game messages
        if (event.getType() != ChatMessageType.GAMEMESSAGE)
        {
            return;
        }

        // Strip color tags etc.
        String message = Text.removeTags(event.getMessage());

        // We only care about the Sailing crystal imbue messages
        if (!message.startsWith("You imbue the Crystal of "))
        {
            return;
        }

        // Only split if we think the trial is running
        if (!trialRunning)
        {
            return;
        }

        // Extract the "(x/8)" part
        int openIdx = message.indexOf('(');
        int slashIdx = message.indexOf('/', openIdx);
        int closeIdx = message.indexOf(')', slashIdx);

        if (openIdx == -1 || slashIdx == -1 || closeIdx == -1)
        {
            return; // Unexpected format
        }

        int imbueNumber;
        try
        {
            String numStr = message.substring(openIdx + 1, slashIdx); // x in (x/8)
            imbueNumber = Integer.parseInt(numStr);
        }
        catch (NumberFormatException e)
        {
            return;
        }

        // Split on crystals 1–8
        if (imbueNumber >= 1 && imbueNumber <= 8 && imbueNumber > currentSplit)
        {
            currentSplit = imbueNumber;
            sendMessage("split");
        }
    }

    private void sendMessage(String message)
    {
        if (writer != null)
        {
            writer.write(message + "\r\n");
            writer.flush();
        }
    }
}
