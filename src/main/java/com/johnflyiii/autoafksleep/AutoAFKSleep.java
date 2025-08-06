/*
 * Copyright 2025 John Fly III
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.johnflyiii.autoafksleep;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * AutoAFKSleep - A Minecraft Fabric mod that automatically sleeps when AFK
 * 
 * Features:
 * - Automatic sleeping when night falls if near a bed
 * - Chat monitoring with auto-response to direct messages
 * - Disconnect phrase detection
 * - Configurable failure actions (disconnect, custom command, or nothing)
 * 
 * @author John Fly
 * @version 1.0.1
 */
public class AutoAFKSleep implements ClientModInitializer {
    public static final String MOD_ID = "autoafksleep";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static AutoAFKSleep instance;
    private ModConfig config;
    private int tickCounter = 0;
    private long lastSleepAttempt = 0;
    private static final long SLEEP_ATTEMPT_COOLDOWN = 3000; // 3 seconds
    private long lastChatResponse = 0;
    private static final long CHAT_RESPONSE_COOLDOWN = 30000; // 30 seconds between responses
    
    // Smart timing constants
    private static final int NIGHT_START = 12541;
    private static final int NIGHT_END = 23458;
    private static final int DAY_LENGTH = 24000;
    private static final int TICKS_PER_MINUTE = 1200; // Minecraft minutes (20 ticks = 1 second, 60 seconds = 1 minute)
    private static final int CHECK_INTERVAL_FAR = 600; // 30 seconds when far from night
    private static final int CHECK_INTERVAL_NEAR = 200; // 10 seconds when near night
    private static final int CHECK_INTERVAL_NIGHT = 100; // 5 seconds during night
    private int nextCheckTick = 0;
    
    // Sleep verification tracking
    private BlockPos pendingSleepPos = null;
    private int sleepVerifyTicks = 0;
    private static final int SLEEP_VERIFY_DELAY = 40; // 2 seconds
    
    // Interaction constants
    private static final double MAX_INTERACT_DISTANCE = 2.0; // Minecraft's bed interaction distance
    private static final double MAX_INTERACT_DISTANCE_SQ = MAX_INTERACT_DISTANCE * MAX_INTERACT_DISTANCE;
    private static final int BED_SEARCH_RADIUS = 3; // Search slightly beyond reach to find all nearby beds
    
    // Keybinding
    private static KeyBinding configKeyBinding;
    
    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("AutoAFK Sleep mod initializing...");
        
        // Load configuration
        config = ModConfig.load();
        LOGGER.info("Configuration loaded successfully");
        
        // Register tick event
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        
        // Register chat events - both GAME (server messages) and CHAT (player messages)
        ClientReceiveMessageEvents.GAME.register(this::onChatMessage);
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            // Forward chat messages to our handler with overlay=false since chat messages aren't overlays
            onChatMessage(message, false);
        });
        
        // Register commands
        registerCommands();
        
        // Register keybinding
        configKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.autoafksleep.config",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K, // Default to K key
            "category.autoafksleep"
        ));
        
        // Add keybind listener
        ClientTickEvents.END_CLIENT_TICK.register(this::onKeyPress);
        
        LOGGER.info("AutoAFK Sleep mod initialized successfully");
        LOGGER.info("Mod is currently: {}", config.modEnabled ? "ENABLED" : "DISABLED");
        LOGGER.info("Using smart time checking - checks more frequently near night time");
    }
    
    private void onClientTick(MinecraftClient client) {
        if (!config.modEnabled || client.player == null || client.world == null) {
            return;
        }
        
        tickCounter++;
        
        // Handle pending sleep verification
        if (pendingSleepPos != null) {
            sleepVerifyTicks++;
            if (sleepVerifyTicks >= SLEEP_VERIFY_DELAY) {
                verifySleepAttempt(client);
                pendingSleepPos = null;
                sleepVerifyTicks = 0;
            }
            return; // Don't do other checks while verifying sleep
        }
        
        // Only check when we've reached the next check time
        if (tickCounter < nextCheckTick) {
            return;
        }
        
        // Check dimension - beds explode in Nether and End
        String dimension = client.world.getRegistryKey().getValue().toString();
        if (dimension.contains("the_nether") || dimension.contains("the_end")) {
            LOGGER.debug("In dimension {}, sleeping disabled (beds explode!)", dimension);
            nextCheckTick = tickCounter + CHECK_INTERVAL_FAR;
            return;
        }
        
        long timeOfDay = client.world.getTimeOfDay() % DAY_LENGTH;
        
        // Calculate time until night
        int timeUntilNight = 0;
        if (timeOfDay < NIGHT_START) {
            timeUntilNight = NIGHT_START - (int)timeOfDay;
        } else if (timeOfDay > NIGHT_END) {
            timeUntilNight = (DAY_LENGTH - (int)timeOfDay) + NIGHT_START;
        }
        
        // Determine next check interval based on time
        if (timeOfDay >= NIGHT_START && timeOfDay <= NIGHT_END) {
            // It's night time
            if (!client.player.isSleeping() && System.currentTimeMillis() - lastSleepAttempt >= SLEEP_ATTEMPT_COOLDOWN) {
                LOGGER.debug("Night time ({}), attempting to sleep", timeOfDay);
                tryToSleep(client);
            }
            nextCheckTick = tickCounter + CHECK_INTERVAL_NIGHT;
        } else if (timeUntilNight < TICKS_PER_MINUTE * 2) {
            // Less than 2 Minecraft minutes until night
            LOGGER.debug("Night approaching in {} ticks, checking more frequently", timeUntilNight);
            nextCheckTick = tickCounter + CHECK_INTERVAL_NEAR;
        } else {
            // Far from night
            nextCheckTick = tickCounter + CHECK_INTERVAL_FAR;
        }
    }
    
    private void tryToSleep(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        
        Vec3d playerEyePos = player.getEyePos();
        BlockPos playerPos = player.getBlockPos();
        
        // Collect all reachable beds
        List<BlockPos> reachableBeds = new ArrayList<>();
        
        for (int x = -BED_SEARCH_RADIUS; x <= BED_SEARCH_RADIUS; x++) {
            for (int y = -BED_SEARCH_RADIUS; y <= BED_SEARCH_RADIUS; y++) {
                for (int z = -BED_SEARCH_RADIUS; z <= BED_SEARCH_RADIUS; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    BlockState state = client.world.getBlockState(pos);
                    
                    if (state.getBlock() instanceof BedBlock) {
                        // Check if bed is within reach distance from player's eye position
                        Vec3d bedCenter = Vec3d.ofCenter(pos);
                        double distanceSq = playerEyePos.squaredDistanceTo(bedCenter);
                        
                        if (distanceSq <= MAX_INTERACT_DISTANCE_SQ) {
                            reachableBeds.add(pos);
                        }
                    }
                }
            }
        }
        
        if (reachableBeds.isEmpty()) {
            LOGGER.info("No beds within reach (max {} blocks). Move closer to a bed!", MAX_INTERACT_DISTANCE);
            handleSleepFailure(client);
            return;
        }
        
        // Sort by distance (closest first)
        reachableBeds.sort(Comparator.comparingDouble(pos -> 
            playerEyePos.squaredDistanceTo(Vec3d.ofCenter(pos))));
        
        LOGGER.debug("Found {} reachable bed(s)", reachableBeds.size());
        
        // Try beds in order of distance
        for (BlockPos bedPos : reachableBeds) {
            double distance = Math.sqrt(playerEyePos.squaredDistanceTo(Vec3d.ofCenter(bedPos)));
            if (attemptToUseBed(client, bedPos)) {
                LOGGER.info("Attempting to sleep in bed at {} (distance: {}m)", 
                    bedPos, String.format("%.1f", distance));
                return;
            }
        }
        
        // Couldn't use any bed
        LOGGER.info("Failed to use any of {} reachable beds", reachableBeds.size());
        handleSleepFailure(client);
    }
    
    private boolean attemptToUseBed(MinecraftClient client, BlockPos bedPos) {
        try {
            ClientPlayerEntity player = client.player;
            if (player == null) return false;
            
            lastSleepAttempt = System.currentTimeMillis();
            
            // Create interaction parameters
            Vec3d hitVec = Vec3d.ofCenter(bedPos);
            BlockHitResult hitResult = new BlockHitResult(
                hitVec,
                Direction.UP,
                bedPos,
                false
            );
            
            // Attempt to interact with the bed
            client.interactionManager.interactBlock(
                player,
                Hand.MAIN_HAND,
                hitResult
            );
            
            // Schedule sleep verification
            pendingSleepPos = bedPos;
            sleepVerifyTicks = 0;
            LOGGER.debug("Scheduled sleep verification for bed at {}", bedPos);
            
            return true;
        } catch (Exception e) {
            LOGGER.error("Error attempting to use bed: ", e);
            return false;
        }
    }
    
    private void verifySleepAttempt(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        
        if (player.isSleeping()) {
            LOGGER.info("Successfully sleeping!");
        } else {
            LOGGER.info("Sleep verification failed - player not in bed after {}ms", SLEEP_VERIFY_DELAY * 50);
            handleSleepFailure(client);
        }
    }
    
    private void handleSleepFailure(MinecraftClient client) {
        switch (config.sleepFailureAction) {
            case DISCONNECT:
                LOGGER.info("Disconnecting from server due to sleep failure");
                disconnect(client, "unable to sleep");
                break;
                
            case CUSTOM_COMMAND:
                if (config.customCommand != null && !config.customCommand.isEmpty()) {
                    LOGGER.info("Executing custom command: {}", config.customCommand);
                    sendCommand(client, config.customCommand);
                }
                break;
                
            case NO_ACTION:
                // Silent - no action taken
                break;
        }
    }
    
    private void onChatMessage(Text message, boolean overlay) {
        LOGGER.info("Chat message received: {} (overlay: {})", message.getString(), overlay);
        
        if (!config.modEnabled || overlay) {
            LOGGER.info("Ignoring message - mod disabled: {}, overlay: {}", !config.modEnabled, overlay);
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        
        String fullMessage = message.getString();
        String messageText = fullMessage.toLowerCase();
        String playerName = client.player.getName().getString();
        String playerNameLower = playerName.toLowerCase();
        LOGGER.info("Processing message: '{}' (player: {})", fullMessage, playerName);
        
        // ONLY ignore our own auto-response messages - nothing else!
        boolean isOurAutoResponse = false;
        if (config.autoRespond && config.responseMessage != null) {
            isOurAutoResponse = fullMessage.contains(config.responseMessage);
        }
        // Also ignore the disconnect instruction message
        if (!isOurAutoResponse && config.disconnectPhraseEnabled && config.disconnectPhrase != null) {
            isOurAutoResponse = fullMessage.contains("To force me to disconnect say:") && 
                               fullMessage.contains(config.disconnectPhrase);
        }
        
        if (isOurAutoResponse) {
            LOGGER.info("Ignoring our own auto-response message");
            return;
        }
        
        // Detect message type
        boolean isDirectMessage = false;
        boolean isAboutSleep = false;
        boolean mentionsPlayer = false;
        
        // Common direct message patterns - be more inclusive
        if (messageText.contains(" whispers to you:") ||
            messageText.contains(" whispers:") ||
            messageText.contains("/msg " + playerNameLower) ||
            messageText.contains("/tell " + playerNameLower) ||
            messageText.contains("/w " + playerNameLower) ||
            messageText.contains(" -> " + playerNameLower) ||
            messageText.contains(" -> me]") ||  // Format: [[S] DocSplinters -> me]
            messageText.contains("@" + playerNameLower) ||  // Format: @NiceAndEasy
            messageText.startsWith(playerNameLower + ",") ||
            messageText.startsWith(playerNameLower + ":") ||
            messageText.endsWith(" " + playerNameLower) ||  // "hey NiceAndEasy"
            messageText.endsWith(" " + playerNameLower + "?")) {  // "are you there NiceAndEasy?"
            isDirectMessage = true;
        }
        
        // Check if message is about sleeping or AFK
        if (messageText.contains("sleep") || 
            messageText.contains("bed") || 
            messageText.contains("night") ||
            messageText.contains("afk") ||
            messageText.contains("away") ||
            messageText.contains("there") ||
            messageText.contains("hello") ||
            messageText.contains("wake")) {
            isAboutSleep = true;
        }
        
        // Check if player is mentioned (but not in a system message)
        if (messageText.contains(playerNameLower) && 
            !messageText.contains("joined the game") &&
            !messageText.contains("left the game") &&
            !messageText.contains("has made the advancement")) {
            mentionsPlayer = true;
        }
        
        // Log message classification for debugging
        LOGGER.info("Message classification - Direct: {}, About Sleep: {}, Mentions Player: {}", 
            isDirectMessage, isAboutSleep, mentionsPlayer);
        
        // Check for disconnect phrase in ANY message (including player's own for testing!)
        if (config.disconnectPhraseEnabled && config.disconnectPhrase != null && !config.disconnectPhrase.isEmpty()) {
            String disconnectPhraseLower = config.disconnectPhrase.toLowerCase();
            LOGGER.info("Checking for disconnect phrase '{}' in message", disconnectPhraseLower);
            
            if (messageText.contains(disconnectPhraseLower)) {
                LOGGER.info("Disconnect phrase '{}' detected in message: {}", 
                    config.disconnectPhrase, fullMessage);
                
                // Send acknowledgment before disconnecting
                if (config.autoRespond) {
                    sendChatMessage(client, "Disconnecting due to AFK phrase. Goodbye!");
                    
                    // Small delay to let message send
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }
                
                disconnect(client, "disconnect phrase detected");
                return;
            }
        }
        
        // Auto-respond logic - respond to ANY message that mentions us or sleep/afk keywords
        if (config.autoRespond && config.responseMessage != null && !config.responseMessage.isEmpty()) {
            boolean shouldRespond = false;
            
            // Respond if: direct message, mentions player, or contains sleep/afk keywords
            if (isDirectMessage || mentionsPlayer || isAboutSleep) {
                // Don't respond to system messages
                if (!isSystemMessage(messageText)) {
                    shouldRespond = true;
                    LOGGER.info("Will respond - Direct: {}, Mentions: {}, AboutSleep: {}", 
                        isDirectMessage, mentionsPlayer, isAboutSleep);
                }
            }
            
            if (shouldRespond) {
                // Check cooldown to prevent spam
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastChatResponse < CHAT_RESPONSE_COOLDOWN) {
                    LOGGER.debug("Skipping response due to cooldown ({} seconds remaining)", 
                        (CHAT_RESPONSE_COOLDOWN - (currentTime - lastChatResponse)) / 1000);
                    return;
                }
                
                // Add a small delay to seem more human-like
                try {
                    Thread.sleep(1000 + (long)(Math.random() * 2000)); // 1-3 second delay
                } catch (InterruptedException e) {
                    // Ignore
                }
                
                sendChatMessage(client, config.responseMessage);
                
                // If disconnect phrase is enabled, send a second message with instructions
                if (config.disconnectPhraseEnabled && config.disconnectPhrase != null && !config.disconnectPhrase.isEmpty()) {
                    try {
                        Thread.sleep(500); // Small delay between messages
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                    sendChatMessage(client, "To force me to disconnect say: " + config.disconnectPhrase);
                }
                
                lastChatResponse = currentTime;
            }
        }
    }
    
    private boolean isSystemMessage(String message) {
        return message.contains("has made the advancement") ||
               message.contains("was slain by") ||
               message.contains("fell from") ||
               message.contains("drowned") ||
               message.contains("died") ||
               message.contains("joined the game") ||
               message.contains("left the game") ||
               message.contains("[server]") ||
               message.contains("[system]");
    }
    
    private void sendChatMessage(MinecraftClient client, String message) {
        try {
            if (client.player != null) {
                client.player.networkHandler.sendChatMessage(message);
            }
        } catch (Exception e) {
            LOGGER.error("Error sending chat message: ", e);
        }
    }
    
    private void sendCommand(MinecraftClient client, String command) {
        try {
            if (client.player != null) {
                // Ensure command starts with /
                String cmd = command.startsWith("/") ? command : "/" + command;
                client.player.networkHandler.sendChatCommand(cmd.substring(1));
            }
        } catch (Exception e) {
            LOGGER.error("Error sending command: ", e);
        }
    }
    
    private void disconnect(MinecraftClient client, String reason) {
        try {
            client.execute(() -> {
                if (client.world != null && client.getNetworkHandler() != null) {
                    LOGGER.info("Disconnecting from server: {}", reason);
                    client.getNetworkHandler().getConnection().disconnect(
                        Text.literal("Disconnected by AutoAFKSleep - " + reason)
                    );
                }
            });
        } catch (Exception e) {
            LOGGER.error("Error disconnecting from server: ", e);
        }
    }
    
    private void onKeyPress(MinecraftClient client) {
        if (configKeyBinding.wasPressed() && client.currentScreen == null) {
            client.setScreen(new ConfigScreen(null));
        }
    }
    
    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("autoafksleep")
                .executes(context -> {
                    context.getSource().sendFeedback(Text.literal("AutoAFK Sleep v1.0.1 - Use /autoafksleep help for commands"));
                    return 1;
                })
                .then(ClientCommandManager.literal("help")
                    .executes(context -> {
                        context.getSource().sendFeedback(Text.literal("=== AutoAFK Sleep Commands ==="));
                        context.getSource().sendFeedback(Text.literal("/autoafksleep enable - Enable the mod"));
                        context.getSource().sendFeedback(Text.literal("/autoafksleep disable - Disable the mod"));
                        context.getSource().sendFeedback(Text.literal("/autoafksleep toggle - Toggle mod on/off"));
                        context.getSource().sendFeedback(Text.literal("/autoafksleep status - Show current status"));
                        context.getSource().sendFeedback(Text.literal("/autoafksleep config <option> <value> - Configure mod"));
                        context.getSource().sendFeedback(Text.literal("/autoafksleep ui - Open configuration GUI"));
                        return 1;
                    }))
                .then(ClientCommandManager.literal("enable")
                    .executes(context -> {
                        config.modEnabled = true;
                        saveConfig();
                        context.getSource().sendFeedback(Text.literal("AutoAFK Sleep enabled"));
                        return 1;
                    }))
                .then(ClientCommandManager.literal("disable")
                    .executes(context -> {
                        config.modEnabled = false;
                        saveConfig();
                        context.getSource().sendFeedback(Text.literal("AutoAFK Sleep disabled"));
                        return 1;
                    }))
                .then(ClientCommandManager.literal("toggle")
                    .executes(context -> {
                        config.modEnabled = !config.modEnabled;
                        saveConfig();
                        context.getSource().sendFeedback(Text.literal("AutoAFK Sleep " + (config.modEnabled ? "enabled" : "disabled")));
                        return 1;
                    }))
                .then(ClientCommandManager.literal("status")
                    .executes(context -> {
                        context.getSource().sendFeedback(Text.literal("=== AutoAFK Sleep Status ==="));
                        context.getSource().sendFeedback(Text.literal("Mod: " + (config.modEnabled ? "Enabled" : "Disabled")));
                        context.getSource().sendFeedback(Text.literal("Sleep Failure Action: " + config.sleepFailureAction));
                        context.getSource().sendFeedback(Text.literal("Auto Respond: " + (config.autoRespond ? "Enabled" : "Disabled")));
                        context.getSource().sendFeedback(Text.literal("Disconnect Phrase: " + (config.disconnectPhraseEnabled ? "'" + config.disconnectPhrase + "'" : "Disabled")));
                        context.getSource().sendFeedback(Text.literal("Bed Interaction Range: 2 blocks (fixed)"));
                        
                        // Show timing info if in game
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client.world != null) {
                            long timeOfDay = client.world.getTimeOfDay() % DAY_LENGTH;
                            String timeStatus;
                            if (timeOfDay >= NIGHT_START && timeOfDay <= NIGHT_END) {
                                timeStatus = "Night (sleepable)";
                            } else {
                                int timeUntilNight = timeOfDay < NIGHT_START ? 
                                    NIGHT_START - (int)timeOfDay : 
                                    (DAY_LENGTH - (int)timeOfDay) + NIGHT_START;
                                timeStatus = String.format("Day (night in %d ticks / %.1f seconds)", 
                                    timeUntilNight, timeUntilNight / 20.0f);
                            }
                            context.getSource().sendFeedback(Text.literal("Current time: " + timeOfDay + " - " + timeStatus));
                        }
                        
                        return 1;
                    }))
                .then(ClientCommandManager.literal("ui")
                    .executes(context -> {
                        MinecraftClient client = MinecraftClient.getInstance();
                        client.execute(() -> {
                            client.setScreen(new ConfigScreen(client.currentScreen));
                        });
                        return 1;
                    }))
                .then(ClientCommandManager.literal("config")
                    .then(ClientCommandManager.literal("sleepFailureAction")
                        .then(ClientCommandManager.argument("action", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                builder.suggest("disconnect");
                                builder.suggest("command");
                                builder.suggest("nothing");
                                return builder.buildFuture();
                            })
                            .executes(context -> {
                                String action = StringArgumentType.getString(context, "action");
                                try {
                                    config.sleepFailureAction = ModConfig.SleepFailureAction.valueOf(action.toUpperCase());
                                    saveConfig();
                                    context.getSource().sendFeedback(Text.literal("Sleep failure action set to: " + action));
                                } catch (IllegalArgumentException e) {
                                    context.getSource().sendError(Text.literal("Invalid action. Use: disconnect, command, or nothing"));
                                }
                                return 1;
                            })))
                    .then(ClientCommandManager.literal("customCommand")
                        .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
                            .executes(context -> {
                                config.customCommand = StringArgumentType.getString(context, "command");
                                saveConfig();
                                context.getSource().sendFeedback(Text.literal("Custom command set to: " + config.customCommand));
                                return 1;
                            })))
                    .then(ClientCommandManager.literal("autoRespond")
                        .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
                            .executes(context -> {
                                config.autoRespond = BoolArgumentType.getBool(context, "enabled");
                                saveConfig();
                                context.getSource().sendFeedback(Text.literal("Auto respond " + (config.autoRespond ? "enabled" : "disabled")));
                                return 1;
                            })))
                    .then(ClientCommandManager.literal("responseMessage")
                        .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                            .executes(context -> {
                                config.responseMessage = StringArgumentType.getString(context, "message");
                                saveConfig();
                                context.getSource().sendFeedback(Text.literal("Response message set to: " + config.responseMessage));
                                return 1;
                            })))
                    .then(ClientCommandManager.literal("disconnectPhrase")
                        .then(ClientCommandManager.argument("phrase", StringArgumentType.greedyString())
                            .executes(context -> {
                                config.disconnectPhrase = StringArgumentType.getString(context, "phrase");
                                saveConfig();
                                context.getSource().sendFeedback(Text.literal("Disconnect phrase set to: " + config.disconnectPhrase));
                                return 1;
                            })))
            ));
        });
    }
    
    public static AutoAFKSleep getInstance() {
        return instance;
    }
    
    public ModConfig getConfig() {
        return config;
    }
    
    public void saveConfig() {
        config.save();
        LOGGER.info("Configuration saved");
    }
}