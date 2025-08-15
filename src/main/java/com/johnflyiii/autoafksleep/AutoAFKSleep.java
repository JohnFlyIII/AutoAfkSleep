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
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
 * @version 1.2.1
 */
public class AutoAFKSleep implements ClientModInitializer {
    public static final String MOD_ID = "autoafksleep";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static AutoAFKSleep instance;
    private ModConfig config;
    private AutoEat autoEat;
    private int tickCounter = 0;
    private long lastSleepAttempt = 0;
    private long lastChatResponse = 0;
    
    // Time constants
    private static final int NIGHT_START = 12541;
    private static final int NIGHT_END = 23458;
    private static final int DAY_LENGTH = 24000;
    private static final int TICKS_PER_SECOND = 20;
    
    // Fixed timing parameters
    private static final int CHECK_INTERVAL_OTHER_DIMENSION_TICKS = 6000; // 5 minutes in nether/end
    
    // State tracking
    private int nextCheckTick = 0;
    private int consecutiveFailures = 0;
    
    // Performance optimizations
    private final Set<String> sleepKeywords = new HashSet<>();
    private final Set<String> systemMessagePatterns = new HashSet<>();
    private Pattern directMessagePattern;
    private long lastDimensionCheck = 0;
    private String cachedDimension = null;
    
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
        
        // Initialize performance optimizations
        initializePatterns();
        
        // Initialize AutoEat feature
        autoEat = new AutoEat();
        configureAutoEat();
        
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
        LOGGER.info("Using intelligent time-based scheduling - sleeping until night approaches");
    }
    
    private void initializePatterns() {
        // Initialize sleep keywords for faster lookup
        sleepKeywords.add("sleep");
        sleepKeywords.add("bed");
        sleepKeywords.add("night");
        sleepKeywords.add("afk");
        sleepKeywords.add("away");
        sleepKeywords.add("there");
        sleepKeywords.add("hello");
        sleepKeywords.add("wake");
        
        // System message patterns
        systemMessagePatterns.add("has made the advancement");
        systemMessagePatterns.add("was slain by");
        systemMessagePatterns.add("fell from");
        systemMessagePatterns.add("drowned");
        systemMessagePatterns.add("died");
        systemMessagePatterns.add("joined the game");
        systemMessagePatterns.add("left the game");
        systemMessagePatterns.add("[server]");
        systemMessagePatterns.add("[system]");
        
        // Compile regex pattern for direct messages (compiled once for performance)
        directMessagePattern = Pattern.compile(
            ".*(?:whispers?(?:\\s+to\\s+you)?:|/(?:msg|tell|w)\\s+|->\\s*(?:me\\])?|@).*",
            Pattern.CASE_INSENSITIVE
        );
    }
    
    private void onClientTick(MinecraftClient client) {
        if (!config.modEnabled || client.player == null || client.world == null) {
            return;
        }
        
        // Process AutoEat feature
        if (config.autoEatEnabled) {
            autoEat.tick(client);
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
        
        // Check dimension with caching for performance
        if (!isOverworldDimension(client)) {
            nextCheckTick = tickCounter + CHECK_INTERVAL_OTHER_DIMENSION_TICKS;
            return;
        }
        
        long timeOfDay = client.world.getTimeOfDay() % DAY_LENGTH;
        
        // Calculate intelligent delay based on current time
        if (timeOfDay >= NIGHT_START && timeOfDay <= NIGHT_END) {
            // It's night time
            if (!client.player.isSleeping() && System.currentTimeMillis() - lastSleepAttempt >= config.sleepAttemptCooldownSeconds * 1000L) {
                // Check if we've failed too many times
                if (consecutiveFailures >= config.maxConsecutiveFailures) {
                    // Wait until next night
                    int ticksUntilNextNight = calculateTicksUntilNextNight(timeOfDay);
                    nextCheckTick = tickCounter + ticksUntilNextNight;
                    LOGGER.info("Too many failures. Waiting {} seconds until next night", ticksUntilNextNight / TICKS_PER_SECOND);
                } else {
                    LOGGER.debug("Night time ({}), attempting to sleep (attempt {} of {})", 
                        timeOfDay, consecutiveFailures + 1, config.maxConsecutiveFailures);
                    tryToSleep(client);
                    nextCheckTick = tickCounter + config.checkIntervalNightSeconds * TICKS_PER_SECOND;
                }
            } else {
                nextCheckTick = tickCounter + config.checkIntervalNightSeconds * TICKS_PER_SECOND;
            }
        } else {
            // Calculate intelligent delay until night
            int ticksUntilNight = calculateTicksUntilNight(timeOfDay);
            nextCheckTick = tickCounter + ticksUntilNight;
            
            // Reset failure counter during day
            consecutiveFailures = 0;
            
            LOGGER.info("Day time ({}). Next check in {} seconds", 
                timeOfDay, ticksUntilNight / TICKS_PER_SECOND);
        }
    }
    
    private void tryToSleep(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;
        
        List<BlockPos> reachableBeds = findReachableBeds(client, player);
        
        if (reachableBeds.isEmpty()) {
            LOGGER.info("No beds within reach (max {} blocks). Move closer to a bed!", MAX_INTERACT_DISTANCE);
            consecutiveFailures++;
            handleSleepFailure(client);
            return;
        }
        
        LOGGER.debug("Found {} reachable bed(s)", reachableBeds.size());
        
        // Try beds in order of distance (already sorted in findReachableBeds)
        Vec3d playerEyePos = player.getEyePos();
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
        consecutiveFailures++;
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
            consecutiveFailures = 0; // Reset on success
        } else {
            LOGGER.info("Sleep verification failed - player not in bed after {}ms", SLEEP_VERIFY_DELAY * 50);
            consecutiveFailures++;
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
        // Skip overlay messages silently (coordinates, etc.)
        if (overlay) {
            return;
        }
        
        LOGGER.debug("Chat message received: {}", message.getString());
        
        if (!config.modEnabled) {
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
        LOGGER.debug("Processing message: '{}' (player: {})", fullMessage, playerName);
        
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
        
        // Check for direct message patterns (optimized)
        if (isDirectMessageToPlayer(messageText, playerNameLower)) {
            isDirectMessage = true;
        }
        
        // Check if message contains sleep keywords (optimized with Set lookup)
        for (String keyword : sleepKeywords) {
            if (messageText.contains(keyword)) {
                isAboutSleep = true;
                break;
            }
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
                
                // Send acknowledgment before disconnecting (non-blocking)
                if (config.autoRespond) {
                    sendChatMessage(client, "Disconnecting due to AFK phrase. Goodbye!");
                    // Schedule disconnect after message sends
                    CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS).execute(() -> {
                        disconnect(client, "disconnect phrase detected");
                    });
                    return;
                }
                
                // Disconnect already scheduled in callback
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
                long cooldownMs = config.chatResponseCooldownSeconds * 1000L;
                if (currentTime - lastChatResponse < cooldownMs) {
                    LOGGER.debug("Skipping response due to cooldown ({} seconds remaining)", 
                        (cooldownMs - (currentTime - lastChatResponse)) / 1000);
                    return;
                }
                
                // Schedule response with delay (non-blocking)
                scheduleDelayedResponse(client, config.responseMessage, 1000 + (long)(Math.random() * 2000));
                
                lastChatResponse = currentTime;
            }
        }
    }
    
    private boolean isSystemMessage(String message) {
        // Optimized with Set lookup
        for (String pattern : systemMessagePatterns) {
            if (message.contains(pattern)) {
                return true;
            }
        }
        return false;
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
                    context.getSource().sendFeedback(Text.literal("AutoAFK Sleep v1.2.1 - Use /autoafksleep help for commands"));
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
                        context.getSource().sendFeedback(Text.literal("/autoafksleep config autoEat <true/false> - Toggle AutoEat"));
                        context.getSource().sendFeedback(Text.literal("/autoafksleep config autoEatThreshold <1-19> - Set hunger threshold"));
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
                        context.getSource().sendFeedback(Text.literal("AutoEat: " + (config.autoEatEnabled ? "Enabled (threshold: " + config.autoEatHungerThreshold + "/20)" : "Disabled")));
                        
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
                                int nextCheckIn = Math.max(0, nextCheckTick - tickCounter);
                                timeStatus = String.format("Day (night in %.1f seconds, next check in %.1f seconds)", 
                                    timeUntilNight / (float)TICKS_PER_SECOND, nextCheckIn / (float)TICKS_PER_SECOND);
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
                    .then(ClientCommandManager.literal("autoEat")
                        .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
                            .executes(context -> {
                                config.autoEatEnabled = BoolArgumentType.getBool(context, "enabled");
                                saveConfig();
                                context.getSource().sendFeedback(Text.literal("AutoEat " + (config.autoEatEnabled ? "enabled" : "disabled")));
                                return 1;
                            })))
                    .then(ClientCommandManager.literal("autoEatThreshold")
                        .then(ClientCommandManager.argument("threshold", IntegerArgumentType.integer(1, 19))
                            .executes(context -> {
                                config.autoEatHungerThreshold = IntegerArgumentType.getInteger(context, "threshold");
                                saveConfig();
                                context.getSource().sendFeedback(Text.literal("AutoEat hunger threshold set to: " + config.autoEatHungerThreshold + "/20"));
                                return 1;
                            })))
                    .then(ClientCommandManager.literal("autoEatStews")
                        .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
                            .executes(context -> {
                                config.autoEatStews = BoolArgumentType.getBool(context, "enabled");
                                saveConfig();
                                context.getSource().sendFeedback(Text.literal("AutoEat stews/soups " + (config.autoEatStews ? "enabled" : "disabled")));
                                return 1;
                            })))
                    .then(ClientCommandManager.literal("autoEatMinFood")
                        .then(ClientCommandManager.argument("value", IntegerArgumentType.integer(1, 20))
                            .executes(context -> {
                                config.autoEatMinFoodValue = IntegerArgumentType.getInteger(context, "value");
                                saveConfig();
                                context.getSource().sendFeedback(Text.literal("AutoEat minimum food value set to: " + config.autoEatMinFoodValue));
                                return 1;
                            })))
                    .then(ClientCommandManager.literal("autoEatDisconnect")
                        .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
                            .executes(context -> {
                                config.autoEatDisconnectOnNoFood = BoolArgumentType.getBool(context, "enabled");
                                saveConfig();
                                context.getSource().sendFeedback(Text.literal("AutoEat disconnect on no food " + (config.autoEatDisconnectOnNoFood ? "enabled" : "disabled")));
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
        configureAutoEat();
        LOGGER.info("Configuration saved");
    }
    
    private void configureAutoEat() {
        if (autoEat != null) {
            autoEat.setEnabled(config.autoEatEnabled);
            autoEat.setHungerThreshold(config.autoEatHungerThreshold);
            autoEat.setEatStew(config.autoEatStews);
            autoEat.setMinFoodValue(config.autoEatMinFoodValue);
            autoEat.setDisconnectOnNoFood(config.autoEatDisconnectOnNoFood);
        }
    }
    
    // ========== Helper Methods ==========
    
    private boolean isDirectMessageToPlayer(String messageText, String playerNameLower) {
        // Quick checks first
        if (messageText.contains(" whispers to you:") ||
            messageText.contains(" whispers:") ||
            messageText.contains(" -> me]")) {
            return true;
        }
        
        // Player name checks
        if (messageText.contains("/msg " + playerNameLower) ||
            messageText.contains("/tell " + playerNameLower) ||
            messageText.contains("/w " + playerNameLower) ||
            messageText.contains(" -> " + playerNameLower) ||
            messageText.contains("@" + playerNameLower)) {
            return true;
        }
        
        // Start/end checks
        if (messageText.startsWith(playerNameLower + ",") ||
            messageText.startsWith(playerNameLower + ":") ||
            messageText.endsWith(" " + playerNameLower) ||
            messageText.endsWith(" " + playerNameLower + "?")) {
            return true;
        }
        
        return false;
    }
    
    private boolean isOverworldDimension(MinecraftClient client) {
        if (client.world == null) return false;
        
        // Cache dimension check for performance (check every 5 seconds)
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDimensionCheck < 5000 && cachedDimension != null) {
            return !cachedDimension.contains("the_nether") && !cachedDimension.contains("the_end");
        }
        
        cachedDimension = client.world.getRegistryKey().getValue().toString();
        lastDimensionCheck = currentTime;
        
        boolean isOverworld = !cachedDimension.contains("the_nether") && !cachedDimension.contains("the_end");
        if (!isOverworld) {
            LOGGER.debug("In dimension {}, sleeping disabled (beds explode!)", cachedDimension);
        }
        return isOverworld;
    }
    
    private int calculateTicksUntilNight(long timeOfDay) {
        int ticksUntilNight;
        
        int wakeUpMarginTicks = config.wakeUpMarginSeconds * TICKS_PER_SECOND;
        
        if (timeOfDay < NIGHT_START) {
            // Morning/afternoon - night is coming today
            ticksUntilNight = NIGHT_START - (int)timeOfDay - wakeUpMarginTicks;
        } else {
            // Late night (past NIGHT_END) - night is tomorrow
            ticksUntilNight = (DAY_LENGTH - (int)timeOfDay) + NIGHT_START - wakeUpMarginTicks;
        }
        
        // Ensure minimum delay
        return Math.max(ticksUntilNight, TICKS_PER_SECOND);
    }
    
    private int calculateTicksUntilNextNight(long timeOfDay) {
        int wakeUpMarginTicks = config.wakeUpMarginSeconds * TICKS_PER_SECOND;
        return (DAY_LENGTH - (int)timeOfDay) + NIGHT_START - wakeUpMarginTicks;
    }
    
    private List<BlockPos> findReachableBeds(MinecraftClient client, ClientPlayerEntity player) {
        Vec3d playerEyePos = player.getEyePos();
        BlockPos playerPos = player.getBlockPos();
        List<BlockPos> reachableBeds = new ArrayList<>();
        
        // Use more efficient iteration pattern
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        
        for (int x = -BED_SEARCH_RADIUS; x <= BED_SEARCH_RADIUS; x++) {
            for (int y = -BED_SEARCH_RADIUS; y <= BED_SEARCH_RADIUS; y++) {
                for (int z = -BED_SEARCH_RADIUS; z <= BED_SEARCH_RADIUS; z++) {
                    mutablePos.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                    
                    // Early distance check before block lookup
                    Vec3d bedCenter = Vec3d.ofCenter(mutablePos);
                    double distanceSq = playerEyePos.squaredDistanceTo(bedCenter);
                    
                    if (distanceSq <= MAX_INTERACT_DISTANCE_SQ) {
                        BlockState state = client.world.getBlockState(mutablePos);
                        if (state.getBlock() instanceof BedBlock) {
                            reachableBeds.add(mutablePos.toImmutable());
                        }
                    }
                }
            }
        }
        
        // Sort by distance (closest first)
        reachableBeds.sort(Comparator.comparingDouble(pos -> 
            playerEyePos.squaredDistanceTo(Vec3d.ofCenter(pos))));
        
        return reachableBeds;
    }
    
    private void scheduleDelayedResponse(MinecraftClient client, String message, long delayMs) {
        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS).execute(() -> {
            client.execute(() -> {
                sendChatMessage(client, message);
                
                // Send follow-up message if disconnect phrase is enabled
                if (config.disconnectPhraseEnabled && config.disconnectPhrase != null && !config.disconnectPhrase.isEmpty()) {
                    CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS).execute(() -> {
                        client.execute(() -> {
                            sendChatMessage(client, "To force me to disconnect say: " + config.disconnectPhrase);
                        });
                    });
                }
            });
        });
        
        lastChatResponse = System.currentTimeMillis();
    }
}