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

import com.google.gson.JsonElement;
import com.pokescape.PokescapeConfig;
import com.pokescape.PokescapePlugin;
import com.pokescape.ui.PokescapePanel;
import com.pokescape.util.Utils;
import com.pokescape.util.PokeScapeGoals;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.common.base.Strings;
import net.runelite.api.Client;
import net.runelite.client.game.ItemStack;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.ImageUtil;
import static net.runelite.http.api.RuneLiteAPI.GSON;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import okhttp3.Response;
import okhttp3.MultipartBody;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.ConnectException;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.List;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PokescapeClient {
    private @Inject Client client;
    private @Inject OkHttpClient okHttpClient;
    private @Inject OkHttpClient sseClient;
    private @Inject DrawManager drawManager;
    private @Inject PokescapePlugin plugin;
    private @Inject PokescapeConfig config;
    private @Inject formatBody format;
    private @Inject Utils utils;
    private @Inject PokeScapeGoals goals;
    private @Inject PokescapePanel panel;

    private static final String API_ENDPOINT = "https://api.pokescape.com";
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private int reconnectTimeout = 0;
    private static Call sseHandler;
    private JsonObject cacheManifest;

    public void status(PokescapePanel pokescapePanel) {
        panel = pokescapePanel;
        getRequest("/status");
    }

    public void profile(PokescapePanel pokescapePanel, PokeScapeGoals pokescapeGoals) {
        panel = pokescapePanel;
        goals = pokescapeGoals;
        postBody postBody = new postBody();
        String rsn = client.getLocalPlayer().getName();
        String clientHash = Long.toString(client.getAccountHash());
        postBody.setRsn(rsn);
        postBody.setClientHash(clientHash);
        postBody.setPluginVersion(PokescapeConfig.PLUGIN_VERSION);
        postRequest(postBody, "/profile");
    }

    // Initialize a call instance that doesn't timeout for SSE
    public void initSSE() {
        shutdownSSE();
        sseClient = sseClient.newBuilder().connectTimeout(0, TimeUnit.MINUTES).readTimeout(0, TimeUnit.MINUTES).writeTimeout(0, TimeUnit.MINUTES).build();
        connectToSSE(API_ENDPOINT+"/sse", sseClient);
    }

    // Null out the sseHandler so scheduleReconnect doesn't reopen the connection
    public void shutdownSSE() {
        if (sseHandler != null) { sseHandler.cancel(); sseHandler = null; }
    }

    // If the SSE connection is interuppted: First attempt to reconnect instantly, then every 5 seconds
    private void scheduleReconnect(String url, OkHttpClient sseClient) {
        if (sseHandler != null) scheduler.schedule(() -> connectToSSE(url, sseClient), reconnectTimeout, TimeUnit.SECONDS);
        reconnectTimeout = 5;
    }

    private void connectToSSE(String url, OkHttpClient sseClient) {
        // Build the SSE connection request
        Request request = new Request.Builder().url(url)
                .addHeader("Accept", "text/event-stream")
                .addHeader("Client-Hash", Long.toString(client.getAccountHash()))
                .build();

        // Create a new call with an event listener and start the connection
        sseHandler = sseClient.newCall(request);
        sseHandler.enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    log.debug("Failed to connect to SSE endpoint. HTTP status code: " + response.code());
                    scheduleReconnect(url, sseClient);
                    return;
                }
                // Read SSE events
                try (ResponseBody body = response.body()) {
                    if (body != null) readSSEEvents(body);
                } catch (Exception e) {
                    try { if (!e.getMessage().equals("Socket closed")) { scheduleReconnect(url, sseClient); }
                    } catch (Exception f) { scheduleReconnect(url, sseClient); }
                }
            }
            @Override
            public void onFailure(Call call, IOException e) {
                scheduleReconnect(url, sseClient);
            }
        });
    }

    private void readSSEEvents(ResponseBody body) throws IOException {
        // Read SSE events in chunks
        reconnectTimeout = 0;
        BufferedSource source = body.source();
        Buffer buffer = new Buffer();
        while (!source.exhausted()) {
            source.read(buffer, 2048);
            String event = buffer.readUtf8();
            JsonObject eventBody;
            try { eventBody = GSON.fromJson(event, JsonObject.class); }
            catch (Exception e) { eventBody = new JsonObject(); }

            // Validate the message and respond to events
            if (validMessage(eventBody)) {
                if (eventBody.has("fetchProfile") && !eventBody.get("fetchProfile").isJsonNull()) {
                    boolean fetchProfile = eventBody.has("fetchProfile") && !eventBody.get("fetchProfile").isJsonNull() && eventBody.get("fetchProfile").getAsBoolean();
                    if (fetchProfile) profile(panel, goals);
                }
                if (eventBody.has("sync") && !eventBody.get("sync").isJsonNull()) {
                    boolean syncProfile = eventBody.has("sync") && !eventBody.get("sync").isJsonNull() && eventBody.get("sync").getAsBoolean();
                    if (syncProfile) sync();
                }
            }
        }
    }

    private boolean validMessage(JsonObject eventBody) {
        boolean messageValid = false;
        if (eventBody.has("id") && eventBody.get("id").isJsonArray()) {
            JsonArray messageIDs = eventBody.get("id").getAsJsonArray();
            for (JsonElement id : messageIDs) {
                if (id.getAsString().equals(Long.toString(client.getAccountHash()))) messageValid = true;
                if (id.getAsString().equals("all")) messageValid = true;
            }
        }
        return messageValid;
    }

    public void sync() {
        postBody postBody = new postBody();
        String rsn = client.getLocalPlayer().getName();
        String clientHash = Long.toString(client.getAccountHash());
        postBody.setRsn(rsn);
        postBody.setClientHash(clientHash);
        postBody.setPluginVersion(PokescapeConfig.PLUGIN_VERSION);
        postRequest(postBody, "/sync");
    }

    public void validateMinigame(PokescapePanel pokescapePanel, String validationData) {
        panel = pokescapePanel;
        postBody postBody = format.minigame(validationData);
        postRequest(postBody, "/validation");
    }

    public void gameEvent(String eventName, String eventType, List<String> messageCollector, JsonObject eventInfo, JsonObject recentActivities, Integer spriteID) {
        postBody postBody = format.event(eventName, eventType, messageCollector, eventInfo, recentActivities, spriteID);
        postRequest(postBody, "/event");
    }

    public void loot(String activity, String name, Integer id, Collection<ItemStack> items, List<String> messageCollector, JsonObject recentActivities) {
        postBody postBody = format.loot(activity, name, id, items, messageCollector, recentActivities);
        postRequest(postBody, "/loot");
    }

    private void getRequest(String route) {
        // Validate the webhook path and url
        String url = API_ENDPOINT;
        if (Strings.isNullOrEmpty(url) || route == null) return;
        else url = API_ENDPOINT + route;
        HttpUrl u = HttpUrl.parse(url);
        if (u == null) { log.info("Malformed webhook url {}", url); return; }

        // Build the request
        Request request = new Request.Builder().url(url).build();

        // Send the request
        okHttpClient.newCall(request).enqueue(new Callback() {
            // If the request doesn't hit the server, update the server status to "Unreachable" in the side panel
            @Override
            public void onFailure(Call call, IOException e) {
                try { throw new ConnectException("Unreachable"); }
                catch (ConnectException c) {
                    panel.setServerStatusText(0);
                    panel.setServerAnnoucement(0, "");
                    panel.setPokescapeTeam("", "");
                    panel.setTotalLevel("");
                    panel.setDexCount("");
                }
            }
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    JsonObject responseBody;
                    try { responseBody = GSON.fromJson(response.body() != null ? response.body().string() : null, JsonObject.class); }
                    catch (Exception e) { responseBody = new JsonObject(); }

                    // Update the panel to reflect the server status
                    if (response.code() / 100 != 2) panel.setServerStatusText(2);
                    else panel.setServerStatusText(1);

                    // Update the panel with any annoucements and/or special status from the server
                    if (responseBody.has("serverMessage") && !responseBody.get("serverMessage").isJsonNull()) {
                        String serverMessage = responseBody.get("serverMessage").getAsString();
                        int serverStatus = (responseBody.has("serverStatus") && !responseBody.get("serverStatus").isJsonNull()) ? responseBody.get("serverStatus").getAsInt() : 1;
                        if (serverMessage != null) panel.setServerAnnoucement(serverStatus, serverMessage);
                    }
                } catch (Exception e) {
                    log.debug("Error processing response");
                } finally {
                    response.close();
                }
            }
        });
    }

    private void postRequest(postBody postBody, String route) {
        postRequest(postBody, null, route);
    }

    private void postRequest(postBody postBody, byte[] screenshot, String route) {
        // Validate the webhook path and url
        String url = API_ENDPOINT;
        if (Strings.isNullOrEmpty(url) || route == null) return;
        else url = API_ENDPOINT + route;
        HttpUrl u = HttpUrl.parse(url);
        if (u == null) { log.info("Malformed webhook url {}", url); return; }

        // Build the payload body
        MultipartBody.Builder requestBodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("payload_json", GSON.toJson(postBody));

        // Add formdata to the payload if a screenshot was taken
        if (screenshot != null) {
            requestBodyBuilder.addFormDataPart("file", "image.png",
                    RequestBody.create(MediaType.parse("image/png"), screenshot));
        }

        // Build the request
        MultipartBody requestBody = requestBodyBuilder.build();
        Request request = new Request.Builder().url(url).post(requestBody).build();

        // Send the request
        okHttpClient.newCall(request).enqueue(new Callback() {
            // If the request doesn't hit the server, clear the server+team info in the panel
            @Override
            public void onFailure(Call call, IOException e) {
                try { throw new ConnectException("Unreachable"); }
                catch (ConnectException c) {
                    panel.setServerStatusText(0);
                    panel.setServerAnnoucement(0, "");
                    panel.setPokescapeTeam("", "");
                    panel.setTotalLevel("");
                    panel.setDexCount("");
                }
            }
            // If the request is successful, parse the response
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    JsonObject responseBody;
                    try { responseBody = GSON.fromJson(response.body() != null ? response.body().string() : null, JsonObject.class);}
                    catch (Exception e) { responseBody = new JsonObject(); }

                    // Update the server status in the panel to reflect the success/failure of the request
                    if (response.code() / 100 != 2) {
                        panel.setServerStatusText(2);
                        panel.setPokescapeTeam("", "");
                        panel.setTotalLevel("");
                        panel.setDexCount("");
                    } else panel.setServerStatusText(1);

                    // Update the server annoucement in the side panel
                    if (responseBody.has("serverMessage") && !responseBody.get("serverMessage").isJsonNull()) {
                        String serverMessage = responseBody.get("serverMessage").getAsString();
                        int serverStatus = (responseBody.has("serverStatus") && !responseBody.get("serverStatus").isJsonNull()) ? responseBody.get("serverStatus").getAsInt() : 1;
                        if (serverMessage != null) panel.setServerAnnoucement(serverStatus, serverMessage);
                    }

                    // If there's a help message for the player, print it to their chatbox
                    if (responseBody.has("localChatMsg") && !responseBody.get("localChatMsg").isJsonNull()) {
                        JsonArray messageStructure = responseBody.get("localChatMsg").getAsJsonArray();
                        if (messageStructure.isJsonArray()) utils.sendLocalChatMsg(messageStructure);
                    }

                    // Reinitialize SSE
                    if (responseBody.has("initSSE") && !responseBody.get("initSSE").isJsonNull()) {
                        if (sseHandler != null) sseHandler.cancel();
                        sseHandler = null;
                        initSSE();
                    }

                    // Update the side panel with the player's team info and verification status
                    if (route.equals("/profile") && responseBody.has("teamName") && responseBody.has("teamColor")) {
                        if (!responseBody.get("teamName").isJsonNull() && !responseBody.get("teamColor").isJsonNull()) {
                            String teamName = responseBody.get("teamName").getAsString();
                            String teamColor = responseBody.get("teamColor").getAsString();
                            panel.setPokescapeTeam(teamName, teamColor);
                        }
                        if (responseBody.has("verification") && !responseBody.get("verification").isJsonNull()) {
                            JsonObject verification = responseBody.get("verification").getAsJsonObject();
                            boolean tempoVerification = verification.get("tempoross").getAsBoolean();
                            boolean gotrVerification = verification.get("gotr").getAsBoolean();
                            panel.setTemporossVerification(tempoVerification);
                            panel.setGotrVerification(gotrVerification);
                        }
                        if (responseBody.has("goals") && !responseBody.get("goals").isJsonNull()) {
                            // TODO: Set the research description in the panel
                            goals.setGoals(responseBody.get("goals").getAsJsonArray());
                        }
                        if (responseBody.has("totalLevel") && !responseBody.get("totalLevel").isJsonNull()) {
                            String totalLevel = responseBody.get("totalLevel").getAsString();
                            panel.setTotalLevel(totalLevel);
                        }
                        if (responseBody.has("dexCount") && !responseBody.get("dexCount").isJsonNull()) {
                            String dexCount = responseBody.get("dexCount").getAsString();
                            panel.setDexCount(dexCount);
                        }
                        if (responseBody.has("eventPassword") && !responseBody.get("eventPassword").isJsonNull())
                            config.setEventPassword(responseBody.get("eventPassword").getAsString());
                        // Request a sync if the manifest is missing or old
                        if (responseBody.has("manifest") && !responseBody.get("manifest").isJsonNull()) {
                            if (cacheManifest == null || !cacheManifest.toString().equals(responseBody.get("manifest").getAsJsonObject().toString())) {
                                cacheManifest = responseBody.get("manifest").getAsJsonObject();
                                sync();
                            }
                        }
                        // Initialize the SSE connection if it hasn't yet been created
                        if (sseHandler == null) initSSE();
                    }

                    // Update pet and events after a sync
                    if (route.equals("/sync")) {
                        if (responseBody.has("pets") && !responseBody.get("pets").isJsonNull())
                            format.setPets(responseBody.get("pets").getAsJsonObject());
                        if (responseBody.has("events") && !responseBody.get("events").isJsonNull())
                            plugin.setGameEvents(responseBody.get("events").getAsJsonObject());
                        if (responseBody.has("activities") && !responseBody.get("activities").isJsonNull())
                            plugin.setGameActivities(responseBody.get("activities").getAsJsonObject());
                        if (responseBody.has("allowblock") && !responseBody.get("allowblock").isJsonNull())
                            plugin.setAllowBlockList(responseBody.get("allowblock").getAsJsonObject());
                    }

                    // Update the side panel with minigame verification status
                    if (route.equals("/validation") && responseBody.has("activity") && responseBody.has("valid")) {
                        if (!responseBody.get("activity").isJsonNull() && !responseBody.get("valid").isJsonNull()) {
                            String activity = responseBody.get("activity").getAsString();
                            boolean validity = responseBody.get("valid").getAsBoolean();
                            if (activity.equals("tempoross")) panel.setTemporossVerification(validity);
                            if (activity.equals("gotr")) panel.setGotrVerification(validity);
                        }
                    }

                    // When the server successfully validates loot it may send back a 210. This means take a screenshot!
                    if (response.code() == 210) {
                        // Add validation from this response into the body of the screenshot request
                        if (responseBody.has("validEvents") && !responseBody.get("validEvents").isJsonNull()) {
                            postBody.setValidEvents(responseBody.get("validEvents").getAsJsonArray());
                        }
                        // If a delay is specified, wait the delay amount before taking a screenshot. Otherwise, take the screenshot
                        if (responseBody.has("delayScreenshot") && !responseBody.get("delayScreenshot").isJsonNull()) {
                            int delay = responseBody.get("delayScreenshot").getAsInt();
                            scheduler.schedule(() -> requestScreenshot(postBody, route), delay, TimeUnit.MILLISECONDS);
                        } else {
                            requestScreenshot(postBody, route);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Error processing response", e);
                } finally {
                    response.close();
                }
            }
        });
    }

    private void requestScreenshot(postBody postBody, String route) {
        drawManager.requestNextFrameListener(image -> {
            BufferedImage bufferedImage = (BufferedImage) image;
            // Resize the dimensions of the screenshot to 800px before sending it off
            bufferedImage = resizeScreenshot(bufferedImage, 800);
            byte[] imageBytes = null;
            try {
                imageBytes = convertImageToByteArray(bufferedImage);
            } catch (IOException e) {
                log.error("Error converting image to byte array", e);
            }
            if (imageBytes != null) postRequest(postBody, imageBytes, route);
        });
    }

    // Takes the widest dimension of the screenshot and scales it down proportionally
    public static BufferedImage resizeScreenshot(final BufferedImage screenshot, final int maxSize) {
        final Image resizedImg;
        if (screenshot.getWidth() > screenshot.getHeight()) resizedImg = screenshot.getScaledInstance(maxSize, -1, Image.SCALE_SMOOTH);
        else resizedImg = screenshot.getScaledInstance(-1, maxSize, Image.SCALE_SMOOTH);
        return ImageUtil.bufferedImageFromImage(resizedImg);
    }

    private static byte[] convertImageToByteArray(BufferedImage bufferedImage) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", byteArrayOutputStream);
        return byteArrayOutputStream.toByteArray();
    }
}
