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
package com.pokescape.web;

import com.pokescape.PokescapeConfig;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.WorldType;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

@Slf4j
public class formatBody {
    private @Inject Client client;
    private @Inject ItemManager itemManager;
    private @Inject PokescapeConfig config;

    private JsonObject allPets;
    private static final ImmutableSet<String> PET_MESSAGES = ImmutableSet.of(
            "You have a funny feeling like you're being followed.",
            "You feel something weird sneaking into your backpack.",
            "You have a funny feeling like you would have been followed..."
    );

    public void setPets(JsonObject pets) {
        allPets = pets;
    }

    public postBody minigame(String validationData) {
        postBody postBody = new postBody();
        String rsn = client.getLocalPlayer().getName();
        long clienthash = client.getAccountHash();
        int world = client.getWorld();
        EnumSet<WorldType> worldType = client.getWorldType();

        postBody.setRsn(rsn);
        postBody.setClientHash(clienthash);
        postBody.setEventPasswordVisible(eventPasswordVisible());
        postBody.setCurrentWorld(world);
        postBody.setWorldTypes(worldType);
        postBody.setTriggerActivity("validation");
        postBody.setValidationData(validationData);
        return postBody;
    }

    public postBody event(String eventName, List<String> messageCollector, JsonObject eventInfo) {
        postBody postBody = new postBody();
        String rsn = client.getLocalPlayer().getName();
        long clienthash = client.getAccountHash();
        int world = client.getWorld();
        EnumSet<WorldType> worldType = client.getWorldType();

        // Populate body with game messages
        List<String> gameMessages = new ArrayList<>(messageCollector);

        // If other information is present, send it in the request
        if (eventInfo != null && !eventInfo.entrySet().isEmpty()) postBody.setEventInfo(eventInfo);

        postBody.setRsn(rsn);
        postBody.setClientHash(clienthash);
        postBody.setEventPasswordVisible(eventPasswordVisible());
        postBody.setCurrentWorld(world);
        postBody.setWorldTypes(worldType);
        postBody.setTriggerActivity("gameEvent");
        postBody.setGameEvent(eventName);
        postBody.setGameMsg(gameMessages);
        return postBody;
    }

    public postBody loot(String activity, String npcName, Integer npcID, Collection<ItemStack> items, List<String> messageCollector) {
        postBody postBody = new postBody();
        String rsn = client.getLocalPlayer().getName();
        long clienthash = client.getAccountHash();
        int world = client.getWorld();
        EnumSet<WorldType> worldType = client.getWorldType();

        // Populate body with loot info
        List<String> lootName = new ArrayList<>();
        List<Integer> lootID = new ArrayList<>();
        List<Integer> lootQuantity = new ArrayList<>();
        for (ItemStack item : stack(items)) {
            int itemId = item.getId();
            ItemComposition itemComposition = itemManager.getItemComposition(itemId);
            lootName.add(itemComposition.getName());
            lootID.add(itemId);
            lootQuantity.add(item.getQuantity());
        }

        // Populate body with game messages
        List<String> gameMessages = new ArrayList<>(messageCollector);

        // If a pet message is found add the pet item associated with the npc to the loot received
        for (String message : gameMessages) {
            if (PET_MESSAGES.contains(message)) {
                if (allPets != null && allPets.has(npcName)) {
                    JsonObject petInfo = allPets.get(npcName).getAsJsonObject();
                    String petName = petInfo.get("petName").getAsString();
                    Integer petID = petInfo.get("petID").getAsInt();
                    lootName.add(petName);
                    lootID.add(petID);
                    lootQuantity.add(1);
                    break;
                }
            }
        }

        postBody.setRsn(rsn);
        postBody.setClientHash(clienthash);
        postBody.setEventPasswordVisible(eventPasswordVisible());
        postBody.setCurrentWorld(world);
        postBody.setWorldTypes(worldType);
        postBody.setTriggerActivity(activity);
        postBody.setNpcName(npcName);
        postBody.setNpcID(npcID);
        postBody.setLootName(lootName);
        postBody.setLootID(lootID);
        postBody.setLootQuantity(lootQuantity);
        postBody.setGameMsg(gameMessages);
        return postBody;
    }

    private boolean eventPasswordVisible() {
        String boolA = (config.overlayVisibility()) ? "1" : "0";
        String boolB = (!config.eventPassword().isEmpty()) ? "1" : "0";
        int binaryOut = Integer.parseInt(boolA+boolB,2);
        return binaryOut == 3;
    }

    private static Collection<ItemStack> stack(Collection<ItemStack> items) {
        final List<ItemStack> list = new ArrayList<>();
        for (final ItemStack item : items) {
            int quantity = 0;
            for (final ItemStack i : list) {
                if (i.getId() == item.getId()) {
                    quantity = i.getQuantity();
                    list.remove(i);
                    break;
                }
            }
            if (quantity > 0) list.add(new ItemStack(item.getId(), item.getQuantity() + quantity, item.getLocation()));
            else list.add(item);
        }
        return list;
    }
}
