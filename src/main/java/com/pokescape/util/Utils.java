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
package com.pokescape.util;

import com.pokescape.PokescapePlugin;
import com.pokescape.web.PokescapeClient;
import com.pokescape.ui.PokescapePanel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Actor;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.Player;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.util.Text;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.awt.Color;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Utils {
    private @Inject Client client;
    private @Inject ClientThread clientThread;
    private @Inject PokescapePlugin plugin;
    private @Inject PokescapeClient sendRequest;
    private @Inject ChatMessageManager chatMessageManager;
    private @Inject PokescapePanel panel;

    private static final Pattern CHECKRIFT_REGEX = Pattern.compile("You have ([0-9.,]+) catalytic energy and ([0-9.,]+) elemental energy. You can use them to search the rift ([0-9.,]+) times. You have searched the rift ([0-9.,]+) times.");
    private static final Pattern CHECKTEMPO_REGEX = Pattern.compile("There is a reward for you to find in the reward pool.|There are ([0-9.,]+) rewards for you to find in the reward pool.");
    private static final Pattern EMPTYTEMPO_REGEX = Pattern.compile("There doesn't seem to be anything here for you. Maybe you could work with the Spirit Anglers...");

    private static final int COX_WIDGET = 32768010;
    private static final int TOA_WIDGET = 31522858;
    private static final int TOB_RAIDERS_VARC = 330;
    private static final int TOA_RAIDERS_VARC = 1099;

    private static final int RAID_PARTY_SIZE = 5424;

    public List<eventObject> matchEvent(JsonObject object, String event, String eventType, String filterKey, String filterValue) {
        List<eventObject> matchedEvent = new ArrayList<>();
        object.keySet().forEach(keyName -> {
            JsonObject keyObj = object.get(keyName).getAsJsonObject();
            String filterKeyValue = keyObj.get(filterKey).getAsString();
            String eventKey = (keyObj.has("event") && !keyObj.get("event").isJsonNull()) ? keyObj.get("event").getAsString() : null;
            if (eventKey != null && (event.startsWith(eventKey) || event.matches(eventKey)) && filterKeyValue.equals(filterValue)) {
                JsonArray eventParameters = new JsonArray();
                if (keyObj.has("param")) eventParameters = keyObj.get("param").getAsJsonArray();
                matchedEvent.add(new eventObject(keyName, eventType, eventParameters));
            }
        });
        return matchedEvent;
    }

    public JsonObject matchActivity(JsonObject recentActivities, JsonObject object, String menuAction, String menuTarget, int animAction) {
        object.keySet().forEach(keyName -> {
            String keyCategory = object.get(keyName).getAsJsonObject().get("category").getAsString();
            if (!recentActivities.has(keyCategory)) recentActivities.add(keyCategory, null);
        });
        object.keySet().forEach(keyName -> {
            JsonObject keyActivity = object.get(keyName).getAsJsonObject();
            String keyCategory = object.get(keyName).getAsJsonObject().get("category").getAsString();
            boolean setToTarget = keyActivity.has("setToTarget") && !keyActivity.get("setToTarget").isJsonNull();
            JsonArray clearCategories = (keyActivity.has("clearCategories") && !keyActivity.get("clearCategories").isJsonNull()) ? keyActivity.get("clearCategories").getAsJsonArray() : null;
            JsonArray actionFilters = (keyActivity.has("menuActions") && !keyActivity.get("menuActions").isJsonNull()) ? keyActivity.get("menuActions").getAsJsonArray() : null;
            JsonArray targetFilters = (keyActivity.has("menuTargets") && !keyActivity.get("menuTargets").isJsonNull()) ? keyActivity.get("menuTargets").getAsJsonArray() : null;
            boolean actionMatch, targetMatch;
            if (actionFilters != null && targetFilters != null) {
                for (int i = 0; i < actionFilters.size(); i++) {
                    actionMatch = (menuAction.matches(actionFilters.get(i).getAsString()));
                    targetMatch = (menuTarget.matches(targetFilters.get(i).getAsString()));
                    if (actionMatch && targetMatch) {
                        if (clearCategories != null) for (JsonElement category : clearCategories)
                            recentActivities.add(category.toString().replaceAll("\"", ""), null);
                        if (setToTarget) recentActivities.addProperty(keyCategory, menuTarget);
                        else recentActivities.addProperty(keyCategory, keyName);
                    }
                }
            }
            if (animAction > -1) {
                JsonArray animFilters = (keyActivity.has("animActions") && !keyActivity.get("animActions").isJsonNull()) ? keyActivity.get("animActions").getAsJsonArray() : null;
                if (animFilters != null) {
                    for (int i = 0; i < animFilters.size(); i++) {
                        boolean animMatch = (animAction == animFilters.get(i).getAsInt());
                        if (animMatch) {
                            if (clearCategories != null) for (JsonElement category : clearCategories)
                                recentActivities.add(category.toString().replaceAll("\"", ""), null);
                            recentActivities.addProperty(keyCategory, keyName);
                        }
                    }
                }
            }
        });
        return recentActivities;
    }

    public Boolean processAllowBlock(JsonObject allowBlockList, String lootName, String lootType) {
        // Return null if the list cannot be found
        if (allowBlockList == null) return null;
        final Boolean[] matchAction = { null };
        allowBlockList.keySet().forEach(keyName -> {
            // Parse the values of each entry in the allowblock list
            String name = "", type = "", action = "";
            JsonObject listEntry = (allowBlockList.has(keyName)) ? allowBlockList.get(keyName).getAsJsonObject() : new JsonObject();
            if (listEntry.has("name") && !listEntry.get("name").isJsonNull())
                name = listEntry.get("name").getAsString();
            if (listEntry.has("type") && !listEntry.get("type").isJsonNull())
                type = listEntry.get("type").getAsString();
            if (listEntry.has("action") && !listEntry.get("action").isJsonNull())
                action = listEntry.get("action").getAsString();
            // If a matching entry is found, return true or false depending on whether the action is allow or block
            boolean nameMatch = lootName.matches(name), typeMatch = lootType.matches(type);
            if (nameMatch && typeMatch) {
                if (action.equals("allow")) matchAction[0] = true;
                if (action.equals("block")) matchAction[0] = false;
            }
        });
        // Returns true or false if matches were found. Return null otherwise
        return matchAction[0];
    }

    public void processEvent(String eventName, String eventType, JsonArray eventParameters, int eventWidget, List<String> messageCollector, JsonObject recentActivities, Integer spriteID) {
        JsonObject eventInfo = new JsonObject();
        boolean suppressRequest = false;
        for (JsonElement param : eventParameters) {
            String eventParam = param.getAsString();
            switch(eventParam) {
                case "widgetInfo":
                    eventInfo.add("widgetInfo", getWidgetInfo(eventWidget, eventParameters));
                    break;
                case "suppressRequest":
                    suppressRequest = true;
                    break;
                case "suppressNullWidget":
                    if (eventInfo.has("widgetInfo") && eventInfo.get("widgetInfo").isJsonNull()) suppressRequest = true;
                    break;
                case "raidInfo":
                    eventInfo.add("raidInfo", getRaidInfo());
                    break;
                case "visiblePlayers":
                    eventInfo.add("visiblePlayers", getVisiblePlayers());
                    break;
                case "playerLocation":
                    eventInfo.add("playerLocation", getPlayerLocation());
                    break;
                case "playerItems":
                    eventInfo.add("playerItems", getPlayerItems());
                    break;
                default:
            }
        }
        if (!suppressRequest) sendRequest.gameEvent(eventName, eventType, messageCollector, eventInfo, recentActivities, spriteID);
    }

    public void matchValidation(PokescapePanel panel, ChatMessage event, String message) {
        // Check stored permits in a populated Tempoross reward pool.
        Matcher matcher = CHECKTEMPO_REGEX.matcher(message);
        if (matcher.find()) {
            int rewardPermits = 0;
            try {
                rewardPermits = Integer.parseInt(matcher.group(1).replaceAll("[.,]", ""));
            } catch (Exception e) {
                rewardPermits = 1;
            }
            if (!panel.getTemporossVerification())
                sendRequest.validateMinigame(panel, "{\"activity\":\"tempoross\",\"rewardPermits\":"+rewardPermits+"}");
        }

        if (event.getType() == ChatMessageType.MESBOX) {
            // Check stored catalytic and elemental energy at the reward guardian.
            matcher = CHECKRIFT_REGEX.matcher(message);
            if (matcher.find()) {
                int catalyticEnergy = Integer.parseInt(matcher.group(1).replaceAll("[.,]",""));
                int elementalEnergy = Integer.parseInt(matcher.group(2).replaceAll("[.,]",""));
                if (!panel.getGotrVerification())
                    sendRequest.validateMinigame(panel, "{\"activity\":\"gotr\",\"catalyticEnergy\":"+catalyticEnergy+",\"elementalEnergy\":"+elementalEnergy+"}");
            }
            // Check stored permits in an empty Tempoross reward pool.
            matcher = EMPTYTEMPO_REGEX.matcher(message);
            if (matcher.find()) {
                int rewardPermits = 0;
                if (!panel.getTemporossVerification())
                    sendRequest.validateMinigame(panel, "{\"activity\":\"tempoross\",\"rewardPermits\":"+rewardPermits+"}");
            }
        }
    }

    public void matchOverhead(OverheadTextChanged event, String eventName, String eventType, JsonArray eventParameters, JsonObject recentActivities) {
        List<String> messageCollector = new ArrayList<>();
        Actor overheadActor = event.getActor();
        Actor followerActor = client.getFollower();
        Actor playerActor = client.getLocalPlayer();
        String overheadActorName = event.getActor().getName();
        String matchOp = eventParameters.get(0).getAsString();
        String actorName = (matchOp.startsWith("Actor=")) ? matchOp.substring(matchOp.indexOf("=")+1) : "";
        if (matchOp.startsWith("Actor=")) matchOp = "matchName";
        switch(matchOp) {
            case "matchFollower":
                if (overheadActor == followerActor)
                    sendRequest.gameEvent(eventName, eventType, messageCollector, null, recentActivities, -1);
                break;
            case "matchSelf":
                if (overheadActor == playerActor)
                    sendRequest.gameEvent(eventName, eventType, messageCollector, null, recentActivities, -1);
                break;
            case "matchName":
                if (Objects.equals(overheadActorName, actorName))
                    sendRequest.gameEvent(eventName, eventType, messageCollector, null, recentActivities, -1);
                break;
            default:
                sendRequest.gameEvent(eventName, eventType, messageCollector, null, recentActivities, -1);
        }
    }

    private JsonObject getWidgetInfo(int targetWidgetID, JsonArray eventParameters) {
        JsonObject widgetInfo = new JsonObject();
        // Go up one level and get the parent of the initial target
        int rootWidgetID = -1;
        for (int i = 0; i < 12; i++) {
            try {
                rootWidgetID = client.getWidget(targetWidgetID, i).getParentId();
            } catch (Exception e) {
            }
            if (rootWidgetID != -1) break;
        }
        // Processes all the childen of the root widget to generate a json object of all available text and sprites
        if (rootWidgetID != -1) processWidgetNodes(rootWidgetID, widgetInfo, 0, new int[]{0,0});

        // If filters are included with the event, apply them to the generated json object
        // This reduces the size of the request payload and restructures the widget data to have meaningful key-value pairs
        List<String> filterKeys = new ArrayList<>();
        List<String> renameKeys = new ArrayList<>();
        List<String> filterEVKeys = new ArrayList<>();
        String bitArrayKey = null;
        String playerStateKey = null;
        int delayDupeWidget = 0;
        for (JsonElement param : eventParameters) {
            String eventParam = param.getAsString();
            if (eventParam.startsWith("filterKeys="))
                filterKeys = Arrays.asList(eventParam.substring(eventParam.indexOf("=") + 1).split(","));
            if (eventParam.startsWith("renameKeys="))
                renameKeys = Arrays.asList(eventParam.substring(eventParam.indexOf("=") + 1).split(","));
            if (eventParam.startsWith("filterEVKeys="))
                filterEVKeys = Arrays.asList(eventParam.substring(eventParam.indexOf("=") + 1).split(","));
            if (eventParam.startsWith("makeBitArray=")) bitArrayKey = eventParam.substring(eventParam.indexOf("=") + 1);
            if (eventParam.startsWith("setPlayerState="))
                playerStateKey = eventParam.substring(eventParam.indexOf("=") + 1);
            if (eventParam.startsWith("delayDupeWidget")) {
                try {
                    delayDupeWidget = Integer.parseInt(eventParam.split("delayDupeWidget=")[1]);
                } catch (Exception e) {
                    delayDupeWidget = 2;
                }
            }
        }
        if (rootWidgetID != -1 && (filterKeys.size() > 0 || renameKeys.size() > 0))
            widgetInfo = filterWidgetInfo(widgetInfo, filterKeys, renameKeys, bitArrayKey, filterEVKeys, delayDupeWidget);

        // If requested, set the playerState to the processed widget data
        if (playerStateKey != null && widgetInfo != null && widgetInfo.has(playerStateKey)) {
            JsonObject playerState = plugin.getPlayerState();
            playerState.add(playerStateKey, widgetInfo.get(playerStateKey));
            plugin.setPlayerState(playerState);
        }
        return widgetInfo;
    }

    private void processWidgetNodes(int widgetID, JsonObject widgetStructure, int searchIteration, int[] childIteration) {
        // Grab all possible children of the target widget and concatenate them into a single array
        Widget[] sChildren = Objects.requireNonNull(client.getWidget(widgetID)).getStaticChildren();
        Widget[] nChildren = Objects.requireNonNull(client.getWidget(widgetID)).getNestedChildren();
        Widget[] dChildren = Objects.requireNonNull(client.getWidget(widgetID)).getDynamicChildren();
        Widget[] allChildren = Stream.concat(Stream.concat(Arrays.stream(sChildren), Arrays.stream(nChildren)), Arrays.stream(dChildren)).toArray(Widget[]::new);
        // For every child widget, search for text/sprite values, generate a unique key and add it the key-value to the json object
        for (int i = 0; i < allChildren.length; i++) {
            if (!allChildren[i].getText().equals("")) {
                String key = "Text_"+searchIteration+i;
                key = genUniqueStaticKey(widgetStructure, childIteration[0], key);
                widgetStructure.addProperty(key, Text.removeTags(allChildren[i].getText()));
            }
            if (allChildren[i].getSpriteId() > -1) {
                String key = "SpriteID_"+searchIteration+i;
                key = genUniqueStaticKey(widgetStructure, childIteration[1], key);
                widgetStructure.addProperty(key, allChildren[i].getSpriteId());
            }
            // Repeat the process for each node that has its own children
            // Most widgets won't have more than 50 total nodes, but for safety we cap this at 200 iterations
            if (allChildren[i].getId() != allChildren[i].getParentId() && allChildren[i].getParentId() != -1 && searchIteration < 200) {
                searchIteration += 1;
                processWidgetNodes(allChildren[i].getId(), widgetStructure, searchIteration, new int[]{0,0});
            }
        }
    }

    private String genUniqueStaticKey(JsonObject widgetStructure, int childIteration, String key) {
        if (widgetStructure.has(key)) {
            childIteration += 1;
            key = key+childIteration;
        }
        if (widgetStructure.has(key)) return genUniqueStaticKey(widgetStructure, childIteration, key);
        else return key;
    }

    private JsonObject filterWidgetInfo(JsonObject widgetStructure, List<String> filterKeys, List<String> renameKeys, String bitArrayKey, List<String> filterEVKeys, int delayDupeWidget) {
        JsonObject filteredStructure = new JsonObject();
        // Only add keys that are included in the filterkeys list
        for (String key : filterKeys) if (widgetStructure.has(key)) filteredStructure.add(key, widgetStructure.get(key));
        // Rename generated keys to the provided rename keys
        for (String key : renameKeys) {
            List<String> rename = Arrays.asList(key.split("::"));
            String oldName = rename.get(0);
            String newName = rename.get(1);
            // Return the key value as a boolean if there are mapped values provided
            List<String> boolValues = new ArrayList<>();
            if (newName.contains("!!")) {
                List<String> boolOp = Arrays.asList(newName.split("!!"));
                newName = boolOp.get(0);
                boolValues = Arrays.asList(boolOp.get(1).split(":"));
            }
            JsonElement returnValue = widgetStructure.get(oldName);
            if (boolValues.size() == 2) {
                if (returnValue.toString().equals(boolValues.get(0))) returnValue = new JsonPrimitive(true);
                if (returnValue.toString().equals(boolValues.get(1))) returnValue = new JsonPrimitive(false);
            }
            if (filteredStructure.has(oldName)) {
                filteredStructure.remove(oldName);
                filteredStructure.add(newName, returnValue);
            }
        }
        // Filter the result based on a target keyword
        if (filterEVKeys.size() > 0) {
            boolean eventValueFound = false;
            for (String eventValue : filterEVKeys) {
                // If the filter key is looking for the player's name replace the tag
                if (eventValue.contains("@localPlayerName") && client.getLocalPlayer().getName() != null) {
                    eventValue = eventValue.replace("@localPlayerName", client.getLocalPlayer().getName());
                }
                for (String key : filteredStructure.keySet()) {
                    JsonElement value = filteredStructure.get(key);
                    if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                        String strValue = value.getAsString();
                        if (strValue.matches(eventValue)) {
                            eventValueFound = true;
                            break;
                        }
                    }
                }
                if (eventValueFound) break;
            }
            if (eventValueFound && delayDupeWidget != 0) plugin.setDelayDupeWidget(delayDupeWidget);
            if (!eventValueFound) return null;
        }
        // Optional argument to pack booleans into an array to reduce transmission size. filterKeys sets the index order
        // Could use an int/long bitfield, but an array is more flexible if more than 64 bits need to be represented
        if (bitArrayKey != null) {
            JsonArray bitArray = new JsonArray();
            List<String> keyList = new ArrayList<>(filteredStructure.keySet());
            for (String key : keyList) {
                // We test for literal "true" or "false" strings so non-boolean values are filtered out from the array
                String value = filteredStructure.get(key).getAsString();
                if (value.equals("true")) bitArray.add(1);
                if (value.equals("false")) bitArray.add(0);
            }
            filteredStructure = new JsonObject();
            filteredStructure.add(bitArrayKey, bitArray);
        }
        return filteredStructure;
    }

    public void updateLootInfo(JsonObject recentActivities, String npcName, List<String> lootName, List<Integer> lootID, List<Integer> lootQuantity) {
        try {
            if (recentActivities.has("lastLootNPCName")) recentActivities.addProperty("lastLootNPCName", npcName);
            if (recentActivities.has("lastLootItems")) {
                JsonObject lootObject = new JsonObject();
                lootObject.add("lootName", new JsonArray());
                lootObject.add("lootID", new JsonArray());
                lootObject.add("lootQuantity", new JsonArray());
                for (String itemName : lootName) lootObject.get("lootName").getAsJsonArray().add(itemName);
                for (Integer itemID : lootID) lootObject.get("lootID").getAsJsonArray().add(itemID);
                for (Integer itemQuantity : lootQuantity) lootObject.get("lootQuantity").getAsJsonArray().add(itemQuantity);
                recentActivities.add("lastLootItems", lootObject);
            }
        } catch (Exception ignored) {}
    }

    public void sendLocalChatMsg(JsonArray messageStructure) {
        // Initialize the chat message builder
        ChatMessageBuilder messageBuilder = new ChatMessageBuilder();
        // Iterate through the color and message keys to build the message. This allows for multicolored messages
        for (JsonElement msgElem : messageStructure) {
            JsonObject msgObj; Color chatColor = Color.WHITE; String message = "";
            try {
                msgObj = msgElem.getAsJsonObject();
                if (msgObj != null && msgObj.has("color") && !msgObj.get("color").isJsonNull()) {
                    String hexColor = msgObj.get("color").getAsString();
                    try { chatColor = Color.decode(hexColor); }
                    catch(Exception e) { log.debug("Color could not be parsed"); }
                }
                if (msgObj != null && msgObj.has("message") && !msgObj.get("message").isJsonNull()) {
                    message = msgObj.get("message").getAsString();
                }
                messageBuilder.append(chatColor, message);
            } catch (Exception e) {
                log.debug("Message could not be parsed");
                break;
            }
        }
        // Send the message
        final String chatMessage = messageBuilder.build();
        if (!chatMessage.isEmpty()) chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.CONSOLE).runeLiteFormattedMessage(chatMessage).build());
    }

    private JsonObject getRaidInfo() {
        int raidersVarc, totalPoints = 0, personalPoints = 0, coxSize = 0, toaLevel;
        String raidName = "";
        JsonArray raidParty = new JsonArray();
        JsonArray toaInvosActive = new JsonArray();

        // Get CoX party members if the raid varbit is set
        int coxState = client.getVarbitValue(Varbits.IN_RAID);
        if (coxState > 0) {
            Widget coxWidget = client.getWidget(COX_WIDGET);
            if (coxWidget != null) {
                Widget[] coxParty = coxWidget.getChildren();
                if (coxParty != null) {
                    for (Widget widget : coxParty) {
                        String partyEntry = widget.getText();
                        if (partyEntry.startsWith("<col=ffffff>")) {
                            String playerName = Text.removeTags(partyEntry);
                            raidParty.add(playerName);
                        }
                    }
                }
            }
            totalPoints = client.getVarbitValue(Varbits.TOTAL_POINTS);
            personalPoints = client.getVarpValue(VarPlayer.RAIDS_PERSONAL_POINTS);
            coxSize = client.getVarbitValue(RAID_PARTY_SIZE);
            raidName = "cox";
        }

        // Get ToB party members if the tob state is set to "inside"
        int tobState = client.getVarbitValue(Varbits.THEATRE_OF_BLOOD);
        if (tobState > 1) {
            raidersVarc = TOB_RAIDERS_VARC;
            int maxPartySize = 5;
            processRaidVarcs(raidersVarc, maxPartySize, raidParty);
            raidName = "tob";
        }

        // Get ToA party members if the invocation level is being displayed
        Widget toaWidget = client.getWidget(TOA_WIDGET);
        if (toaWidget != null) {
            raidersVarc = TOA_RAIDERS_VARC;
            int maxPartySize = 8;
            JsonObject playerState = plugin.getPlayerState();
            if (playerState.has("toaInvos") && playerState.get("toaInvos").isJsonArray()) {
                toaInvosActive = playerState.get("toaInvos").getAsJsonArray();
            }
            processRaidVarcs(raidersVarc, maxPartySize, raidParty);
            raidName = "toa";
        }

        // Return json object containing raid info
        JsonObject raidInfo = new JsonObject();
        int raidSize = raidParty.size();
        raidInfo.add("members", raidParty);
        switch (raidName) {
            case "cox":
                raidInfo.addProperty("totalPts", totalPoints);
                raidInfo.addProperty("personalPts", personalPoints);
                raidInfo.addProperty("size", coxSize);
                break;
            case "tob":
                raidInfo.addProperty("size", raidSize);
                break;
            case "toa":
                raidInfo.addProperty("size", raidSize);
                try {toaLevel = Integer.parseInt(toaWidget.getText().replace("Level: ", ""));
                } catch (Exception e) { toaLevel = 0; }
                raidInfo.addProperty("invo", toaLevel);
                raidInfo.add("invosActive", toaInvosActive);
                break;
        }

        return raidInfo;
    }

    private void processRaidVarcs(int raidersVarc, int maxPartySize, JsonArray raidParty) {
        for (int i = 0; i < maxPartySize; i++) {
            String playerName = client.getVarcStrValue(raidersVarc + i);
            playerName = playerName.replaceAll("[^a-zA-Z0-9]", " ");
            if (!playerName.isEmpty()) raidParty.add(playerName);
        }
    }

    private JsonArray getVisiblePlayers() {
        // Returns the names of the first 25 players visible to the player
        JsonArray renderedPlayers = new JsonArray();
        List<Player> players = client.getPlayers();
        for (Player player : players) {
            String playerName = player.getName();
            renderedPlayers.add(playerName);
            if (renderedPlayers.size() >= 25) break;
        }
        return renderedPlayers;
    }

    public JsonObject getPlayerLocation() {
        // Returns coordinates and regions of the player
        JsonObject locationInfo = new JsonObject();
        locationInfo.addProperty("worldX", client.getLocalPlayer().getWorldLocation().getX());
        locationInfo.addProperty("worldY", client.getLocalPlayer().getWorldLocation().getY());
        locationInfo.addProperty("region", client.getLocalPlayer().getWorldLocation().getRegionID());
        locationInfo.addProperty("regionX", client.getLocalPlayer().getWorldLocation().getRegionX());
        locationInfo.addProperty("regionY", client.getLocalPlayer().getWorldLocation().getRegionY());
        locationInfo.addProperty("plane", client.getLocalPlayer().getWorldLocation().getPlane());
        JsonArray regions = new JsonArray();
        int[] regionIDs = client.getMapRegions();
        for (int regionID : regionIDs) regions.add(regionID);
        locationInfo.add("regions", regions);
        return locationInfo;
    }

    public JsonObject getPlayerItems() {
        // Fetch the player's inventory and gear containers
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);

        // Map gear to named slots. Set to null if nothing is equipped in the slot
        HashMap<String, Integer> equipmentSlots = new HashMap<>();
        if (equipment != null) {
            equipmentSlots.put("head", equipment.getItem(0) != null ? Objects.requireNonNull(equipment.getItem(0)).getId() : null);
            equipmentSlots.put("cape", equipment.getItem(1) != null ? Objects.requireNonNull(equipment.getItem(1)).getId() : null);
            equipmentSlots.put("neck", equipment.getItem(2) != null ? Objects.requireNonNull(equipment.getItem(2)).getId() : null);
            equipmentSlots.put("weapon", equipment.getItem(3) != null ? Objects.requireNonNull(equipment.getItem(3)).getId() : null);
            equipmentSlots.put("body", equipment.getItem(4) != null ? Objects.requireNonNull(equipment.getItem(4)).getId() : null);
            equipmentSlots.put("shield", equipment.getItem(5) != null ? Objects.requireNonNull(equipment.getItem(5)).getId() : null);
            equipmentSlots.put("legs", equipment.getItem(7) != null ? Objects.requireNonNull(equipment.getItem(7)).getId() : null);
            equipmentSlots.put("hands", equipment.getItem(9) != null ? Objects.requireNonNull(equipment.getItem(9)).getId() : null);
            equipmentSlots.put("feet", equipment.getItem(10) != null ? Objects.requireNonNull(equipment.getItem(10)).getId() : null);
            equipmentSlots.put("jaw", equipment.getItem(11) != null ? Objects.requireNonNull(equipment.getItem(11)).getId() : null);
            equipmentSlots.put("ring", equipment.getItem(12) != null ? Objects.requireNonNull(equipment.getItem(12)).getId() : null);
            equipmentSlots.put("ammo", equipment.getItem(13) != null ? Objects.requireNonNull(equipment.getItem(13)).getId() : null);
        }

        // Map all items to their respective containers
        Item[] itemsInventory = inventory != null ? inventory.getItems() : new Item[0];
        Item[] itemsEquipped = equipment != null ? equipment.getItems() : new Item[0];
        HashMap<String, Item[]> allItems = new HashMap<>();
        allItems.put("inventory", itemsInventory);
        allItems.put("equipment", itemsEquipped);

        // Convert mapped containers and return json object containing gear+inventory info
        JsonObject inventoryInfo = new JsonObject();
        for (String containerName : allItems.keySet()) {
            JsonObject containerItems = new JsonObject();
            JsonArray itemIDs = new JsonArray();
            JsonArray itemQuantities = new JsonArray();
            for (Item item : allItems.get(containerName)) {
                if (item.getId() != -1) itemIDs.add(item.getId());
                if (item.getId() > 0) itemQuantities.add(item.getQuantity());
            }
            containerItems.add("itemID", itemIDs);
            containerItems.add("itemQuantity", itemQuantities);
            // Create key-values for all inv items and their quantities
            if (containerName.equals("inventory")) {
                Map<Integer, Integer> itemQtyHashMap = consolidateStacks(itemIDs, itemQuantities);
                JsonObject itemQtyMap = mapToJsonObject(itemQtyHashMap);
                containerItems.add("itemQtyMap", itemQtyMap);
            }
            if (containerName.equals("equipment")) {
                JsonObject gearSlots = new JsonObject();
                for (String slot : equipmentSlots.keySet()) {
                    gearSlots.addProperty(slot, equipmentSlots.get(slot));
                }
                containerItems.add("slot", gearSlots);
            }
            inventoryInfo.add(containerName, containerItems);
        }
        return inventoryInfo;
    }

    public Map<Integer, Integer> consolidateStacks(JsonArray items, JsonArray qtys) {
        Map<Integer, Integer> itemQtyMap = new HashMap<>();
        Iterator<JsonElement> itemsIterator = items.iterator();
        Iterator<JsonElement> qtysIterator = qtys.iterator();
        while (itemsIterator.hasNext() && qtysIterator.hasNext()) {
            int item = itemsIterator.next().getAsInt();
            int qty = qtysIterator.next().getAsInt();
            itemQtyMap.put(item, itemQtyMap.getOrDefault(item, 0) + qty);
        }
        return itemQtyMap;
    }

    private JsonObject mapToJsonObject(Map<Integer, Integer> map) {
        JsonObject jsonObject = new JsonObject();
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            jsonObject.addProperty(entry.getKey().toString(), entry.getValue());
        }
        return jsonObject;
    }
}
