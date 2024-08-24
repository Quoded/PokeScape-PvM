package com.pokescape.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pokescape.PokescapePlugin;
import net.runelite.client.callback.ClientThread;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import static net.runelite.http.api.RuneLiteAPI.GSON;

@Slf4j
public class goalUtils {
    private @Inject ClientThread clientThread;
    private @Inject PokescapePlugin plugin;
    private @Inject Utils utils;


    public String retrieveStateValue(JsonObject conditionParams, String searchCondition, JsonObject playerState) {
        String receivedValue = "null";
        // If a tracked value is saved in an array, retrieve the value using an index key
        if ((conditionParams.has("arrayKey") && !conditionParams.get("arrayKey").isJsonNull()) && conditionParams.has("indexKey") && !conditionParams.get("indexKey").isJsonNull()) {
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
            if (playerState.has(searchCondition) && playerState.get(searchCondition).isJsonObject()) {
                JsonObject playerStateObj = playerState.get(searchCondition).getAsJsonObject();
                JsonElement objValue = getObjectValue(playerStateObj, jsonPath);
                if (objValue != null) receivedValue = GSON.toJson(objValue);
            }
            // Otherwise retrieve the value as a string
        } else {
            receivedValue = (playerState.has(searchCondition) && !playerState.get(searchCondition).isJsonNull()) ? playerState.get(searchCondition).getAsString() : "null";
        }
        return receivedValue;
    }

    private JsonElement getObjectValue(JsonObject searchObject, String jsonPath) {
        String[] pathSegments = jsonPath.split("\\.");
        JsonElement traversedElement = searchObject;
        for (String segment : pathSegments) {
            if (segment.contains("[")) {
                String key = segment.substring(0, segment.indexOf("["));
                int segmentIndex = Integer.parseInt(segment.substring(segment.indexOf("[") + 1, segment.indexOf("]")));
                traversedElement = traversedElement.getAsJsonObject().get(key).getAsJsonArray().get(segmentIndex);
            } else {
                traversedElement = traversedElement.getAsJsonObject().get(segment);
            }
        }
        if (traversedElement != null && !traversedElement.isJsonNull()) return traversedElement;
        else return null;
    }

    public boolean compareValues(String op, String receivedValue, JsonArray receivedValues, String targetValue, JsonArray targetValues) {
        boolean valuesMatch = false;
        if (op != null) {
            switch (op) {
                case ("=="): valuesMatch = receivedValue.equals(targetValue); break;
                case ("!="): valuesMatch = !receivedValue.equals(targetValue); break;
                case ("startsWith"): valuesMatch = receivedValue.startsWith(targetValue); break;
                case ("matches"): valuesMatch = receivedValue.matches(targetValue); break;
                case (">"):
                    try { Integer.parseInt(receivedValue); } catch (NumberFormatException e) { break; }
                    valuesMatch = Integer.parseInt(receivedValue) > Integer.parseInt(targetValue); break;
                case ("<"):
                    try { Integer.parseInt(receivedValue); } catch (NumberFormatException e) { break; }
                    valuesMatch = Integer.parseInt(receivedValue) < Integer.parseInt(targetValue); break;
                case (">="):
                    try { Integer.parseInt(receivedValue); } catch (NumberFormatException e) { break; }
                    valuesMatch = Integer.parseInt(receivedValue) >= Integer.parseInt(targetValue); break;
                case ("<="):
                    try { Integer.parseInt(receivedValue); } catch (NumberFormatException e) { break; }
                    valuesMatch = Integer.parseInt(receivedValue) <= Integer.parseInt(targetValue); break;
                case ("contains"):
                    // Process the comparison as an array or string depending on the received and target gamestates
                    if (targetValues != null && targetValues.isJsonArray()) {
                        for (JsonElement arrayValue : targetValues) {
                            if (receivedValue.equals(arrayValue.getAsString())) { valuesMatch = true; break; }
                        }
                    } else if (receivedValues != null && receivedValues.isJsonArray()) {
                        for (JsonElement arrayValue : receivedValues) {
                            if (arrayValue.getAsString().equals(targetValue)) { valuesMatch = true; break; }
                        }
                    } else {
                        valuesMatch = receivedValue.contains(targetValue);
                    }
                    break;
                case ("doesNotContain"):
                    // Process the comparison as an array or string depending on the received and target gamestates
                    if (targetValues != null && targetValues.isJsonArray()) {
                        for (JsonElement arrayValue : targetValues) {
                            valuesMatch = true;
                            if (receivedValue.equals(arrayValue.getAsString())) { valuesMatch = false; break; }
                        }
                    } else if (receivedValues != null && receivedValues.isJsonArray()) {
                        for (JsonElement arrayValue : receivedValues) {
                            valuesMatch = true;
                            if (arrayValue.getAsString().equals(targetValue)) { valuesMatch = false; break; }
                        }
                    } else {
                        valuesMatch = !receivedValue.contains(targetValue);
                    }
                    break;
            }
        }
        return valuesMatch;
    }

    public String distanceFromLocation(String baseCoordHint, String baseCoordFound, Integer playerX, Integer playerY, int x1, int y1, int x2, int y2) {
        // Return if the player is within the target region
        if (playerX >= x1 && playerX <= x2 && playerY <= y1 && playerY >= y2) return baseCoordFound;

        // Get the player's distance and vector from the target
        int xCenter = (x1 + x2) / 2; int yCenter = (y1 + y2) / 2;
        double distance = Math.sqrt(Math.pow(playerX - xCenter, 2) + Math.pow(playerY - yCenter, 2));
        double vector = Math.toDegrees(Math.atan2(playerY - yCenter, playerX - xCenter));

        // Calculate the distance and location and return the hint
        String distanceText = getDistanceText(distance);
        String direction = getDirection(vector);
        return baseCoordHint + direction + ". " + distanceText;
    }

    private static String getDistanceText(double distance) {
        if (distance >= 500) return "The signal is incredibly faint";
        if (distance >= 350) return "The signal is faint";
        if (distance >= 250) return "The signal is weak";
        if (distance >= 150) return "The signal is moderate";
        if (distance >= 100) return "The signal is strong";
        if (distance >= 50) return "The signal is intense";
        return "The signal is incredibly intense";
    }

    private static String getDirection(double vector) {
        List<String> direction = new ArrayList<>();
        if (vector >= 30 && vector <= 150) direction.add("south"); // North of target
        if (vector >= -150 && vector <= -30) direction.add("north"); // South of target
        if ((vector >= -180 && vector <= -120) || (vector >= 120 && vector <= 180)) direction.add("east"); // West of target
        if (vector >= -60 && vector <= 60) direction.add("west"); // East of target
        return String.join("", direction).toLowerCase();
    }

    public void processContainerDeltas(JsonObject playerState) {
        JsonObject lastGearAndItems = playerState.getAsJsonObject("lastGearAndItems");
        // Set the state of the containers pre-update tick
        JsonArray invPreUpdate = lastGearAndItems.get("inventory").getAsJsonObject().get("itemID").getAsJsonArray();
        JsonArray invQtsPreUpdate = lastGearAndItems.get("inventory").getAsJsonObject().get("itemQuantity").getAsJsonArray();
        JsonArray eqpPreUpdate = lastGearAndItems.get("equipment").getAsJsonObject().get("itemID").getAsJsonArray();
        JsonArray eqpQtsPreUpdate = lastGearAndItems.get("equipment").getAsJsonObject().get("itemQuantity").getAsJsonArray();
        clientThread.invokeLater(() -> {
            // Set the state of the containers post-update tick
            JsonObject postGearAndItems = utils.getPlayerItems();
            JsonArray invPostUpdate = postGearAndItems.get("inventory").getAsJsonObject().get("itemID").getAsJsonArray();
            JsonArray invQtsPostUpdate = postGearAndItems.get("inventory").getAsJsonObject().get("itemQuantity").getAsJsonArray();
            JsonArray eqpPostUpdate = postGearAndItems.get("equipment").getAsJsonObject().get("itemID").getAsJsonArray();
            JsonArray eqpQtsPostUpdate = postGearAndItems.get("equipment").getAsJsonObject().get("itemQuantity").getAsJsonArray();

            // Return an object for each container that details items, added/removed, and quantities after update
            JsonObject invUpdate = containerDelta("inventory", invPreUpdate, invPostUpdate, invQtsPreUpdate, invQtsPostUpdate);
            JsonObject eqpUpdate = containerDelta("equipment", eqpPreUpdate, eqpPostUpdate, eqpQtsPreUpdate, eqpQtsPostUpdate);

            // Process each container event
            JsonObject containerEvents = plugin.getContainerEvents();
            containerEvents.keySet().forEach(keyName -> {
                // Get the arguments for the container event
                JsonObject containerObj = containerEvents.get(keyName).getAsJsonObject();
                JsonArray eventItems = containerObj.has("items") && !containerObj.get("items").isJsonNull() ? containerObj.get("items").getAsJsonArray() : new JsonArray();
                JsonObject eventDetails; try { eventDetails = containerObj.get("param").getAsJsonArray().get(0).getAsJsonObject(); } catch (Exception e) { eventDetails = new JsonObject(); }
                String container = eventDetails.has("container") && !eventDetails.get("container").isJsonNull() ? eventDetails.get("container").getAsString() : null;
                String containerAction = eventDetails.has("containerAction") && !eventDetails.get("containerAction").isJsonNull() ? eventDetails.get("containerAction").getAsString() : null;
                JsonArray containerConditions = eventDetails.has("containerConditions") && eventDetails.get("containerConditions").isJsonArray() ? eventDetails.get("containerConditions").getAsJsonArray() : new JsonArray();
                if (container == null || containerAction == null) return;

                // Fetch the container (inventory or equipment) being evaluated by the event
                JsonObject updatedContainer;
                if ("equipment".equals(container)) updatedContainer = eqpUpdate;
                else updatedContainer = invUpdate;

                // Determine if the container has been updated with any event items
                HashSet<Integer> updateSet = jsonArrayToHashSet(updatedContainer.get(containerAction).getAsJsonArray());
                HashSet<Integer> eventSet = jsonArrayToHashSet(eventItems);

                // Test for other event conditions if the event item(s) match the updated item(s)
                if (!Collections.disjoint(updateSet, eventSet)) {
                    int conditionMetCount = 0;
                    for (int i = 0; i < containerConditions.size(); i++) {
                        // Get the parameters to test the condition match
                        String conKey = containerConditions.get(i).getAsJsonObject().get("key").getAsString();
                        String conOp = containerConditions.get(i).getAsJsonObject().get("op").getAsString();
                        String conTarget = containerConditions.get(i).getAsJsonObject().get("target").getAsString();
                        // Test the condition and increment conditionMetCount if the condition passes
                        JsonElement objValue = getObjectValue(playerState, conKey);
                        String conReceived = (objValue != null && objValue.isJsonPrimitive()) ? objValue.getAsString() : null;
                        if (conReceived != null) conReceived = conReceived.replaceAll("\"", "");
                        if (conReceived == null) conReceived = "null";
                        boolean conMatch = compareValues(conOp, conReceived, new JsonArray(), conTarget, new JsonArray());
                        if (conMatch) conditionMetCount += 1;
                    }
                    // Process the event if all event conditions are met
                    if (containerConditions.size() == conditionMetCount) utils.processEvent(keyName, "gameEvent", new JsonArray(), -1, new ArrayList<>(), playerState, -1);
                }
            });
        });
    }

    private JsonObject containerDelta(String containerName, JsonArray preItems, JsonArray postItems, JsonArray preQtys, JsonArray postQtys) {
        JsonObject conUpdate = new JsonObject();
        Map<Integer, Integer> preItemQtyMap = utils.consolidateStacks(preItems, preQtys);
        Map<Integer, Integer> postItemQtyMap = utils.consolidateStacks(postItems, postQtys);
        JsonArray itemsAdded = new JsonArray();
        JsonArray itemsRemoved = new JsonArray();
        JsonArray qtyAdded = new JsonArray();
        JsonArray qtyRemoved = new JsonArray();
        JsonArray totalQty = new JsonArray();

        // Find items added and removed
        for (int postItem : postItemQtyMap.keySet()) {
            if (!preItemQtyMap.containsKey(postItem)) {
                itemsAdded.add(postItem);
                qtyAdded.add(postItemQtyMap.get(postItem));
                totalQty.add(postItemQtyMap.get(postItem));
            }
        }
        for (int preItem : preItemQtyMap.keySet()) {
            if (!postItemQtyMap.containsKey(preItem)) {
                itemsRemoved.add(preItem);
                qtyRemoved.add(preItemQtyMap.get(preItem));
                totalQty.add(0);
            }
        }

        // Find quantity changes for existing items
        for (int preItem : preItemQtyMap.keySet()) {
            if (postItemQtyMap.containsKey(preItem)) {
                int preQty = preItemQtyMap.get(preItem);
                int postQty = postItemQtyMap.get(preItem);
                int quantityChange = postQty - preQty;
                if (quantityChange > 0) {
                    itemsAdded.add(preItem);
                    qtyAdded.add(Math.abs(quantityChange));
                    totalQty.add(postQty);
                } else if (quantityChange < 0) {
                    itemsRemoved.add(preItem);
                    qtyRemoved.add(Math.abs(quantityChange));
                    totalQty.add(postQty);
                }
            }
        }

        conUpdate.addProperty("container", containerName);
        conUpdate.add("added", itemsAdded);
        conUpdate.add("addedQty", qtyAdded);
        conUpdate.add("removed", itemsRemoved);
        conUpdate.add("removedQty", qtyRemoved);
        conUpdate.add("totalQty", totalQty);
        return conUpdate;
    }

    private static HashSet<Integer> jsonArrayToHashSet(JsonArray jsonArray) {
        HashSet<Integer> hashSet = new HashSet<>();
        for (int i = 0; i < jsonArray.size(); i++) {
            int element = jsonArray.get(i).getAsInt();
            hashSet.add(element);
        }
        return hashSet;
    }
}