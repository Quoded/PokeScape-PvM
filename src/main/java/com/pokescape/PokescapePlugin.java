/*
 * Copyright (c) 2024, Quo <https://github.com/Quoded>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.pokescape;

import com.pokescape.ui.PokescapeOverlay;
import com.pokescape.ui.PokescapePanel;
import com.pokescape.web.PokescapeClient;
import com.pokescape.util.Utils;
import com.pokescape.util.eventObject;
import com.pokescape.ui.Icon;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.ResizeableChanged;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.Varbits;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.VarClientInt;
import net.runelite.api.NPC;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import net.runelite.client.chat.ChatMessageManager;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PluginDescriptor(
		name = "PokeScape PvM",
		tags = {"pokescape", "pvm", "competition", "pokemon", "pokeball", "runemon", "gym", "battle", "adventure"},
		description = "The companion plugin for PokeScape PvM! Catch 'em all and make your way to the top to defeat the Runemon League!"
)

public class PokescapePlugin extends Plugin {
	private @Inject Client client;
	private @Inject ClientToolbar clientToolbar;
	private @Inject PokescapeConfig config;
	private @Inject PokescapeClient sendRequest;
	private @Inject Utils utils;
	private @Inject OverlayManager overlayManager;
	private @Inject ChatMessageManager chatMessageManager;

	private PokescapePanel panel;
	private PokescapeOverlay overlay;
	private NavigationButton navButton;
	private static GameState currentGameState;

	private static final String POKESCAPE_TITLE = "PokeScape PvM";

	private JsonObject gameEvents;
	private String eventName;
	private JsonArray eventParameters;
	private final List<String> messageCollector = new ArrayList<>();
	private boolean fetchProfile;
	private boolean fetchGameEvent;

	@Override
	protected void startUp() {
		initPanel();
		overlayManager.add(overlay);
		if (config.showPokescapeSidePanel()) clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() {
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		panel = null;
		overlay = null;
	}

	private void initPanel() {
		panel = injector.getInstance(PokescapePanel.class);
		overlay = injector.getInstance(PokescapeOverlay.class);
		final BufferedImage icon = Icon.PANEL_ICON.getImage();
		navButton = NavigationButton.builder()
				.tooltip(POKESCAPE_TITLE)
				.icon(icon)
				.panel(panel)
				.priority(8)
				.build();

		// Ping the server to return and display its status.
		sendRequest.status(panel);
		fetchProfile = true;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		GameState gameState = gameStateChanged.getGameState();
		if (gameState != GameState.LOGGED_IN && gameState != GameState.LOGIN_SCREEN && gameState != GameState.HOPPING) return;

		// Return if the game state updates between loading areas (LOGGED_IN -> LOADING -> LOGGED_IN...etc)
		boolean isNewGameState = gameState != currentGameState;
		if (gameState == GameState.HOPPING) isNewGameState = true;
		if (!isNewGameState) return;
		currentGameState = gameState;

		// Set a flag to send a profile request after player login/hop
		if (gameState == GameState.LOGGED_IN) fetchProfile = true;
	}

	@Subscribe
	public void onGameTick(GameTick tick) {
		// Send a profile request on login/hop
		if (fetchProfile) {
			sendRequest.profile(panel);
			fetchProfile = !fetchProfile;
		}
		// Process events when a matching event is found
		if (fetchGameEvent) {
			utils.processEvent(eventName, eventParameters, messageCollector);
			fetchGameEvent = !fetchGameEvent;
		}

		if (client.getVarcIntValue(VarClientInt.INVENTORY_TAB) != overlay.getCollapsedTabsState()) {
			overlay.recalcOverlay(false);
			overlay.setCollapsedTabsState(client.getVarcIntValue(VarClientInt.INVENTORY_TAB));
		}
		// Clear the message collector at the end of every tick
		messageCollector.clear();
	}

	public void setGameEvents(JsonObject events) {
		gameEvents = events;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged) {
		// Toggle the visibility of the plugin in the side panel
		if (configChanged.getKey().equals("panel_visibility")) {
			clientToolbar.removeNavigation(navButton);
			if (config.showPokescapeSidePanel()) clientToolbar.addNavigation(navButton);
		}
		if (configChanged.getKey().startsWith("overlay")) {
			overlay.recalcOverlay(false);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		if (event.getVarbitId() == Varbits.SIDE_PANELS)
			overlay.recalcOverlay(false);
	}

	@Subscribe
	public void onResizeableChanged(ResizeableChanged event) {
		overlay.recalcOverlay(true);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		if (
			event.getType() != ChatMessageType.GAMEMESSAGE
			&& event.getType() != ChatMessageType.SPAM
			&& event.getType() != ChatMessageType.FRIENDSCHATNOTIFICATION
			&& event.getType() != ChatMessageType.MESBOX
		) return;

		// Log game messages to the message collector
		final String chatMessage = Text.removeTags(event.getMessage());
		messageCollector.add(chatMessage);

		// Find game event messages
		if (gameEvents != null && !fetchGameEvent) {
			List<eventObject> eventMatch = utils.matchEvent(gameEvents, chatMessage, "type", "gameMessage");
			if (!eventMatch.isEmpty()) {
				for (eventObject item : eventMatch) {
					eventName = item.getEventName();
					eventParameters = item.getEventParameters();
				}
				fetchGameEvent = true;
			}
		}

		// Find validation messages
		utils.matchValidation(panel, event, chatMessage);
	}

	@Subscribe
	public void onOverheadTextChanged(OverheadTextChanged event) {
		// Find overhead event messages
		if (gameEvents != null && !fetchGameEvent) {
			List<eventObject> eventMatch = utils.matchEvent(gameEvents, event.getOverheadText(), "type", "overHeadText");
			if (!eventMatch.isEmpty()) {
				for (eventObject item : eventMatch) {
					eventName = item.getEventName();
					eventParameters = item.getEventParameters();
				}
				utils.matchOverhead(event, eventName, eventParameters);
			}
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived npcLootReceived) {
		NPC npc = npcLootReceived.getNpc();
		Collection<ItemStack> items = npcLootReceived.getItems();
		sendRequest.loot("npcLoot", npc.getName(), npc.getId(), items, messageCollector);
	}

	@Subscribe
	public void onLootReceived(LootReceived lootReceived) {
		if (lootReceived.getType() != LootRecordType.EVENT && lootReceived.getType() != LootRecordType.PICKPOCKET) return;
		Collection<ItemStack> items = lootReceived.getItems();
		sendRequest.loot("otherLoot", lootReceived.getName(), 0, items, messageCollector);
	}

	@Provides
	PokescapeConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(PokescapeConfig.class);
	}
}
