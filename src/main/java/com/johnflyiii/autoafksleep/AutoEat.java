package com.johnflyiii.autoafksleep;

import com.johnflyiii.autoafksleep.mixin.PlayerInventoryAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

public class AutoEat {
    private static final Logger LOGGER = LoggerFactory.getLogger("autoafksleep-autoeat");
    
    // Timing constants (in ticks)
    private static final int EATING_DURATION_TICKS = 32; // 1.6 seconds base eating time
    private static final int EATING_BUFFER_TICKS = 20; // Extra time to ensure completion
    private static final int POST_EAT_DELAY_TICKS = 20; // 1 second cooldown
    private static final int SLOT_SWITCH_DELAY_TICKS = 10; // Delay after switching slots
    
    // State tracking
    private boolean isEating = false;
    private int eatingTicks = 0;
    private int cooldownTicks = 0;
    private int slotSwitchDelay = 0;
    private int targetFoodSlot = -1;
    private int originalSlot = -1;
    private boolean hasWarnedNoFood = false;
    private boolean needsSlotSwitch = false;
    
    // Configuration values
    private boolean enabled = true;
    private int hungerThreshold = 14;
    private boolean eatStew = true;
    private int minFoodValue = 2;
    private boolean disconnectOnNoFood = true;
    
    public void tick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null) {
            return;
        }
        
        ClientPlayerEntity player = client.player;
        
        // Handle cooldown
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }
        
        // Handle slot switch delay
        if (slotSwitchDelay > 0) {
            slotSwitchDelay--;
            
            if (slotSwitchDelay == 0 && targetFoodSlot >= 0) {
                // Start eating after delay
                startEating(client, player);
            }
            return;
        }
        
        // Handle eating progress
        if (isEating) {
            handleEatingProgress(client, player);
            return;
        }
        
        // Check if we should eat
        if (shouldEat(player)) {
            attemptToEat(client, player);
        }
    }
    
    private boolean shouldEat(ClientPlayerEntity player) {
        // Don't eat if already eating or in cooldown
        if (isEating || cooldownTicks > 0 || slotSwitchDelay > 0) {
            return false;
        }
        
        // Check hunger level
        int hunger = player.getHungerManager().getFoodLevel();
        if (hunger >= hungerThreshold) {
            hasWarnedNoFood = false;
            return false;
        }
        
        // Don't eat while sleeping or in GUI
        if (player.isSleeping() || MinecraftClient.getInstance().currentScreen != null) {
            return false;
        }
        
        // Don't eat while using other items
        if (player.isUsingItem() && !player.getActiveItem().isEmpty()) {
            Item activeItem = player.getActiveItem().getItem();
            // Check if the active item is food
            FoodComponent food = player.getActiveItem().get(DataComponentTypes.FOOD);
            if (food == null) {
                // Using non-food item, don't interrupt
                return false;
            }
        }
        
        return true;
    }
    
    private void attemptToEat(MinecraftClient client, ClientPlayerEntity player) {
        int foodSlot = findBestFood(player);
        
        if (foodSlot == -1) {
            handleNoFoodAvailable(client, player);
            return;
        }
        
        PlayerInventory inventory = player.getInventory();
        ItemStack foodStack = inventory.getStack(foodSlot);
        
        PlayerInventoryAccessor inventoryAccessor = (PlayerInventoryAccessor) inventory;
        int currentSlot = inventoryAccessor.getSelectedSlot();
        
        LOGGER.info("Found food: {} in slot {} (current slot: {}, hunger: {}/20)", 
            foodStack.getItem().getName().getString(),
            foodSlot,
            currentSlot,
            player.getHungerManager().getFoodLevel());
        
        // Save current slot
        originalSlot = currentSlot;
        targetFoodSlot = foodSlot;
        
        // Switch to food slot if needed
        if (originalSlot != foodSlot) {
            // Directly set the selected slot using accessor
            inventoryAccessor.setSelectedSlot(foodSlot);
            
            // Send packet to sync with server
            if (client.getNetworkHandler() != null) {
                client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(foodSlot));
            }
            
            LOGGER.info("Switched to slot {} using accessor", foodSlot);
            
            // Set small delay before eating
            slotSwitchDelay = 5;
            needsSlotSwitch = false;
        } else {
            // Already on correct slot, start eating immediately
            needsSlotSwitch = false;
            startEating(client, player);
        }
    }
    
    
    private void startEating(MinecraftClient client, ClientPlayerEntity player) {
        if (targetFoodSlot < 0) {
            return;
        }
        
        // Verify we have food in the current slot
        PlayerInventory inventory = player.getInventory();
        PlayerInventoryAccessor inventoryAccessor = (PlayerInventoryAccessor) inventory;
        int currentSlot = inventoryAccessor.getSelectedSlot();
        ItemStack foodStack = inventory.getStack(currentSlot);
        FoodComponent food = foodStack.get(DataComponentTypes.FOOD);
        
        if (food == null) {
            LOGGER.warn("No food found in current slot {}", currentSlot);
            targetFoodSlot = -1;
            needsSlotSwitch = false;
            return;
        }
        
        LOGGER.info("Starting to eat {} from slot {} (hunger: {}/20)", 
            foodStack.getItem().getName().getString(),
            currentSlot,
            player.getHungerManager().getFoodLevel());
        
        // Start using the item
        client.options.useKey.setPressed(true);
        isEating = true;
        eatingTicks = 0;
        hasWarnedNoFood = false;
        needsSlotSwitch = false;
    }
    
    private void handleEatingProgress(MinecraftClient client, ClientPlayerEntity player) {
        eatingTicks++;
        
        // Keep holding use key
        client.options.useKey.setPressed(true);
        
        // Check if we should stop eating
        int totalEatingTime = EATING_DURATION_TICKS + EATING_BUFFER_TICKS;
        
        // Also check if player is no longer using item (finished eating)
        if (!player.isUsingItem() && eatingTicks > 10) {
            // Player finished eating early
            finishEating(client, player);
        } else if (eatingTicks >= totalEatingTime) {
            // Maximum eating time reached
            finishEating(client, player);
        }
    }
    
    private void finishEating(MinecraftClient client, ClientPlayerEntity player) {
        // Release use key
        client.options.useKey.setPressed(false);
        
        LOGGER.info("Finished eating (new hunger: {}/20)", 
            player.getHungerManager().getFoodLevel());
        
        // Note: We don't restore the original slot since we swapped items
        // The player's original item is now in the food's old slot
        
        // Reset state
        isEating = false;
        eatingTicks = 0;
        cooldownTicks = POST_EAT_DELAY_TICKS;
        targetFoodSlot = -1;
        originalSlot = -1;
        needsSlotSwitch = false;
    }
    
    private void handleNoFoodAvailable(MinecraftClient client, ClientPlayerEntity player) {
        if (hasWarnedNoFood) {
            return;
        }
        
        int hunger = player.getHungerManager().getFoodLevel();
        LOGGER.warn("No safe food available in hotbar! Hunger: {}/20", hunger);
        
        if (disconnectOnNoFood && hunger <= 6) {
            LOGGER.info("Disconnecting due to no safe food available");
            disconnect(client, "no safe food available");
        } else {
            if (client.player != null) {
                client.player.sendMessage(
                    Text.literal("§c[AutoAFK] Warning: No safe food in hotbar! Hunger: " + hunger + "/20"), 
                    false
                );
            }
        }
        
        hasWarnedNoFood = true;
    }
    
    private void disconnect(MinecraftClient client, String reason) {
        client.execute(() -> {
            if (client.world != null && client.getNetworkHandler() != null) {
                LOGGER.info("Disconnecting from server: {}", reason);
                client.getNetworkHandler().getConnection().disconnect(
                    Text.literal("Disconnected by AutoAFKSleep - " + reason)
                );
            }
        });
    }
    
    private int findBestFood(ClientPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        List<FoodCandidate> foodCandidates = new ArrayList<>();
        
        // Only check hotbar slots for reliability
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;
            
            Item item = stack.getItem();
            FoodComponent food = stack.get(DataComponentTypes.FOOD);
            if (food == null) continue;
            
            // Skip poisonous food
            if (isPoisonousFood(item)) {
                LOGGER.debug("Skipping poisonous food: {}", item.getName().getString());
                continue;
            }
            
            // Filter by minimum food value
            if (food.nutrition() < minFoodValue) continue;
            
            // Skip stews/soups if configured
            if (!eatStew && isStewOrSoup(item)) continue;
            
            // Calculate food score
            int score = calculateFoodScore(food, item);
            foodCandidates.add(new FoodCandidate(i, score, food.nutrition(), item));
        }
        
        if (foodCandidates.isEmpty()) {
            return -1;
        }
        
        // Sort by score (higher is better)
        foodCandidates.sort(Comparator.comparingInt(FoodCandidate::score).reversed());
        
        return foodCandidates.get(0).slot();
    }
    
    private boolean isPoisonousFood(Item item) {
        return item == Items.POISONOUS_POTATO || 
               item == Items.SPIDER_EYE || 
               item == Items.ROTTEN_FLESH ||
               item == Items.PUFFERFISH ||
               item == Items.SUSPICIOUS_STEW;
    }
    
    private int calculateFoodScore(FoodComponent food, Item item) {
        int score = food.nutrition() * 10;
        score += (int)(food.saturation() * 10);
        
        if (isMagicalFood(item)) {
            score -= 1000; // Heavy penalty for magical food
        } else {
            score += 100; // Bonus for normal food
        }
        
        if (isCommonFood(item)) {
            score += 50; // Extra bonus for common foods
        }
        
        return score;
    }
    
    private boolean isMagicalFood(Item item) {
        return item == Items.GOLDEN_APPLE ||
               item == Items.ENCHANTED_GOLDEN_APPLE ||
               item == Items.GOLDEN_CARROT ||
               item == Items.GLISTERING_MELON_SLICE ||
               item == Items.CHORUS_FRUIT;
    }
    
    private boolean isCommonFood(Item item) {
        return item == Items.BREAD ||
               item == Items.BAKED_POTATO ||
               item == Items.CARROT ||
               item == Items.APPLE ||
               item == Items.COOKED_BEEF ||
               item == Items.COOKED_PORKCHOP ||
               item == Items.COOKED_CHICKEN ||
               item == Items.COOKED_SALMON ||
               item == Items.COOKED_COD ||
               item == Items.COOKED_MUTTON ||
               item == Items.COOKED_RABBIT ||
               item == Items.COOKIE ||
               item == Items.MELON_SLICE ||
               item == Items.PUMPKIN_PIE ||
               item == Items.BEETROOT;
    }
    
    private boolean isStewOrSoup(Item item) {
        return item == Items.MUSHROOM_STEW ||
               item == Items.RABBIT_STEW ||
               item == Items.BEETROOT_SOUP ||
               item == Items.SUSPICIOUS_STEW;
    }
    
    public void stopEating(MinecraftClient client) {
        if (isEating) {
            client.options.useKey.setPressed(false);
            isEating = false;
            eatingTicks = 0;
            cooldownTicks = POST_EAT_DELAY_TICKS;
        }
    }
    
    // Configuration setters
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled && isEating) {
            stopEating(MinecraftClient.getInstance());
        }
    }
    
    public void setHungerThreshold(int threshold) {
        this.hungerThreshold = Math.max(1, Math.min(19, threshold));
    }
    
    public void setEatStew(boolean eatStew) {
        this.eatStew = eatStew;
    }
    
    public void setMinFoodValue(int minValue) {
        this.minFoodValue = Math.max(1, Math.min(20, minValue));
    }
    
    public void setDisconnectOnNoFood(boolean disconnect) {
        this.disconnectOnNoFood = disconnect;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public boolean isEating() {
        return isEating;
    }
    
    public int getHungerThreshold() {
        return hungerThreshold;
    }
    
    // Helper record for food candidates
    private record FoodCandidate(int slot, int score, int hunger, Item item) {}
}