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

import com.pokescape.web.PokescapeClient;
import com.pokescape.ui.PokescapePanel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Actor;
import net.runelite.api.Varbits;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.awt.Color;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Utils {
    private @Inject Client client;
    private @Inject PokescapeClient sendRequest;
    private @Inject ChatMessageManager chatMessageManager;
    private @Inject PokescapePanel panel;

    private static final Pattern CHECKRIFT_REGEX = Pattern.compile("You have (\\d+) catalytic energy and (\\d+) elemental energy. You can use them to search the rift (\\d+) times. You have searched the rift (\\d+) times.");
    private static final Pattern CHECKTEMPO_REGEX = Pattern.compile("There is a reward for you to find in the reward pool.|There are (\\d+) rewards for you to find in the reward pool.");
    private static final Pattern EMPTYTEMPO_REGEX = Pattern.compile("There doesn't seem to be anything here for you. Maybe you could work with the Spirit Anglers...");

    private static final int COX_WIDGET = 32768010;
    private static final int TOA_WIDGET = 31522858;
    private static final int TOB_RAIDERS_VARC = 330;
    private static final int TOA_RAIDERS_VARC = 1099;

    public List<eventObject> matchEvent(JsonObject object, String event, String filterKey, String filterValue) {
        List<eventObject> matchedEvent = new ArrayList<>();
        object.keySet().forEach(keyName -> {
            JsonObject keyObj = object.get(keyName).getAsJsonObject();
            String filterKeyValue = keyObj.get(filterKey).getAsString();
            String eventKey = keyObj.get("event").getAsString();
            if (event.startsWith(eventKey) && filterKeyValue.equals(filterValue)) {
                JsonArray eventParameters = new JsonArray();
                if (keyObj.has("param")) eventParameters = keyObj.get("param").getAsJsonArray();
                matchedEvent.add(new eventObject(keyName, eventParameters));
            }
        });
        return matchedEvent;
    }

    public void processEvent(String eventName, JsonArray eventParameters, List<String> messageCollector) {
        JsonObject eventInfo = new JsonObject();
        for (JsonElement param : eventParameters) {
            String eventParam = param.getAsString();
            switch(eventParam) {
                case "raidInfo":
                    eventInfo.add("raidInfo", getRaidInfo());
                    break;
                case "visiblePlayers":
                    eventInfo.add("visiblePlayers", getVisiblePlayers());
                    break;
                case "playerLocation":
                    eventInfo.add("playerLocation", getPlayerLocation());
                    break;
                default:
            }
        }
        sendRequest.gameEvent(eventName, messageCollector, eventInfo);
    }

    public void matchValidation(PokescapePanel panel, ChatMessage event, String message) {
        // Check stored permits in a populated Tempoross reward pool.
        Matcher matcher = CHECKTEMPO_REGEX.matcher(message);
        if (matcher.find()) {
            int rewardPermits = 0;
            try { rewardPermits = Integer.parseInt(matcher.group(1)); }
            catch (Exception e) { rewardPermits = 1; }
            if (!panel.getTemporossVerification())
                sendRequest.validateMinigame(panel, "{\"activity\":\"tempoross\",\"rewardPermits\":"+rewardPermits+"}");
        }

        if (event.getType() == ChatMessageType.MESBOX) {
            // Check stored catalytic and elemental energy at the reward guardian.
            matcher = CHECKRIFT_REGEX.matcher(message);
            if (matcher.find()) {
                int catalyticEnergy = Integer.parseInt(matcher.group(1));
                int elementalEnergy = Integer.parseInt(matcher.group(2));
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

    public void matchOverhead(OverheadTextChanged event, String eventName, JsonArray eventParameters) {
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
                if (overheadActor == followerActor) sendRequest.gameEvent(eventName, messageCollector, null);
                break;
            case "matchSelf":
                if (overheadActor == playerActor) sendRequest.gameEvent(eventName, messageCollector, null);
                break;
            case "matchName":
                if (Objects.equals(overheadActorName, actorName)) sendRequest.gameEvent(eventName, messageCollector, null);
                break;
            default:
                sendRequest.gameEvent(eventName, messageCollector, null);
        }
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
            }
            catch(Exception e) { log.debug("Message could not be parsed"); break; }
        }
        // Send the message
        final String chatMessage = messageBuilder.build();
        if (!chatMessage.isEmpty()) chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.CONSOLE).runeLiteFormattedMessage(chatMessage).build());
    }

    private JsonObject getRaidInfo() {
        int raidersVarc, totalPoints = 0, personalPoints = 0, coxScale = 0, toaLevel;
        String raidName = "";
        JsonArray raidParty = new JsonArray();

        // Get CoX party members if there's a raid party in the grouping tab
        Widget raidWidget = client.getWidget(COX_WIDGET);
        if (raidWidget != null) {
            Widget[] coxParty = raidWidget.getChildren();
            if (coxParty != null) {
                for (Widget widget : coxParty) {
                    String partyEntry = widget.getText();
                    if (partyEntry.startsWith("<col=ffffff>")) {
                        String playerName = Text.removeTags(partyEntry);
                        raidParty.add(playerName);
                    }
                }
            }
            totalPoints = client.getVarbitValue(Varbits.TOTAL_POINTS);
            personalPoints = client.getVarbitValue(Varbits.PERSONAL_POINTS);
            coxScale = client.getVarbitValue(Varbits.RAID_PARTY_SIZE);
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
        raidWidget = client.getWidget(TOA_WIDGET);
        if (raidWidget != null) {
            raidersVarc = TOA_RAIDERS_VARC;
            int maxPartySize = 8;
            processRaidVarcs(raidersVarc, maxPartySize, raidParty);
            raidName = "toa";
        }

        // Return json object containing raid info
        JsonObject raidInfo = new JsonObject();
        int raidSize = raidParty.size();
        raidInfo.add("members", raidParty);
        raidInfo.addProperty("size", raidSize);
        switch (raidName) {
            case "cox":
                if (totalPoints != 0) raidInfo.addProperty("totalPts", totalPoints);
                if (personalPoints != 0) raidInfo.addProperty("personalPts", personalPoints);
                if (coxScale != 0) raidInfo.addProperty("scale", coxScale);
                break;
            case "tob":
                break;
            case "toa":
                try {toaLevel = Integer.parseInt(raidWidget.getText().replace("Level: ", ""));
                } catch (Exception e) {toaLevel = 0;}
                raidInfo.addProperty("invo", toaLevel);
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

    private JsonObject getPlayerLocation() {
        // Returns coordinates and regions of the player
        JsonObject locationInfo = new JsonObject();
        locationInfo.addProperty("worldX", client.getLocalPlayer().getWorldLocation().getX());
        locationInfo.addProperty("worldY", client.getLocalPlayer().getWorldLocation().getY());
        locationInfo.addProperty("region", client.getLocalPlayer().getWorldLocation().getRegionID());
        locationInfo.addProperty("regionX", client.getLocalPlayer().getWorldLocation().getRegionX());
        locationInfo.addProperty("regionY", client.getLocalPlayer().getWorldLocation().getRegionY());
        locationInfo.addProperty("plane", client.getLocalPlayer().getWorldLocation().getPlane());
        JsonArray regions = new JsonArray();int[] regionIDs = client.getMapRegions();
        for (int regionID : regionIDs) regions.add(regionID);
        locationInfo.add("regions", regions);
        return locationInfo;
    }
}
