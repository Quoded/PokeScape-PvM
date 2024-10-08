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

import com.google.gson.JsonElement;
import com.pokescape.ui.PokescapeOverlay;
import com.pokescape.ui.PokescapePanel;
import com.pokescape.web.PokescapeClient;
import com.pokescape.util.Utils;
import com.pokescape.util.eventObject;
import com.pokescape.util.PokeScapeGoals;
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
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.Varbits;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.VarClientInt;
import net.runelite.api.NPC;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
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
	private PokeScapeGoals goals;
	private NavigationButton navButton;
	private static GameState currentGameState;

	private static final String POKESCAPE_TITLE = "PokeScape PvM";

	private JsonObject gameEvents;
	private JsonObject gameActivities;
	private JsonObject allowBlockList;
	private String eventName;
	private String eventType;
	private JsonArray eventParameters;
	private int eventWidget;
	private final JsonObject containerEvents = new JsonObject();
	private JsonObject recentActivities = new JsonObject();
	private final List<String> messageCollector = new ArrayList<>();
	private boolean fetchProfile;
	private boolean fetchGameEvent;
	private int delayedGameEventTick;
	private int delayedMsgCleanupTick;
	private int delayedSubmitTick;
	private int delayDupeWidget;
	private final ArrayList<Object> lootObject = new ArrayList <>();

	@Override
	protected void startUp() {
		goals = injector.getInstance(PokeScapeGoals.class);
		goals.startUp();
		initPanel();
		overlayManager.add(overlay);
		if (config.showPokescapeSidePanel()) clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() {
		sendRequest.shutdownSSE();
		goals.shutDown();
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		goals = null;
		panel = null;
		overlay = null;
	}

	public void setGameEvents(JsonObject events) { gameEvents = events; setContainerEvents(events); }
	public void setAllowBlockList(JsonObject allowblock) {
		allowBlockList = allowblock;
	}
	public void setGameActivities(JsonObject activities) { gameActivities = activities; goals.setPlayerState(); }
	public JsonObject getGameActivities() {
		return gameActivities;
	}
	public JsonObject getPlayerState() {
		return recentActivities;
	}
	public void setPlayerState(JsonObject playerState) { recentActivities = playerState; }
	public List<String> getMessageCollector() {
		return messageCollector;
	}
	public void setDelayDupeWidget(int value) { delayDupeWidget = value; }

	public JsonObject getContainerEvents() {
		return containerEvents;
	}
	public void setContainerEvents(JsonObject events) {
		events.keySet().forEach(keyName -> {
			JsonObject keyObj = events.get(keyName).getAsJsonObject();
			String filterKeyValue = keyObj.get("type").getAsString();
			if (filterKeyValue.equals("containerUpdate")) {
				JsonArray itemIDs = (keyObj.has("items") && !keyObj.get("items").isJsonNull()) ? keyObj.get("items").getAsJsonArray() : new JsonArray();
				JsonArray eventParameters = (keyObj.has("param") && !keyObj.get("param").isJsonNull()) ? keyObj.get("param").getAsJsonArray() : new JsonArray();
				JsonObject eventObject = new JsonObject();
				eventObject.add("items", itemIDs);
				eventObject.add("param", eventParameters);
				containerEvents.add(keyName, eventObject);
			}
		});
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
		if (gameState != GameState.LOGGED_IN && gameState != GameState.LOGIN_SCREEN && gameState != GameState.HOPPING && gameState != GameState.LOGGING_IN) return;

		// Return if the game state updates between loading areas (LOGGED_IN -> LOADING -> LOGGED_IN...etc)
		boolean isNewGameState = gameState != currentGameState;
		if (gameState == GameState.HOPPING) isNewGameState = true;
		if (!isNewGameState) return;

		// Connect to SSE if logging in from the title screen
		if (currentGameState == GameState.LOGGING_IN && gameState == GameState.LOGGED_IN) sendRequest.initSSE();

		// Set the current gameState
		currentGameState = gameState;

		// Disconnect from SSE on logout to title screen
		if (gameState == GameState.LOGIN_SCREEN) sendRequest.shutdownSSE();

		// Set a flag to send a profile request after player login/hop
		if (gameState == GameState.LOGGED_IN) fetchProfile = true;
	}

	@Subscribe
	public void onGameTick(GameTick tick) {
		// Send a profile request on login/hop
		if (fetchProfile) {
			sendRequest.profile(panel, goals);
			fetchProfile = !fetchProfile;
		}
		// Process events when a matching event is found
		if (fetchGameEvent && --delayedGameEventTick < 0) {
			int spriteID = fetchWidgetSprite();
			utils.processEvent(eventName, eventType, eventParameters, eventWidget, messageCollector, recentActivities, spriteID);
			fetchGameEvent = !fetchGameEvent;
		}

		if (client.getVarcIntValue(VarClientInt.INVENTORY_TAB) != overlay.getCollapsedTabsState()) {
			overlay.recalcOverlay(false);
			overlay.setCollapsedTabsState(client.getVarcIntValue(VarClientInt.INVENTORY_TAB));
		}

		// Handles submitting loot set with a delayed submission
		if (!lootObject.isEmpty() && --delayedSubmitTick < 0) {
			sendRequest.loot((String) lootObject.get(0), (String) lootObject.get(1), (Integer) lootObject.get(2), (Collection<ItemStack>) lootObject.get(3), messageCollector, recentActivities);
			lootObject.clear();
		}

		// Clears the message collector at the end of every tick
		// delayedLootTick can be set to a postive value to log messages for a longer period
		if (--delayedMsgCleanupTick < 0) {
			messageCollector.clear();
		}

		// Filters out duplicate widget events when positive
		--delayDupeWidget;
	}

	private int fetchWidgetSprite() {
		Widget sprite = client.getWidget(ComponentID.DIALOG_SPRITE_SPRITE);
		if (sprite != null) return sprite.getItemId();
		return -1;
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
		if (event.getVarbitId() == Varbits.SIDE_PANELS) overlay.recalcOverlay(false);
	}

	@Subscribe
	public void onResizeableChanged(ResizeableChanged event) {
		overlay.recalcOverlay(true);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		// Matches menu actions to a key-value list to determine the player's most recently performed activities
		String lastMenuOption = Text.removeTags(event.getMenuOption());
		String lastMenuTarget = Text.removeTags(event.getMenuTarget());
		if (gameActivities != null) recentActivities = utils.matchActivity(recentActivities, gameActivities, lastMenuOption, lastMenuTarget, -1);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		if (
			event.getType() != ChatMessageType.GAMEMESSAGE
			&& event.getType() != ChatMessageType.SPAM
			&& event.getType() != ChatMessageType.FRIENDSCHATNOTIFICATION
			&& event.getType() != ChatMessageType.MESBOX
			&& event.getType() != ChatMessageType.DIALOG
		) return;

		// Log game messages to the message collector
		final String chatMessage = Text.removeTags(event.getMessage());
		messageCollector.add(chatMessage);

		// Find game event messages
		if (gameEvents != null && !fetchGameEvent) {
			List<eventObject> eventMatch = utils.matchEvent(gameEvents, chatMessage, "gameEvent", "type", "gameMessage");
			if (!eventMatch.isEmpty()) {
				for (eventObject item : eventMatch) {
					eventName = item.getEventName();
					eventType = item.getEventType();
					eventParameters = item.getEventParameters();
				}
				fetchGameEvent = true;

				// Events with parameters that manipulate timing need to be processed immediately
				for (JsonElement param : eventParameters) {
					String eventParam = param.getAsString();
					// delaySubmission parameter temporarily prevents loot from being submitted
					// Some bosses (Nex) may take multiple ticks to completely log all their game messages
					if (eventParam.startsWith("delaySubmission")) {
						try { delayedSubmitTick = Integer.parseInt(eventParam.split("delaySubmission=")[1]); }
						catch (Exception e) { delayedSubmitTick = 2; }
					}
					// delayMsgCleanup parameter temporarily prevents the message collector from being cleared
					// Some bosses (Nightmare, Duke) fire onLootReceived on a future game tick
					if (eventParam.startsWith("delayMsgCleanup")) {
						try { delayedMsgCleanupTick = Integer.parseInt(eventParam.split("delayMsgCleanup=")[1]); }
						catch (Exception e) { delayedMsgCleanupTick = 2; }
					}
					// delayFetch parameter temporarily prevents an event from being processed
					// This is useful if waiting for other events unrelated to this event
					if (eventParam.startsWith("delayFetch")) {
						try { delayedGameEventTick = Integer.parseInt(eventParam.split("delayFetch=")[1]); }
						catch (Exception e) { delayedGameEventTick = 2; }
					}
					// suppressFetch parameters blocks an event from being sent back to the server
					if (eventParam.startsWith("suppressFetch")) fetchGameEvent = false;
				}
			}
		}

		// Find validation messages
		utils.matchValidation(panel, event, chatMessage);
	}

	@Subscribe
	public void onOverheadTextChanged(OverheadTextChanged event) {
		// Find overhead event messages
		if (gameEvents != null && !fetchGameEvent) {
			List<eventObject> eventMatch = utils.matchEvent(gameEvents, event.getOverheadText(), "gameEvent", "type", "overHeadText");
			if (!eventMatch.isEmpty()) {
				for (eventObject item : eventMatch) {
					eventName = item.getEventName();
					eventType = item.getEventType();
					eventParameters = item.getEventParameters();
				}
				utils.matchOverhead(event, eventName, eventType, eventParameters, recentActivities);
			}
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		// Find widget events
		if (gameEvents != null && !fetchGameEvent && delayDupeWidget <= 0) {
			List<eventObject> eventMatch = utils.matchEvent(gameEvents, Integer.toString(event.getGroupId()), "gameEvent", "type", "loadedWidget");
			if (!eventMatch.isEmpty()) {
				for (eventObject item : eventMatch) {
					eventName = item.getEventName();
					eventType = item.getEventType();
					eventParameters = item.getEventParameters();
					eventWidget = event.getGroupId();
				}
				fetchGameEvent = true;
				// Events with parameters that manipulate timing need to be processed immediately
				for (JsonElement param : eventParameters) {
					String eventParam = param.getAsString();
					// Used if the widget will be overwritten/unavailable the next tick
					if (eventParam.startsWith("processSameTick")) {
						utils.processEvent(eventName, eventType, eventParameters, eventWidget, messageCollector, recentActivities, -1);
					}
					// suppressFetch parameters blocks events from being sent back to the server
					if (eventParam.startsWith("suppressFetch")) fetchGameEvent = false;
				}
			}
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived npcLootReceived) {
		NPC npc = npcLootReceived.getNpc();

		// Allow or block loot requests if the event is configured in the allowblock list
		Boolean allowBlock = utils.processAllowBlock(allowBlockList, npc.getName(), "NPC");
		if (Boolean.FALSE.equals(allowBlock)) return;

		// If the submission needs to be delayed, hold a reference that can be submitted later
		Collection<ItemStack> items = npcLootReceived.getItems();
		if (delayedSubmitTick > 0) {
			lootObject.add("npcLoot");
			lootObject.add(npc.getName());
			lootObject.add(npc.getId());
			lootObject.add(items);
		} else {
			sendRequest.loot("npcLoot", npc.getName(), npc.getId(), items, messageCollector, recentActivities);
		}
	}

	@Subscribe
	public void onLootReceived(LootReceived lootReceived) {
		// Allow or block loot requests if the event is configured in the allowblock list
		// Default behavior: Loot of type "EVENT" is allowed, all other types are blocked (upstream handles NPCs)
		// No action is taken if the event is unspecified (allowBlock returns null)
		Boolean allowBlock = utils.processAllowBlock(allowBlockList, lootReceived.getName(), lootReceived.getType().toString());
		if (lootReceived.getType() != LootRecordType.EVENT && !Boolean.TRUE.equals(allowBlock)) return;
		if (Boolean.FALSE.equals(allowBlock)) return;

		// If the submission needs to be delayed, hold a reference that can be submitted later
		Collection<ItemStack> items = lootReceived.getItems();
		if (delayedSubmitTick > 0) {
			lootObject.add("otherLoot");
			lootObject.add(lootReceived.getName());
			lootObject.add(0);
			lootObject.add(items);
		} else {
			sendRequest.loot("otherLoot", lootReceived.getName(), 0, items, messageCollector, recentActivities);
		}
	}

	@Provides
	PokescapeConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(PokescapeConfig.class);
	}
}
