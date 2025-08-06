package com.johnflyiii.autoafksleep;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import java.util.ArrayList;
import java.util.List;

public class InstructionsScreen extends Screen {
    private final Screen parent;
    private final List<String> instructions = new ArrayList<>();
    
    protected InstructionsScreen(Screen parent) {
        super(Text.literal("AutoAFK Sleep Instructions"));
        this.parent = parent;
        
        // Build instructions
        instructions.add("AutoAFK Sleep Instructions");
        instructions.add("");
        instructions.add("Basic Features:");
        instructions.add("• Automatically sleeps when night falls (if near a bed)");
        instructions.add("• Must be within 2 blocks of a bed to sleep");
        instructions.add("• Works in Overworld only (beds explode in Nether/End!)");
        instructions.add("");
        instructions.add("Auto-Respond Messages:");
        instructions.add("• Responds to direct messages and @mentions");
        instructions.add("• Sends your custom response message");
        instructions.add("• Has a 30-second cooldown to prevent spam");
        instructions.add("");
        instructions.add("Disconnect Phrase:");
        instructions.add("• When enabled, disconnects you if ANYONE says your phrase");
        instructions.add("• Works in ANY chat message (public, private, even your own!)");
        instructions.add("• Choose a unique phrase to avoid accidental disconnects");
        instructions.add("• Default phrase: \"afk-logout\"");
        instructions.add("• Example: If phrase is \"afk-signout\", you'll disconnect");
        instructions.add("  when anyone types \"...afk-signout...\" anywhere");
        instructions.add("");
        instructions.add("Sleep Failure Actions:");
        instructions.add("• No Action: Just logs the failure");
        instructions.add("• Disconnect: Disconnects from server");
        instructions.add("• Custom Command: Runs your specified command");
        instructions.add("");
        instructions.add("Commands:");
        instructions.add("• /autoafksleep enable/disable - Toggle mod");
        instructions.add("• /autoafksleep status - Check current status");
        instructions.add("• /autoafksleep ui - Open this config screen");
        instructions.add("• Press K (default) to open config");
        
        // Debug
        AutoAFKSleep.LOGGER.info("Instructions screen initialized with {} lines", instructions.size());
    }
    
    @Override
    protected void init() {
        // Back button
        this.addDrawableChild(ButtonWidget.builder(
            ScreenTexts.BACK,
            button -> this.close())
            .dimensions(this.width / 2 - 100, this.height - 30, 200, 20)
            .build());
    }
    
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Call super.render first - it handles background and widgets
        super.render(context, mouseX, mouseY, delta);
        
        // Title
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("AutoAFK Sleep Instructions"),
            this.width / 2,
            10,
            0xFFFFFFFF
        );
        
        // Draw instructions
        int y = 35;
        int lineHeight = 12; // Slightly more spacing
        int x = 40;
        
        for (String line : instructions) {
            if (line.isEmpty()) {
                // Empty line - just add space
                y += lineHeight / 2;
            } else if (line.equals("AutoAFK Sleep Instructions")) {
                // Skip - already drawn as title
                continue;
            } else if (line.endsWith(":") && !line.startsWith("•")) {
                // Section headers
                context.drawTextWithShadow(
                    this.textRenderer, 
                    Text.literal(line), 
                    x, 
                    y, 
                    0xFFFFAA00  // Orange with full alpha
                );
                y += lineHeight;
            } else {
                // Regular text
                int indent = line.startsWith("•") ? 0 : 10;
                context.drawTextWithShadow(
                    this.textRenderer, 
                    Text.literal(line), 
                    x + indent, 
                    y, 
                    0xFFE0E0E0  // Light gray with full alpha
                );
                y += lineHeight;
            }
        }
    }
    
    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}