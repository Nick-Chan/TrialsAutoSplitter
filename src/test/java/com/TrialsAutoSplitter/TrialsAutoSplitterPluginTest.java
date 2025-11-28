package com.TrialsAutoSplitter;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TrialsAutoSplitterPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TrialsAutoSplitterPlugin.class);
		RuneLite.main(args);
	}
}