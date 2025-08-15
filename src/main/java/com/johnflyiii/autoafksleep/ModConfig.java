package com.johnflyiii.autoafksleep;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Configuration handler for AutoAFKSleep mod
 * Manages loading, saving, and validation of user settings
 */
public class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("autoafksleep-config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(
        FabricLoader.getInstance().getConfigDir().toFile(), 
        "autoafksleep.json"
    );
    
    // Configuration fields
    public boolean modEnabled = true;
    public SleepFailureAction sleepFailureAction = SleepFailureAction.NO_ACTION;
    public String customCommand = "/move lobby";
    public boolean autoRespond = true;
    public String responseMessage = "I'm AFK with auto-sleep enabled. I'll sleep automatically when night comes!";
    public boolean disconnectPhraseEnabled = true;
    public String disconnectPhrase = "afk-logout";
    
    // Advanced timing configuration (in seconds)
    public int wakeUpMarginSeconds = 30; // Wake up this many seconds before night
    public int checkIntervalNightSeconds = 30; // Check interval during night
    public int checkIntervalFailureSeconds = 60; // Check interval after failures
    public int maxConsecutiveFailures = 3; // Stop trying after this many failures
    public int chatResponseCooldownSeconds = 30; // Cooldown between auto-responses
    public int sleepAttemptCooldownSeconds = 3; // Cooldown between sleep attempts
    
    // AutoEat configuration
    public boolean autoEatEnabled = true;
    public int autoEatHungerThreshold = 14; // Start eating when hunger <= this (max 20)
    public boolean autoEatStews = true; // Whether to eat stews/soups
    public int autoEatMinFoodValue = 2; // Minimum hunger value of food to eat
    public boolean autoEatDisconnectOnNoFood = true; // Disconnect when out of safe food
    
    public enum SleepFailureAction {
        DISCONNECT("Disconnect"),
        CUSTOM_COMMAND("Custom Command"),
        NO_ACTION("No Action");
        
        private final String displayName;
        
        SleepFailureAction(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public Text getText() {
            return Text.literal(displayName);
        }
    }
    
    public static ModConfig load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                ModConfig config = GSON.fromJson(reader, ModConfig.class);
                config.validate();
                LOGGER.info("Loaded configuration from {}", CONFIG_FILE.getName());
                return config;
            } catch (Exception e) {
                LOGGER.error("Failed to load configuration, using defaults", e);
            }
        } else {
            LOGGER.info("No configuration file found, creating default");
        }
        
        ModConfig config = new ModConfig();
        config.validate();
        config.save();
        return config;
    }
    
    public void save() {
        try {
            // Ensure config directory exists
            CONFIG_FILE.getParentFile().mkdirs();
            
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
                LOGGER.debug("Saved configuration to {}", CONFIG_FILE.getName());
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save configuration", e);
        }
    }
    
    // Validation methods
    public void validate() {
        // Ensure strings are not null or empty - set defaults if needed
        if (customCommand == null || customCommand.trim().isEmpty()) {
            customCommand = "/move lobby";
        }
        if (responseMessage == null || responseMessage.trim().isEmpty()) {
            responseMessage = "I'm AFK with auto-sleep enabled. I'll sleep automatically when night comes!";
        }
        if (disconnectPhrase == null || disconnectPhrase.trim().isEmpty()) {
            disconnectPhrase = "afk-logout";
        }
        
        // Validate timing parameters (ensure reasonable bounds)
        wakeUpMarginSeconds = Math.max(10, Math.min(120, wakeUpMarginSeconds));
        checkIntervalNightSeconds = Math.max(10, Math.min(300, checkIntervalNightSeconds));
        checkIntervalFailureSeconds = Math.max(30, Math.min(600, checkIntervalFailureSeconds));
        maxConsecutiveFailures = Math.max(1, Math.min(10, maxConsecutiveFailures));
        chatResponseCooldownSeconds = Math.max(10, Math.min(300, chatResponseCooldownSeconds));
        sleepAttemptCooldownSeconds = Math.max(1, Math.min(30, sleepAttemptCooldownSeconds));
        
        // Validate AutoEat parameters
        autoEatHungerThreshold = Math.max(1, Math.min(19, autoEatHungerThreshold));
        autoEatMinFoodValue = Math.max(1, Math.min(20, autoEatMinFoodValue));
    }
}