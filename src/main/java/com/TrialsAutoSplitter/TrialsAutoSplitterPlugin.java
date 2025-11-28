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

// debug
import net.runelite.api.events.VarbitChanged;

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

    // Sailing trial config
    private int currentSplit = 0;
    private int lastTimeStart = 0;
    private int lastCompletedCount = 0;
    private boolean trialRunning = false;
    private int totalSplitsThisRun = 0;
    private int lostSuppliesTotalThisRun = 0;
    private int lastCrystalSplit = 8;
    private int trialTypeThisRun = 0;

    // The Gwenith Glide
    private static final int VARBIT_SAILING_BT_IN_TRIAL_GWENITH_GLIDE = 18410;
    private static final int VARP_SAILING_BT_TIME_GWENITH_GLIDE_START = 4987;
    private static final int VARP_SAILING_BT_TRIAL_GWENITH_GLIDE_COMPLETED = 5000;
    private static final int VARBIT_SAILING_BT_OBJECTIVE_BASE = 18448;

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
        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/trial_split_icon.png");

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
        int timeStart = client.getVarpValue(VARP_SAILING_BT_TIME_GWENITH_GLIDE_START);
        int completedCount = client.getVarpValue(VARP_SAILING_BT_TRIAL_GWENITH_GLIDE_COMPLETED);
        int trialTypeVar = client.getVarbitValue(VARBIT_SAILING_BT_IN_TRIAL_GWENITH_GLIDE);

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
            trialTypeThisRun = trialTypeVar;

            switch (trialTypeThisRun) {
                case 2: // Swordfish
                    totalSplitsThisRun = 5;
                    lostSuppliesTotalThisRun = 30;
                    lastCrystalSplit = 3;
                    break;
                case 3: // Shark
                    totalSplitsThisRun = 7;
                    lostSuppliesTotalThisRun = 60;
                    lastCrystalSplit = 5;
                    break;
                case 4: // Marlin
                    totalSplitsThisRun = 10;
                    lostSuppliesTotalThisRun = 96;
                    lastCrystalSplit = 8;
                    break;
                default:
                    totalSplitsThisRun = 0;
                    lostSuppliesTotalThisRun = 0;
                    lastCrystalSplit = Integer.MAX_VALUE;
                    break;
            }

            // Fresh run in LiveSplit
            sendMessage("reset");
            sendMessage("starttimer");
        }

        if (trialRunning)
        {
            if (!lostSuppliesDone)
            {
                boolean anyActive = false;

                // Scan all Lost Supplies varbits
                for (int i = 0; i < lostSuppliesTotalThisRun; i++)
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
            if (lostSuppliesDone && !boxesSplit && currentSplit >= lastCrystalSplit)
            {
                boxesSplit = true;
                if (currentSplit < totalSplitsThisRun)
                {
                    currentSplit++;
                    sendMessage("split");

                    client.addChatMessage(
                            ChatMessageType.GAMEMESSAGE,
                            "",
                            "Lost Supplies Split",
                            null
                    );
                }
            }

            // Finish split
            if (!finishSplit && completedCount > lastCompletedCount)
            {
                finishSplit = true;
                if (currentSplit < totalSplitsThisRun)
                {
                    currentSplit++;
                    sendMessage("split");

                    client.addChatMessage(
                            ChatMessageType.GAMEMESSAGE,
                            "",
                            "Finish split",
                            null
                    );
                }
                lastCompletedCount = completedCount;
            }
        }

        // End/reset of trial
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
    public void onVarbitChanged(VarbitChanged event)
    {
        int id = event.getVarbitId();
        int value = event.getValue();
/*
        client.addChatMessage(
                ChatMessageType.GAMEMESSAGE,
                "",
                "Varbit changed during trial: id=" + id + " value=" + value,
                null
        );*/
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

        // Extract the "(x/x)" part
        int openIdx = message.indexOf('(');
        int slashIdx = message.indexOf('/', openIdx);
        int closeIdx = message.indexOf(')', slashIdx);

        if (openIdx == -1 || slashIdx == -1 || closeIdx == -1)
        {
            return;
        }

        int imbueNumber;
        try
        {
            String numStr = message.substring(openIdx + 1, slashIdx);
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
