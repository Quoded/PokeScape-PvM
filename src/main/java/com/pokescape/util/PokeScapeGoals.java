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

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pokescape.PokescapePlugin;
import com.pokescape.ui.PokescapePanel;
import com.pokescape.web.PokescapeClient;
import net.runelite.api.InventoryID;
import net.runelite.api.Player;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.Client;
import net.runelite.api.Varbits;
import net.runelite.api.VarPlayer;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.GameState;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.NPCManager;
import net.runelite.client.util.Text;
import static net.runelite.http.api.RuneLiteAPI.GSON;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PokeScapeGoals {
    private @Inject Client client;
    private @Inject ClientThread clientThread;
    private @Inject EventBus eventBus;
    private @Inject Utils utils;
    private @Inject PokescapePlugin plugin;
    private @Inject PokescapeClient sendRequest;
    private @Inject ChatMessageManager chatMessageManager;
    private @Inject NPCManager npcManager;
    private @Inject PokescapePanel panel;

    private JsonObject gameActivities;
    private JsonObject playerState;
    private JsonArray activeGoals;
    private JsonArray evaluateConditions;
    private final LinkedHashMap<String, String> goalTypes = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, String> damageMap = new LinkedHashMap<>();
    private final List<Integer> varbitFilter = new ArrayList<>();
    private final List<Integer> scriptFilter = new ArrayList<>();

    public void startUp() {
        clearState();
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
        clearState();
    }

    private void clearState() {
        activeGoals = null;
        playerState = null;
    }

    public void setGoals(JsonArray goals) {
        // Add goals if there are no active goals
        if (activeGoals == null || activeGoals.size() < 1) activeGoals = goals;

        // Sync goals: Add new goals and remove completed goals without overwriting active goals
        JsonArray syncedGoals = new JsonArray();
        for (int i = 0; i < goals.size(); i++) {
            JsonObject goal = goals.get(i).getAsJsonObject();
            if (goal.has("goalName") && !goal.get("goalName").isJsonNull()) {
                String receivedGoalName = goal.get("goalName").getAsString();
                boolean matchFound = false;
                for (int j = 0; j < activeGoals.size(); j++) {
                    JsonObject activeGoal = activeGoals.get(j).getAsJsonObject();
                    String activeGoalName = "";
                    if (activeGoal.has("goalName") && !activeGoal.get("goalName").isJsonNull()) {
                        activeGoalName = activeGoal.get("goalName").getAsString();
                    }
                    if (activeGoalName.equals(receivedGoalName)) {
                        matchFound = true;
                        syncedGoals.add(activeGoal);
                    }
                }
                if (!matchFound) syncedGoals.add(goal);
            }
        }
        activeGoals = syncedGoals;

        // Notify the player for each new goal they have. Only notifies once per session or when assigned a new goal
        for (int i = 0; i < activeGoals.size(); i++) {
            JsonObject goal = activeGoals.get(i).getAsJsonObject();
            boolean playerNotified = true;
            if (goal.has("playerNotified") && !goal.get("playerNotified").isJsonNull()) {
                playerNotified = goal.get("playerNotified").getAsBoolean();
            }
            if (!playerNotified && goal.has("goalNotify") && !goal.get("goalNotify").isJsonNull()) {
                activeGoals.get(i).getAsJsonObject().addProperty("playerNotified", true);
                JsonArray goalNotification = goal.get("goalNotify").getAsJsonArray();
                if (goal.get("goalNotify").isJsonArray()) utils.sendLocalChatMsg(goalNotification);
            }
        }

        // Initialize the activities and the playerstate
        if (gameActivities == null) gameActivities = plugin.getGameActivities();
        if (gameActivities != null && playerState == null) setPlayerState();
    }

    public void setPlayerState() {
        gameActivities = plugin.getGameActivities();
        if (gameActivities != null && gameActivities.isJsonObject()) {
            playerState = plugin.getPlayerState();
            if (gameActivities.has("goalTypes") && gameActivities.get("goalTypes").getAsJsonObject().get("types").isJsonArray() &&
                gameActivities.get("goalTypes").getAsJsonObject().get("states").isJsonArray()) {
                JsonArray types = gameActivities.get("goalTypes").getAsJsonObject().get("types").getAsJsonArray();
                JsonArray states = gameActivities.get("goalTypes").getAsJsonObject().get("states").getAsJsonArray();
                for (int i = 0; i < types.size(); i++) goalTypes.put(types.get(i).getAsString(), states.get(i).getAsString());
            }
            if (gameActivities.has("evaluateConditions") && gameActivities.get("evaluateConditions").getAsJsonObject().get("conditions").isJsonArray()) {
                evaluateConditions = gameActivities.get("evaluateConditions").getAsJsonObject().get("conditions").getAsJsonArray();
            }
            if (gameActivities.has("filterActivities") && gameActivities.get("filterActivities").getAsJsonObject().get("filter").isJsonArray()) {
                playerState.add("filterActivities", gameActivities.get("filterActivities").getAsJsonObject().get("filter").getAsJsonArray());
            }
            if (gameActivities.has("nullOnLoot") && gameActivities.get("nullOnLoot").getAsJsonObject().get("null").isJsonArray()) {
                playerState.add("nullOnLoot", gameActivities.get("nullOnLoot").getAsJsonObject().get("null").getAsJsonArray());
            }
            if (gameActivities.has("trackedSkills") && gameActivities.get("trackedSkills").getAsJsonObject().get("skills").isJsonArray()) {
                playerState.add("trackedSkills", gameActivities.get("trackedSkills").getAsJsonObject().get("skills").getAsJsonArray());
            }
            if (gameActivities.has("damageMap") && gameActivities.get("damageMap").getAsJsonObject().get("damage").isJsonArray() &&
                    gameActivities.get("damageMap").getAsJsonObject().get("source").isJsonArray()) {
                JsonArray dmg = gameActivities.get("damageMap").getAsJsonObject().get("damage").getAsJsonArray();
                JsonArray source = gameActivities.get("damageMap").getAsJsonObject().get("source").getAsJsonArray();
                for (int i = 0; i < dmg.size(); i++) damageMap.put(dmg.get(i).getAsInt(), source.get(i).getAsString());
            }
            if (gameActivities.has("trackedVarbits") && gameActivities.get("trackedVarbits").getAsJsonObject().get("varbits").isJsonArray() &&
                gameActivities.get("trackedVarbits").getAsJsonObject().get("varTypes").isJsonArray()) {
                JsonArray varbits = gameActivities.get("trackedVarbits").getAsJsonObject().get("varbits").getAsJsonArray();
                JsonArray varTypes = gameActivities.get("trackedVarbits").getAsJsonObject().get("varTypes").getAsJsonArray();
                JsonArray initialVarbitValues = new JsonArray();
                for (int i = 0; i < varbits.size(); i++) {
                    varbitFilter.add(varbits.get(i).getAsInt());
                    initialVarbitValues.add(0);
                }
                playerState.add("trackedVarbits", varbits);
                playerState.add("varTypes", varTypes);
                playerState.add("lastVarbitValues", initialVarbitValues);
            }
            if (gameActivities.has("trackedScripts") && gameActivities.get("trackedScripts").getAsJsonObject().get("scripts").isJsonArray()) {
                JsonArray scripts = gameActivities.get("trackedScripts").getAsJsonObject().get("scripts").getAsJsonArray();
                for (int i = 0; i < scripts.size(); i++) scriptFilter.add(i,scripts.get(i).getAsInt());
            }
            if (gameActivities.has("lastGearAndItems")) playerState.add("lastGearAndItems", utils.getPlayerItems());
            if (gameActivities.has("lastLocation")) playerState.add("lastLocation", utils.getPlayerLocation());
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (playerState == null || gameActivities == null) return;
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM &&
            event.getType() != ChatMessageType.FRIENDSCHATNOTIFICATION && event.getType() != ChatMessageType.MESBOX &&
            event.getType() != ChatMessageType.DIALOG) return;
        // Evaluate the goal requirements on each message
        if (playerState.has("lastChatMessage")) {
            String chatMessage = Text.removeTags(event.getMessage());
            playerState.addProperty("lastChatMessage", chatMessage);
            evaluateGoal();
        }
    }

    @Subscribe
    private void onStatChanged(StatChanged event) {
        if (playerState == null || gameActivities == null) return;
        // Track changes to skills and evaluate the goal requirements each time a tracked stat updates
        boolean restartTracking = false;
        trackStatChanges(restartTracking);
        evaluateGoal();
    }

    @Subscribe
    private void onVarbitChanged(VarbitChanged event) {
        if (playerState == null || gameActivities == null || (!varbitFilter.contains(event.getVarbitId()) && !varbitFilter.contains(event.getVarpId()))) return;
        // Track varbit value changes and evaluate the goal requirements each time a tracked varbit updates
        int[] updatedVarbit = { event.getVarbitId(), event.getVarpId(), event.getValue() };
        trackVarbitChanges(updatedVarbit);
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event) {
        if (playerState == null || gameActivities == null || !scriptFilter.contains(event.getScriptId())) return;
        // Evaluate the goal requirements each time a script fires
        // Upstream can be very spammy even after filtering. We gate this so downstream only evaluates at most once a tick
        if (playerState.has("lastScriptPostFired") && playerState.get("lastScriptPostFired").isJsonNull()) {
            playerState.addProperty("lastScriptPostFired", event.getScriptId());
            clientThread.invokeLater(this::evaluateGoal);
        }
    }

    @Subscribe
    private void onGameStateChanged(GameStateChanged event) {
        if (playerState == null || gameActivities == null) return;
        GameState areaLoaded = event.getGameState();
        if (areaLoaded != GameState.LOGGED_IN && areaLoaded != GameState.LOGIN_SCREEN && areaLoaded != GameState.HOPPING) return;
        // Evaluate the goal requirements each time a map region is loaded
        int loadedRegion = client.getLocalPlayer().getWorldLocation().getRegionID();
        trackRegionChanges(loadedRegion);
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (playerState == null || gameActivities == null) return;
        // Evaluate the goal requirements each time gear or inventory changes
        if (event.getContainerId() == InventoryID.EQUIPMENT.getId() || event.getContainerId() == InventoryID.INVENTORY.getId()) {
            if (playerState.has("lastGearAndItems")) playerState.add("lastGearAndItems", utils.getPlayerItems());
            evaluateGoal();
        }
    }

    @Subscribe
    public void onItemDespawned(ItemDespawned event) {
        if (playerState == null || gameActivities == null) return;
        // Detect the last item the player has picked an item from the ground, tables or adjacent tiles
        trackLastItemPickup(event);
    }

    @Subscribe
    private void onAnimationChanged(AnimationChanged event) {
        if (playerState == null || gameActivities == null) return;
        if (event.getActor() == client.getLocalPlayer()) {
            int lastPlayerAnim = event.getActor().getAnimation();
            playerState = plugin.getPlayerState();
            gameActivities = plugin.getGameActivities();
            // Record changes to tracked animations to the playerstate
            playerState = utils.matchActivity(playerState, gameActivities, "", "", lastPlayerAnim);
            if (playerState.has("lastPlayerAnim")) playerState.addProperty("lastPlayerAnim", lastPlayerAnim);
            clientThread.invokeLater(this::evaluateGoal);
        }
    }

    @Subscribe
    private void onHitsplatApplied(HitsplatApplied event) {
        if (playerState == null || gameActivities == null) return;
        // Track the last damage dealt and taken.
        String lastTargetInstance = (playerState.has("lastTargetInstance") && !playerState.get("lastTargetInstance").isJsonNull()) ?
                playerState.get("lastTargetInstance").getAsString() : "";
        // Evaluate the goal requirements each time damage is dealt (on the next tick) or taken
        if (event.getActor().toString().equals(lastTargetInstance) && playerState.has("lastDmgDealt")) {
            playerState.addProperty("lastDmgDealt", event.getHitsplat().getAmount());
            clientThread.invokeLater(() -> attackedFromFullHP(event));
        }
        if (event.getActor().toString().equals(client.getLocalPlayer().toString()) && playerState.has("lastDmgTaken"))
            playerState.addProperty("lastDmgTaken", event.getHitsplat().getAmount());
    }

    @Subscribe
    private void onInteractingChanged(InteractingChanged event) {
        if (playerState == null || gameActivities == null) return;
        // Track the last NPC the player interacted with
        if (event.getTarget() != null && (event.getTarget() instanceof Player || event.getTarget() instanceof NPC)) {
            if (Objects.equals(event.getSource(), client.getLocalPlayer())) {
                if (playerState.has("lastTargetName")) playerState.addProperty("lastTargetName", event.getTarget().getName());
                if (playerState.has("lastTargetInstance")) playerState.addProperty("lastTargetInstance", event.getTarget().toString());
            }
        }
    }

    @Subscribe
    private void onActorDeath(ActorDeath event) {
        if (playerState == null || gameActivities == null) return;
        clientThread.invokeLater(() -> {
            String npcInst = event.getActor().toString();
            for (int i = 0; i < activeGoals.size(); i++) {
                JsonObject goal = activeGoals.get(i).getAsJsonObject();
                if (goal.has("validTargets") && goal.get("validTargets").isJsonArray()) {
                    JsonArray validTargets = goal.getAsJsonArray("validTargets");
                    JsonArray validTargetsDmg = goal.has("validTargetsDmg") ? goal.getAsJsonArray("validTargetsDmg") : new JsonArray();
                    boolean validExists = false;
                    for (int x = 0; x < validTargets.size(); x++) {
                        if (validTargets.get(x).getAsString().equals(npcInst)) { validExists = true; break; }
                    }
                    if (validExists) {
                        // Evaluate the goal requirements each time an NPC dies
                        if (playerState.has("npcValidDeath")) playerState.addProperty("npcValidDeath", event.getActor().getName());
                        evaluateGoal();
                        // Remove the npc from the list of targets after evaluation
                        if (playerState.has("npcValidDeath")) playerState.add("npcValidDeath", null);
                        for (int x = 0; x < validTargets.size(); x++) {
                            if (validTargets.get(x).getAsString().equals(npcInst)) {
                                validTargets.remove(x);
                                if (goal.has("validTargetsDmg")) validTargetsDmg.remove(x);
                                break;
                            }
                        }
                    }
                }
            }
        });
    }

    private void trackStatChanges(boolean restartTracking) {
        // Get each skill that is being tracked
        JsonArray trackedSkills = new JsonArray();
        if (gameActivities.has("trackedSkills") && !gameActivities.get("trackedSkills").getAsJsonObject().isJsonNull()) {
            trackedSkills = gameActivities.get("trackedSkills").getAsJsonObject().get("skills").getAsJsonArray();
        }

        // Get the current level of each tracked skill and initialize losses and gains at 0
        JsonArray skillLevels = new JsonArray();
        JsonArray lastGoalSkillLosses = new JsonArray();
        JsonArray lastGoalSkillGains = new JsonArray();
        for (int i = 0; i < trackedSkills.size(); i++) {
            skillLevels.add(client.getBoostedSkillLevel(Skill.valueOf(trackedSkills.get(i).getAsString())));
            lastGoalSkillLosses.add(0);
            lastGoalSkillGains.add(0);
        }

        // Get the previous level of each tracked skill
        JsonArray prevSkillLevels = (playerState.has("lastSkillLevels") && playerState.get("lastSkillLevels").isJsonArray()) ?
                playerState.get("lastSkillLevels").getAsJsonArray() : skillLevels;

        // Calculate the deltas of each tracked skill
        JsonArray skillLevelDeltas = new JsonArray();
        if (playerState.has("lastSkillDeltas")) {
            for (int i = 0; i < trackedSkills.size(); i++) {
                skillLevelDeltas.add(skillLevels.get(i).getAsInt() - prevSkillLevels.get(i).getAsInt());
            }
        }

        // Get the last known losses and gains of each tracked skill
        if (playerState.has("lastGoalSkillLosses") && playerState.get("lastGoalSkillLosses").isJsonArray()) {
            lastGoalSkillLosses = playerState.get("lastGoalSkillLosses").getAsJsonArray();
        }
        if (playerState.has("lastGoalSkillGains") && playerState.get("lastGoalSkillGains").isJsonArray()) {
            lastGoalSkillGains = playerState.get("lastGoalSkillGains").getAsJsonArray();
        }

        // Use the calculated deltas to set the new losses and gains of each tracked skill
        // If restartTracking is passed in, we reset the losses and gains instead
        JsonArray newGoalSkillLosses = new JsonArray();
        JsonArray newGoalSkillGains = new JsonArray();
        for (int i = 0; i < trackedSkills.size(); i++) {
            if (restartTracking) { newGoalSkillLosses.add(0); newGoalSkillGains.add(0); }
            else {
                if (skillLevelDeltas.get(i).getAsInt() < 0) {
                    newGoalSkillLosses.add(lastGoalSkillLosses.get(i).getAsInt() + skillLevelDeltas.get(i).getAsInt());
                    newGoalSkillGains.add(lastGoalSkillGains.get(i).getAsInt());
                }
                if (skillLevelDeltas.get(i).getAsInt() > 0) {
                    newGoalSkillGains.add(lastGoalSkillGains.get(i).getAsInt() + skillLevelDeltas.get(i).getAsInt());
                    newGoalSkillLosses.add(lastGoalSkillLosses.get(i).getAsInt());
                }
                if (skillLevelDeltas.get(i).getAsInt() == 0) {
                    newGoalSkillLosses.add(lastGoalSkillLosses.get(i).getAsInt());
                    newGoalSkillGains.add(lastGoalSkillGains.get(i).getAsInt());
                }
            }
        }

        // Update the playerstate with the new skill levels, deltas and accumulated losses and gains
        playerState.add("lastSkillLevels", skillLevels);
        playerState.add("lastSkillDeltas", skillLevelDeltas);
        playerState.add("lastGoalSkillLosses", newGoalSkillLosses);
        playerState.add("lastGoalSkillGains", newGoalSkillGains);
    }

    private void trackVarbitChanges(int[] updatedVarbit) {
        if (playerState.has("trackedVarbits") && playerState.get("trackedVarbits").isJsonArray() &&
            playerState.has("varTypes") && playerState.get("varTypes").isJsonArray()) {
            JsonArray trackedVarbits = playerState.get("trackedVarbits").getAsJsonArray();
            JsonArray trackedVarbTypes = playerState.get("varTypes").getAsJsonArray();
            JsonArray trackedVarbitValues = new JsonArray();
            for (int i = 0; i < trackedVarbits.size(); i++) {
                int varbType = trackedVarbTypes.get(i).getAsInt();
                // If a tracked varbit was updated, save the new value to the playerstate and evaluate the goal
                // Otherwise, save the previous value of the varbit or initialize it at 0
                if (trackedVarbits.get(i).getAsInt() == updatedVarbit[varbType]) {
                    trackedVarbitValues.add(updatedVarbit[2]);
                    evaluateGoal();
                } else {
                    int lastVarbitValue = (playerState.has("lastVarbitValues") && playerState.get("lastVarbitValues").isJsonArray()) ?
                    playerState.get("lastVarbitValues").getAsJsonArray().get(i).getAsInt() : 0;
                    trackedVarbitValues.add(lastVarbitValue);
                }
            }
            playerState.add("lastVarbitValues", trackedVarbitValues);
        }
    }

    private void trackLastItemPickup(ItemDespawned event) {
        // Get the coordinates of the despawned item and the player
        int despawnX = event.getTile().getWorldLocation().getX();
        int despawnY = event.getTile().getWorldLocation().getY();
        int playerX = client.getLocalPlayer().getWorldLocation().getX();
        int playerY = client.getLocalPlayer().getWorldLocation().getY();

        // Determine if the "Take" option has not been cancelled and the item has despawned under or next to the player
        boolean takeOpActive = playerState.has("takeOpActive") && !playerState.get("takeOpActive").isJsonNull();
        if (takeOpActive && (Math.abs(despawnX-playerX) <= 1) && (Math.abs(despawnY-playerY) <= 1)) {
            String quantitiesPreUpdate = utils.getPlayerItems().get("inventory").getAsJsonObject().get("itemQuantity").getAsJsonArray().toString();
            // It's still possible that the item could have despawned naturally or another player took the item
            // On the next tick check if the player's inventory has been updated with the item that just despawned
            clientThread.invokeLater(() -> {
                JsonArray invPostUpdate = utils.getPlayerItems().get("inventory").getAsJsonObject().get("itemID").getAsJsonArray();
                boolean itemFound = false;
                for (JsonElement item : invPostUpdate) {
                    int itemID = item.getAsInt();
                    if (itemID == event.getItem().getId()) { itemFound = true; break; }
                }
                // If the despawned item is in the player's inv, also check that the item quantities have updated
                String quantitiesPostUpdate = utils.getPlayerItems().get("inventory").getAsJsonObject().get("itemQuantity").getAsJsonArray().toString();
                if (itemFound && !quantitiesPreUpdate.equals(quantitiesPostUpdate) && playerState.has("lastItemPickup")) {
                    playerState.addProperty("lastItemPickup", event.getItem().getId());
                    playerState.add("takeOpActive", null);
                    // Evaluate the goal requirements each time an item is picked up
                    evaluateGoal();
                }
            });
        }
    }

    private void trackRegionChanges(int newRegion) {
        // Get the last region saved in the playerState
        JsonObject lastLocation = (playerState.has("lastLocation") && playerState.get("lastLocation").isJsonObject()) ?
                playerState.get("lastLocation").getAsJsonObject() : new JsonObject();
        int prevRegion = (lastLocation.has("region") && !lastLocation.get("region").isJsonNull()) ? lastLocation.get("region").getAsInt() : newRegion;

        // The region transition is tracked as a string with a comma seperating the two regions
        String regionTransition = prevRegion+","+newRegion;
        if (playerState.has("lastRegionTransition")) playerState.addProperty("lastRegionTransition", regionTransition);

        // Evaluate the research requirements each time a new region is loaded
        evaluateGoal();

        // Flag used for detecting non-standard teleports, resets when a new region is loaded
        if (playerState.has("teleportQueued") && !playerState.get("teleportQueued").isJsonNull()) {
            playerState.add("teleportQueued", null);
        }

        // Update the playerstate with the last location
        if (playerState.has("lastLocation")) playerState.add("lastLocation", utils.getPlayerLocation());
    }

    private void attackedFromFullHP(HitsplatApplied event) {
        // Get the health ratios, damage dealt and max HP of the attacked NPC
        String npcName = Text.removeTags(event.getActor().getName());
        int damageDealt = event.getHitsplat().getAmount();
        int[] npcHP; int npcCurrentHP = 0; int npcMaxHP = 0;

        // If there's an HP overlay: Retrieve the current and max HP values directly from the overlay
        // If there's no HP overlay: Calculate the current and max HP values from ratios
        boolean npcHPOverlay = client.getVarbitValue(Varbits.BOSS_HEALTH_OVERLAY) == 0;
        boolean npcIDOverlay = client.getVarpValue(VarPlayer.HP_HUD_NPC_ID) != -1;
        if (npcHPOverlay && npcIDOverlay && event.getActor() instanceof NPC) npcHP = hpFromOverlay();
        else npcHP = hpFromRatios(npcName, event);
        if (npcHP[0] != -1) npcCurrentHP = npcHP[0];
        if (npcHP[1] != -1) npcMaxHP = npcHP[1];

        // Determine if the player initiated combat with a NPC at full health by comparing the NPC's max+current HP against the last hitsplat
        // Calculating the hp from ratios is not perfect, so a threshold is also calculated that scales with the HP of the target (+1 for every 100 hp)
        if (activeGoals != null) {
            for (int i = 0; i < activeGoals.size(); i++) {
                JsonObject goal = activeGoals.get(i).getAsJsonObject();
                boolean invalidTarget = false;
                String npcInst = event.getActor().toString();

                // If max HP is provided, use that value instead
                npcMaxHP = (goal.has("npcMaxHP") && !goal.get("npcMaxHP").isJsonNull()) ? goal.get("npcMaxHP").getAsInt() : npcMaxHP;
                int hpThreshold = (int) Math.ceil((double) npcMaxHP / 100) + 1;
                if (Math.abs((npcMaxHP - damageDealt) - npcCurrentHP) <= hpThreshold) {
                    // Check if the attacked npc has been marked as invalid (from a previous failed goal attempt)
                    if (goal.has("invalidTargets") && goal.get("invalidTargets").isJsonArray()) {
                        JsonArray invalidTargets = goal.get("invalidTargets").getAsJsonArray();
                        for (JsonElement target : invalidTargets) {
                            if (Objects.equals(target.getAsString(), npcInst)) {
                                invalidTarget = true;
                                break;
                            }
                        }
                    }
                    // Add the npc as a valid target if it isn't invalid and isn't already marked as valid
                    if (!invalidTarget) {
                        if (playerState.has("npcAttackedAtFullHP")) playerState.addProperty("npcAttackedAtFullHP", npcName);
                        if (goal.has("validTargets") && goal.get("validTargets").isJsonArray()) {
                            JsonArray validTargets = goal.get("validTargets").getAsJsonArray();
                            boolean validExists = false;
                            for (JsonElement target : validTargets) {
                                if (Objects.equals(target.getAsString(), npcInst)) {
                                    validExists = true;
                                    break;
                                }
                            }
                            if (!validExists) {
                                validTargets.add(npcInst);
                                goal.add("validTargets", validTargets);
                                if (goal.has("validTargetsDmg") && goal.get("validTargetsDmg").isJsonArray()) {
                                    JsonArray validTargetsDmg = goal.get("validTargetsDmg").getAsJsonArray();
                                    validTargetsDmg.add(0);
                                    goal.add("validTargetsDmg", validTargetsDmg);
                                }
                            }
                        }
                        // Evaluate the goal requirements before setting npcAttackedAtFullHP back to null
                        evaluateGoal();
                        if (playerState.has("npcAttackedAtFullHP")) playerState.add("npcAttackedAtFullHP", null);
                    }
                }
                String dmgSource = damageMap.get(event.getHitsplat().getHitsplatType());
                if (!invalidTarget && dmgSource != null && dmgSource.equals("self")) {
                    if (goal.has("validTargets") && goal.get("validTargets").isJsonArray()) {
                        JsonArray validTargets = goal.get("validTargets").getAsJsonArray();
                        if (goal.has("validTargetsDmg") && goal.get("validTargetsDmg").isJsonArray()) {
                            JsonArray validTargetsDmg = goal.get("validTargetsDmg").getAsJsonArray();
                            for (int x = 0; x < validTargets.size(); x++) {
                                if (validTargets.get(x).getAsString().equals(npcInst)) {
                                    int addedDmg = validTargetsDmg.get(x).getAsInt()+damageDealt;
                                    validTargetsDmg.set(x, new JsonPrimitive(addedDmg));
                                }
                            }
                        }
                    }
                }
            }
        }
        evaluateGoal();
    }

    private int[] hpFromOverlay() {
        int currentHP = -1; int maxHP = -1;
        Widget hpWidget = client.getWidget(ComponentID.HEALTH_HEALTHBAR_TEXT);
        if (hpWidget != null) {
            Pattern HP_REGEX = Pattern.compile("(\\d+) / (\\d+) \\(.*");
            Matcher matcher = HP_REGEX.matcher(hpWidget.getText());
            if (matcher.find()) {
                currentHP = Integer.parseInt(matcher.group(1));
                maxHP = Integer.parseInt(matcher.group(2));
            }
        }
        return new int[] {currentHP, maxHP};
    }

    private int[] hpFromRatios(String npcName, HitsplatApplied event) {
        int currentHP = -1;
        int lastRatio = event.getActor().getHealthRatio();
        int lastHealthScale = event.getActor().getHealthScale();
        int npcID = getNpcIdByName(npcName);
        Integer maxHP = npcManager.getHealth(npcID);
        // If npcManager cannot return info, fallback to using the hp scale as the max hp
        // We also have the option to override the maxHP if it's incorrect or missing
        if (maxHP == null) maxHP = lastHealthScale;

        // Calculate the current HP from the known max hp and hp scale ratios
        if (maxHP > 0 && lastRatio > 0 && lastHealthScale > 1) {
            int minHealth = (maxHP * (lastRatio - 1) + lastHealthScale - 2) / (lastHealthScale - 1);
            int maxHealth = Math.min((maxHP * lastRatio - 1) / (lastHealthScale - 1), maxHP);
            currentHP = (minHealth + maxHealth + 1) / 2;
        }
        return new int[] {currentHP, maxHP};
    }

    private int getNpcIdByName(String name) {
        for (NPC npc : client.getCachedNPCs()) {
            if (npc != null && npc.getName() != null && npc.getName().equalsIgnoreCase(name)) return npc.getId();
        }
        return -1;
    }

    private void evaluateGoal() {
        if (activeGoals == null || evaluateConditions == null) return;
        // Here we check if the player's research needs to be started, failed, updated or completed
        for (Map.Entry<String, String> entry : goalTypes.entrySet()) {
            String goalType = entry.getKey();
            String targetState = entry.getValue();
            for (int i = 0; i < activeGoals.size(); i++) {
                JsonObject goal = activeGoals.get(i).getAsJsonObject();
                String goalState = goal.has("goalState") ? goal.get("goalState").getAsString() : "";
                if (goalState.equals(targetState) && goal.has(goalType)) {
                    JsonObject goalDetails = goal.getAsJsonObject(goalType);
                    for (JsonElement conditionElement : evaluateConditions) {
                        String searchCondition = conditionElement.getAsString();
                        if (goalDetails.has(searchCondition)) {
                            JsonElement conditionValue = goalDetails.get(searchCondition);
                            if (!conditionValue.isJsonNull()) {
                                JsonArray goalConditions = conditionValue.getAsJsonArray();
                                matchConditions(goal, goalType, searchCondition, goalConditions);
                            }
                        }
                    }
                }
            }
        }
    }

    private void matchConditions(JsonObject goal, String goalType, String searchCondition, JsonArray goalConditions) {
        // Determine if the playerState matches any conditions that would make the player fail their research
        for (JsonElement goalCondition : goalConditions) {
            JsonObject conditionParams = goalCondition.getAsJsonObject();

            // Optionally check if the player's last npc target was the goal target
            Boolean engagedWithTarget = (conditionParams.has("engagedWithTarget") && !conditionParams.get("engagedWithTarget").isJsonNull()) ?
                    conditionParams.get("engagedWithTarget").getAsBoolean() : null;
            String lastTargetName = (playerState.has("lastTargetName") && !playerState.get("lastTargetName").isJsonNull()) ?
                    playerState.get("lastTargetName").getAsString() : null;
            String targetStartName = (goal.has("npcTarget")) ? goal.get("npcTarget").getAsString() : "";
            boolean engagementRequiredAndPassing = Boolean.TRUE.equals(engagedWithTarget) && (Objects.equals(lastTargetName, targetStartName));
            if (!Boolean.TRUE.equals(engagedWithTarget)) engagementRequiredAndPassing = true;

            // If a tracked value is saved in an array, retrieve the value using an index key
            String receivedValue = "null";
            if ((conditionParams.has("arrayKey") && !conditionParams.get("arrayKey").isJsonNull()) &&
                conditionParams.has("indexKey") && !conditionParams.get("indexKey").isJsonNull()) {
                String arrayKey = conditionParams.get("arrayKey").getAsString();
                String indexKey = conditionParams.get("indexKey").getAsString();
                if (playerState.has(arrayKey) && playerState.get(arrayKey).isJsonArray()) {
                    JsonArray searchArray = playerState.get(arrayKey).getAsJsonArray();
                    for (int i = 0; i < searchArray.size(); i++) {
                        String element = searchArray.get(i).getAsString();
                        if (element.equals(indexKey)) {
                            receivedValue = playerState.get(searchCondition).getAsJsonArray().get(i).getAsString();
                            break;
                        }
                    }
                }
            // If a tracked value is saved in an object, retrieve the value by traversing the path structure
            } else if (conditionParams.has("pathKey") && !conditionParams.get("pathKey").isJsonNull()) {
                String jsonPath = conditionParams.get("pathKey").getAsString();
                String[] pathSegments = jsonPath.split("\\.");
                if (playerState.has(searchCondition) && playerState.get(searchCondition).isJsonObject()) {
                    JsonElement traversedElement = playerState.get(searchCondition).getAsJsonObject();
                    for (String segment : pathSegments) {
                        if (segment.contains("[")) {
                            String key = segment.substring(0, segment.indexOf("["));
                            int segmentIndex = Integer.parseInt(segment.substring(segment.indexOf("[") + 1, segment.indexOf("]")));
                            traversedElement = traversedElement.getAsJsonObject().get(key).getAsJsonArray().get(segmentIndex);
                        } else {
                            traversedElement = traversedElement.getAsJsonObject().get(segment);
                        }
                    }
                    if (traversedElement != null && !traversedElement.isJsonNull()) receivedValue = GSON.toJson(traversedElement);
                }
            // Otherwise retrieve the value as a string
            } else {
                receivedValue = (playerState.has(searchCondition) && !playerState.get(searchCondition).isJsonNull()) ?
                        playerState.get(searchCondition).getAsString() : "null";
            }

            // If we're comparing arrays, set flags as needed so values can be matched safely
            JsonArray targetValues = null;
            JsonArray receivedValues = null;
            boolean receivedisArray = false;
            boolean targetIsArray = conditionParams.has("match") && conditionParams.get("match").isJsonArray();
            if (targetIsArray) targetValues = conditionParams.get("match").getAsJsonArray();

            String targetValue = (targetIsArray) ? "null" : conditionParams.get("match").getAsString();
            try { receivedisArray = GSON.fromJson(receivedValue, JsonArray.class).isJsonArray(); } catch (Exception e) {}
            if (receivedisArray) receivedValues = GSON.fromJson(receivedValue, JsonArray.class).getAsJsonArray();

            // Compare the received and target values to determine if the player failed their goal
            boolean goalMatched = false;
            String op = (conditionParams.has("op") && !conditionParams.get("op").isJsonNull()) ? conditionParams.get("op").getAsString() : null;
            if (op != null) {
                switch (op) {
                    case ("=="): goalMatched = receivedValue.equals(targetValue); break;
                    case ("!="): goalMatched = !receivedValue.equals(targetValue); break;
                    case ("startsWith"): goalMatched = receivedValue.startsWith(targetValue); break;
                    case ("matches"): goalMatched = receivedValue.matches(targetValue); break;
                    case (">"): goalMatched = Integer.parseInt(receivedValue) > Integer.parseInt(targetValue); break;
                    case ("<"): goalMatched = Integer.parseInt(receivedValue) < Integer.parseInt(targetValue); break;
                    case (">="): goalMatched = Integer.parseInt(receivedValue) >= Integer.parseInt(targetValue); break;
                    case ("<="): goalMatched = Integer.parseInt(receivedValue) <= Integer.parseInt(targetValue); break;
                    case ("contains"):
                        // Process the comparison as an array or string depending on the received and target gamestates
                        if (targetValues != null && targetValues.isJsonArray()) {
                            for (JsonElement arrayValue : targetValues) {
                                if (receivedValue.equals(arrayValue.getAsString())) { goalMatched = true; break; }
                            }
                        } else if (receivedValues != null && receivedValues.isJsonArray()) {
                            for (JsonElement arrayValue : receivedValues) {
                                if (arrayValue.getAsString().equals(targetValue)) { goalMatched = true; break; }
                            }
                        } else {
                            goalMatched = receivedValue.contains(targetValue);
                        }
                    break;
                    case ("doesNotContain"):
                        // Process the comparison as an array or string depending on the received and target gamestates
                        if (targetValues != null && targetValues.isJsonArray()) {
                            for (JsonElement arrayValue : targetValues) {
                                goalMatched = true;
                                if (receivedValue.equals(arrayValue.getAsString())) { goalMatched = false; break; }
                            }
                        } else if (receivedValues != null && receivedValues.isJsonArray()) {
                            for (JsonElement arrayValue : receivedValues) {
                                goalMatched = true;
                                if (arrayValue.getAsString().equals(targetValue)) { goalMatched = false; break; }
                            }
                        } else {
                            goalMatched = !receivedValue.contains(targetValue);
                        }
                    break;
                }
            }

            // Optionally update the players location
            if (conditionParams.has("updateLocation") && !conditionParams.get("updateLocation").isJsonNull()) {
                if (playerState.has("lastLocation")) playerState.add("lastLocation", utils.getPlayerLocation());
            }

            // Optionally check if the player is within a target boundary
            boolean boundsReq = conditionParams.has("regionBounds") || conditionParams.has("coordBounds");
            boolean failInBounds = boundsReq && conditionParams.has("failInBounds");
            if (boundsReq && goalMatched && !playerInBounds(conditionParams) && !failInBounds) goalMatched = false;
            if (boundsReq && goalMatched && playerInBounds(conditionParams) && failInBounds) goalMatched = false;

            // Optionally reset a tracked condition
            if (conditionParams.has("resetCondition") && !conditionParams.get("resetCondition").isJsonNull()) {
                boolean resetCondition = conditionParams.get("resetCondition").getAsBoolean();
                if (resetCondition) playerState.add(searchCondition, null);
            }

            // Advance the goal if any matching conditions are found
            if (goalMatched && engagementRequiredAndPassing) {
                if (goalType.equals("goalStart")) { goalStart(goal, conditionParams); break; }
                if (goalType.equals("goalCheckpoint")) goalCheckpoint(goal, conditionParams);
                if (goalType.equals("goalLost")) goalLost(goal, conditionParams);
                if (goalType.equals("goalComplete")) { goalComplete(goal, conditionParams); break; }
            }
        }
    }

    private void goalStart(JsonObject goal, JsonObject conditionParams) {
        // Set the goal state to "started"
        String goalState = (goal.has("goalState") && !goal.get("goalState").isJsonNull()) ? goal.get("goalState").getAsString() : "";
        String npcName = (playerState.has("lastTargetName") && !playerState.get("lastTargetName").isJsonNull()) ? playerState.get("lastTargetName").getAsString() : "";
        goal.addProperty("goalState", "started");
        goal.addProperty("npcTarget", npcName);

        // Reset the playerState and tracked stats
        resetPlayerState();
        boolean restartTracking = true;
        trackStatChanges(restartTracking);

        // Notify the player they've started the research task
        if (npcRequiredAndValid(goal) && conditionParams.has("notify") && conditionParams.get("notify").isJsonArray() && !goalState.equals("started")) {
            JsonArray startNotify = conditionParams.get("notify").getAsJsonArray();
            if (startNotify.isJsonArray()) utils.sendLocalChatMsg(startNotify);
        }
    }

    private void goalLost(JsonObject goal, JsonObject conditionParams) {
        // Return if there are goal lost checkpoints and they are not completed
        if (checkpointsPresent(goal, conditionParams)) return;

        // Set the goal back to open and reset all checkpoints
        goal.addProperty("goalState", "open");
        resetCheckpoints(goal, conditionParams);

        // Reset the playerState and tracked stats
        resetPlayerState();
        boolean restartTracking = true;
        trackStatChanges(restartTracking);

        // Notify the player they've failed the research task
        if (npcRequiredAndValid(goal) && conditionParams.has("notify") && conditionParams.get("notify").isJsonArray()) {
            JsonArray lostNotify = conditionParams.get("notify").getAsJsonArray();
            if (lostNotify.isJsonArray()) utils.sendLocalChatMsg(lostNotify);
        }

        // Clear and invalidate all targets
        if (!conditionParams.has("noInvalidate")) {
            String npcInst = (playerState.has("lastTargetInstance") && !playerState.get("lastTargetInstance").isJsonNull()) ? playerState.get("lastTargetInstance").getAsString() : "";
            if (goal.has("invalidTargets") && goal.get("invalidTargets").isJsonArray()) {
                JsonArray invalidTargets = goal.get("invalidTargets").getAsJsonArray();
                invalidTargets.add(new JsonPrimitive(npcInst));
                goal.add("invalidTargets", invalidTargets);
            }
        }
        if (goal.has("npcTarget")) goal.addProperty("npcTarget", "");
        if (goal.has("validTargets")) goal.add("validTargets", new JsonArray());
        if (goal.has("validTargetsDmg")) goal.add("validTargetsDmg", new JsonArray());
    }

    private void goalCheckpoint(JsonObject goal, JsonObject conditionParams) {
        // Return if the target requires minimum damage and the player dealt less than required
        if (invalidDamage(goal, conditionParams)) return;

        // If the checkpoint can only be updated once, do not update it again
        if (conditionParams.has("updateOnce") && !conditionParams.get("updateOnce").isJsonNull()) return;

        // Determine how the checkpoint will be updated and update it
        String checkpointRef = (conditionParams.has("cpRef") && !conditionParams.get("cpRef").isJsonNull()) ? conditionParams.get("cpRef").getAsString() : null;
        String checkpointOp = (conditionParams.has("cpOp") && !conditionParams.get("cpOp").isJsonNull()) ? conditionParams.get("cpOp").getAsString() : null;
        String valueType = (conditionParams.has("valueType") && !conditionParams.get("valueType").isJsonNull()) ? conditionParams.get("valueType").getAsString() : null;
        String baseType = (conditionParams.has("baseType") && !conditionParams.get("baseType").isJsonNull()) ? conditionParams.get("baseType").getAsString() : null;
        int valueInt = 0; String valueString = ""; int baseInt = 0; JsonArray baseArray = new JsonArray();
        if (valueType != null && baseType != null && checkpointOp != null && checkpointRef != null && goal.has(checkpointRef)) {
            switch (valueType) {
                case ("int"): valueInt = conditionParams.get("cpValue").getAsInt(); break;
                case ("string"): valueString = conditionParams.get("cpValue").getAsString(); break;
            }
            switch (baseType) {
                case ("int"): baseInt = goal.get(checkpointRef).getAsInt(); break;
                case ("array"): baseArray = goal.get(checkpointRef).getAsJsonArray(); break;
            }
            switch (checkpointOp) {
                case ("+"): goal.addProperty(checkpointRef, baseInt+valueInt); break;
                case ("-"): goal.addProperty(checkpointRef, baseInt-valueInt); break;
                case ("pushInt"): baseArray.add(valueInt); goal.add(checkpointRef, baseArray); break;
                case ("pushStr"): baseArray.add(valueString); goal.add(checkpointRef, baseArray); break;
            }
        }

        // Set a flag if the checkpoint should only update once, and it hasn't been updated yet
        if (conditionParams.has("updateOnce") && conditionParams.get("updateOnce").isJsonNull()) conditionParams.addProperty("updateOnce", true);

        // Notify the player that they've made progress on their research task
        if (checkpointRef != null && conditionParams.has("notify") && conditionParams.get("notify").isJsonArray()) {
            String updatedValue = goal.get(checkpointRef).toString();
            JsonArray lostNotify = conditionParams.get("notify").getAsJsonArray();
            // $cpRef is a control word that is replaced with the saved checkpoint value
            // $plural is a control word that picks the singular or plural form of a word if $cpRef is = 1 or != 1
            JsonArray updatedNotify = new JsonArray();
            for (int i = 0; i < lostNotify.size(); i++) {
                String message;
                JsonObject msgObj = new JsonObject();
                lostNotify.get(i).getAsJsonObject().entrySet().forEach(entry -> msgObj.add(entry.getKey(), entry.getValue()));
                if (msgObj.has("message") && !msgObj.get("message").isJsonNull()) {
                    message = msgObj.get("message").getAsString();
                    if (isParsableAsInt(updatedValue)) {
                        message = message.replace("$cpRef", updatedValue);
                        String pluralKey = (Integer.parseInt(updatedValue) == 1) ? "$1" : "$2";
                        message = message.replaceFirst("\\$plural\\[(.*):(.*)]", pluralKey);
                    }
                    msgObj.addProperty("message", message);
                    updatedNotify.add(msgObj);
                }
            }
            if (lostNotify.isJsonArray()) utils.sendLocalChatMsg(updatedNotify);
        }
    }

    private void goalComplete(JsonObject goal, JsonObject conditionParams) {
        // Return if the target requires minimum damage and the player dealt less than required
        if (invalidDamage(goal, conditionParams)) return;

        // Return if there are goal complete checkpoints and they are not completed
        if (checkpointsPresent(goal, conditionParams)) return;

        // Set the goal to completed and reset all checkpoints
        goal.addProperty("goalState", "completed");
        resetCheckpoints(goal, conditionParams);

        // Send a notification in the chatbox that the player completed their goal
        if (conditionParams.has("notify") && conditionParams.get("notify").isJsonArray()) {
            JsonArray completeNotify = conditionParams.get("notify").getAsJsonArray();
            utils.sendLocalChatMsg(completeNotify);
        }

        // Process any post-completion events
        int eventWidget = -1;
        List<String> messageCollector = plugin.getMessageCollector();
        JsonObject completeRequest = (conditionParams.has("request") && !conditionParams.get("request").isJsonNull()) ?
                conditionParams.get("request").getAsJsonObject() : new JsonObject();
        JsonArray eventParameters = (completeRequest.has("param") && !completeRequest.get("param").isJsonNull()) ?
                completeRequest.get("param").getAsJsonArray() : new JsonArray();
        if (eventParameters.size() > 0) {
            for (JsonElement param : eventParameters) {
                if (param.getAsString().startsWith("widgetInfo")) eventWidget = Integer.parseInt(completeRequest.get("event").getAsString());
            }
        }
        String goalName = (goal.has("goalName")) ? goal.get("goalName").getAsString() : "";
        String eventType = (goal.has("eventType")) ? goal.get("eventType").getAsString() : "";
        utils.processEvent(goalName, eventType, eventParameters, eventWidget, messageCollector, playerState, -1);
    }

    private boolean checkpointsPresent(JsonObject goal, JsonObject conditionParams) {
        boolean checkpointsComplete = false;
        String checkpointRef = (conditionParams.has("cpRef") && !conditionParams.get("cpRef").isJsonNull()) ? conditionParams.get("cpRef").getAsString() : null;
        String checkpointOp = (conditionParams.has("cpOp") && !conditionParams.get("cpOp").isJsonNull()) ? conditionParams.get("cpOp").getAsString() : null;
        String goalType = (conditionParams.has("goalType") && !conditionParams.get("goalType").isJsonNull()) ? conditionParams.get("goalType").getAsString() : null;
        String baseType = (conditionParams.has("baseType") && !conditionParams.get("baseType").isJsonNull()) ? conditionParams.get("baseType").getAsString() : null;
        int baseInt = 0; JsonArray baseArray = new JsonArray(); int goalInt = 0; String goalString = ""; JsonArray goalArray = new JsonArray();
        if (goal.has(checkpointRef) && goalType != null && baseType != null && checkpointOp != null && checkpointRef != null) {
            switch (baseType) {
                case ("int"): baseInt = goal.get(checkpointRef).getAsInt(); break;
                case ("array"): baseArray = goal.get(checkpointRef).getAsJsonArray(); break;
            }
            switch (goalType) {
                case ("int"): goalInt = conditionParams.get("cpGoal").getAsInt(); break;
                case ("string"): goalString = conditionParams.get("cpGoal").getAsString(); break;
                case ("array"): goalArray = conditionParams.get("cpGoal").getAsJsonArray(); break;
            }
            if (baseType.equals("int") && goalType.equals("int")) {
                switch (checkpointOp) {
                    case ("=="): checkpointsComplete = baseInt == goalInt; break;
                    case ("!="): checkpointsComplete = baseInt != goalInt; break;
                    case (">"): checkpointsComplete = baseInt > goalInt; break;
                    case ("<"): checkpointsComplete = baseInt < goalInt; break;
                    case (">="): checkpointsComplete = baseInt >= goalInt; break;
                    case ("<="): checkpointsComplete = baseInt <= goalInt; break;
                }
            }
            // Process array logic
            Set<String> baseSet = new HashSet<>();
            Set<String> goalSet = new HashSet<>();
            for (JsonElement element : baseArray) baseSet.add(element.getAsString());
            for (JsonElement element : goalArray) goalSet.add(element.getAsString());
            if (baseType.equals("array") && goalType.equals("string")) {
                switch (checkpointOp) {
                    case ("contains"): checkpointsComplete = baseSet.contains(goalString); break;
                    case ("doesNotContain"): checkpointsComplete = !baseSet.contains(goalString); break;
                }
            }
            if (baseType.equals("array") && goalType.equals("array")) {
                switch (checkpointOp) {
                    case ("containsAll"): checkpointsComplete = baseSet.containsAll(goalSet); break;
                    case ("doesNotContainAll"): checkpointsComplete = !baseSet.containsAll(goalSet); break;
                }
            }
        }
        return (!checkpointsComplete && checkpointOp != null);
    }

    private boolean invalidDamage(JsonObject goal, JsonObject conditionParams) {
        boolean validTargetInvalidDamage = false;
        if (conditionParams.has("enforceMinDmg") && !conditionParams.get("enforceMinDmg").isJsonNull()) {
            String npcInst = (playerState.has("lastTargetInstance") && !playerState.get("lastTargetInstance").isJsonNull()) ? playerState.get("lastTargetInstance").getAsString() : "";
            if (goal.has("validTargets") && goal.get("validTargets").isJsonArray()) {
                JsonArray validTargets = goal.getAsJsonArray("validTargets");
                JsonArray validTargetsDmg = goal.has("validTargetsDmg") ? goal.getAsJsonArray("validTargetsDmg") : null;
                int damageRequired = conditionParams.get("enforceMinDmg").getAsInt();
                if (validTargetsDmg != null) {
                    for (int i = 0; i < validTargets.size(); i++) {
                        if (validTargets.get(i).getAsString().equals(npcInst)) {
                            int damageOnTarget = validTargetsDmg.get(i).getAsInt();
                            if (damageOnTarget < damageRequired) {
                                validTargetInvalidDamage = true;
                                break;
                            }
                        }
                    }
                }
                if (validTargetInvalidDamage && conditionParams.has("minDmgNotify") && conditionParams.get("minDmgNotify").isJsonArray()) {
                    JsonArray minDmgNotifyNotify = conditionParams.get("minDmgNotify").getAsJsonArray();
                    utils.sendLocalChatMsg(minDmgNotifyNotify);
                }
            }
        }
        return validTargetInvalidDamage;
    }

    private void resetPlayerState() {
        if (playerState.has("lastGearAndItems")) playerState.add("lastGearAndItems", utils.getPlayerItems());
        if (playerState.has("lastItemPickup")) playerState.add("lastItemPickup", null);
        if (playerState.has("lastScriptPostFired")) playerState.add("lastScriptPostFired", null);
        if (playerState.has("lastChatMessage")) playerState.add("lastChatMessage", null);
        if (playerState.has("lastRegionTransition")) playerState.add("lastRegionTransition", null);
        if (playerState.has("teleportQueued")) playerState.add("teleportQueued", null);
    }

    private void resetCheckpoints(JsonObject goal, JsonObject conditionParams) {
        // Reset all checkpoint references back to their default values
        if (conditionParams.has("cpRef") && conditionParams.has("cpResetRef")) {
            String checkpointRef = (!conditionParams.get("cpRef").isJsonNull()) ? conditionParams.get("cpRef").getAsString() : null;
            String resetRef = (!conditionParams.get("cpResetRef").isJsonNull()) ? conditionParams.get("cpResetRef").getAsString() : null;
            JsonArray resetArray = (goal.has(resetRef) && goal.get(resetRef).isJsonArray()) ? new JsonArray() : null;
            if (checkpointRef != null && resetRef != null) {
                if (resetArray != null) goal.add(checkpointRef, resetArray);
                else goal.add(checkpointRef, goal.get(resetRef));
            }
        }
        // Reset all "Update once" checkpoints
        JsonObject goalCheckpoint = goal.getAsJsonObject("goalCheckpoint");
        if (goalCheckpoint != null) {
            for (int i = 0; i < evaluateConditions.size(); i++) {
                String conditionType = evaluateConditions.get(i).getAsString();
                if (goalCheckpoint.has(conditionType) && !goalCheckpoint.get(conditionType).isJsonNull()) {
                    JsonArray cpConditions = goalCheckpoint.getAsJsonArray(conditionType);
                    for (JsonElement cpCondition : cpConditions) {
                        JsonObject checkpoint = cpCondition.getAsJsonObject();
                        if (checkpoint.has("updateOnce")) checkpoint.add("updateOnce", null);
                    }
                }
            }
        }
    }

    // Prevents NPCs being reevaluated if they were invalidated
    private boolean npcRequiredAndValid(JsonObject goal) {
        boolean validTarget = true;
        String npcInst = (playerState.has("lastTargetInstance") && !playerState.get("lastTargetInstance").isJsonNull()) ? playerState.get("lastTargetInstance").getAsString() : "";
        if (goal.has("validateNPC") && goal.has("invalidTargets") && goal.get("invalidTargets").isJsonArray()) {
            JsonArray invalidTargets = goal.get("invalidTargets").getAsJsonArray();
            for (JsonElement targetElem : invalidTargets) {
                String target = targetElem.getAsString();
                if (npcInst.equals(target)) validTarget = false; break;
            }
        }
        return validTarget;
    }

    private static boolean isParsableAsInt(String str) {
        try { Integer.parseInt(str); return true; }
        catch (NumberFormatException e) { return false; }
    }

    private boolean playerInBounds(JsonObject conditionParams) {
        // Get the last location saved in the playerState
        JsonObject lastLocation;
        if (playerState.has("lastLocation") && playerState.get("lastLocation").isJsonObject()) lastLocation = playerState.get("lastLocation").getAsJsonObject();
        else return false;

        // Determine if the player is in a target map region
        boolean playerInRegion = false;
        boolean regionBounds = conditionParams.has("regionBounds") && conditionParams.get("regionBounds").isJsonArray() && conditionParams.get("regionBounds").isJsonArray();
        if (regionBounds && lastLocation.has("regions") && lastLocation.get("regions").isJsonArray()) {
            JsonArray validRegions = conditionParams.get("regionBounds").getAsJsonArray();
            JsonArray playerRegions = lastLocation.get("regions").getAsJsonArray();
            for (JsonElement vRegion : validRegions) {
                int validRegion = vRegion.getAsInt();
                for (JsonElement pRegion : playerRegions) {
                    int playerRegion = pRegion.getAsInt();
                    if (playerRegion == validRegion) {
                        playerInRegion = true;
                        break;
                    }
                }
                if (playerInRegion) break;
            }
        }

        // Determine if the player is in a target coordinate range and plane
        Integer playerWorldX = (lastLocation.has("worldX") && !lastLocation.get("worldX").isJsonNull()) ? lastLocation.get("worldX").getAsInt() : null;
        Integer playerWorldY = (lastLocation.has("worldY") && !lastLocation.get("worldY").isJsonNull()) ? lastLocation.get("worldY").getAsInt() : null;
        Integer playerRegionX = (lastLocation.has("regionX") && !lastLocation.get("regionX").isJsonNull()) ? lastLocation.get("regionX").getAsInt() : null;
        Integer playerRegionY = (lastLocation.has("regionY") && !lastLocation.get("regionY").isJsonNull()) ? lastLocation.get("regionY").getAsInt() : null;
        boolean playerInCoords = false;
        boolean coordBounds = conditionParams.has("coordBounds") && conditionParams.get("coordBounds").isJsonArray() && conditionParams.get("coordBounds").isJsonArray();
        if (coordBounds && ((playerWorldX != null && playerWorldY !=null) || (playerRegionX != null && playerRegionY !=null))) {
            JsonArray validCoords = conditionParams.get("coordBounds").getAsJsonArray();
            for (JsonElement coordArea : validCoords) {
                if (coordArea.isJsonArray()) {
                    JsonArray coordGroup = coordArea.getAsJsonArray();
                    if (coordGroup.size() == 6) {
                        Integer playerX = null; Integer playerY = null;
                        int playerPlane = (lastLocation.has("plane") && !lastLocation.get("plane").isJsonNull()) ? lastLocation.get("plane").getAsInt() : 0;
                        int x1 = coordGroup.get(0).getAsInt(); int y1 = coordGroup.get(1).getAsInt();
                        int x2 = coordGroup.get(2).getAsInt(); int y2 = coordGroup.get(3).getAsInt();
                        int plane = coordGroup.get(4).getAsInt();
                        if (coordGroup.get(5).getAsString().equals("world")) {
                            playerX = playerWorldX;
                            playerY = playerWorldY;
                        }
                        if (coordGroup.get(5).getAsString().equals("region")) {
                            playerX = playerRegionX;
                            playerY = playerRegionY;
                        }
                        if (playerX != null && playerX >= x1 && playerX <= x2 && playerY != null && playerY <= y1 && playerY >= y2 && playerPlane == plane) {
                            playerInCoords = true;
                            break;
                        }
                    }
                }
            }
        }
        return playerInRegion || playerInCoords;
    }
}
