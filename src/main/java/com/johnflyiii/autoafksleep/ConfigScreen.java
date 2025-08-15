package com.johnflyiii.autoafksleep;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private final ModConfig config;
    
    // Widgets
    private CyclingButtonWidget<Boolean> modEnabledButton;
    private CyclingButtonWidget<ModConfig.SleepFailureAction> failureActionButton;
    private TextFieldWidget customCommandField;
    private CyclingButtonWidget<Boolean> autoRespondButton;
    private TextFieldWidget responseMessageField;
    private TextFieldWidget disconnectPhraseField;
    
    // AutoEat widgets
    private CyclingButtonWidget<Boolean> autoEatEnabledButton;
    private ButtonWidget autoEatThresholdButton;
    private CyclingButtonWidget<Boolean> autoEatStewsButton;
    private ButtonWidget autoEatMinFoodButton;
    private CyclingButtonWidget<Boolean> autoEatDisconnectButton;
    
    public ConfigScreen(Screen parent) {
        super(Text.literal("AutoAFK Sleep Configuration"));
        this.parent = parent;
        this.config = AutoAFKSleep.getInstance().getConfig();
    }
    
    @Override
    protected void init() {
        int y = 20;
        int spacing = 25;
        
        // Mod Enabled Toggle
        this.modEnabledButton = this.addDrawableChild(
            CyclingButtonWidget.onOffBuilder(config.modEnabled)
                .build(this.width / 2 - 100, y, 200, 20,
                    Text.literal("Mod Enabled"),
                    (button, value) -> config.modEnabled = value));
        y += spacing;
        
        // Sleep Failure Action
        this.failureActionButton = this.addDrawableChild(
            CyclingButtonWidget.builder(ModConfig.SleepFailureAction::getText)
                .values(ModConfig.SleepFailureAction.values())
                .initially(config.sleepFailureAction)
                .build(this.width / 2 - 100, y, 200, 20,
                    Text.literal("Sleep Failure Action"),
                    (button, value) -> {
                        config.sleepFailureAction = value;
                        updateWidgetVisibility();
                    }));
        y += spacing;
        
        // Custom Command Field
        this.customCommandField = new TextFieldWidget(
            this.textRenderer, this.width / 2 - 100, y, 200, 20,
            Text.literal("Custom Command"));
        this.customCommandField.setText(config.customCommand);
        this.customCommandField.setChangedListener(value -> config.customCommand = value);
        this.customCommandField.setMaxLength(256);
        this.customCommandField.setEditable(true);  // Ensure it's editable
        this.addDrawableChild(customCommandField);
        y += spacing;
        
        // Auto Respond Toggle
        this.autoRespondButton = this.addDrawableChild(
            CyclingButtonWidget.onOffBuilder(config.autoRespond)
                .build(this.width / 2 - 100, y, 200, 20,
                    Text.literal("Auto Respond"),
                    (button, value) -> {
                        config.autoRespond = value;
                        updateWidgetVisibility();
                    }));
        y += spacing;
        
        // Response Message Field
        this.responseMessageField = new TextFieldWidget(
            this.textRenderer, this.width / 2 - 175, y, 350, 20,
            Text.literal("Response Message"));
        this.responseMessageField.setText(config.responseMessage);
        this.responseMessageField.setChangedListener(value -> {
            config.responseMessage = value;
            AutoAFKSleep.LOGGER.debug("Response message changed, length: {}, value: '{}'", value.length(), value);
        });
        this.responseMessageField.setMaxLength(256);
        this.responseMessageField.setEditable(true);  // Ensure it's editable
        // Set cursor to beginning to show start of text
        this.responseMessageField.setCursor(0, false);
        this.addDrawableChild(responseMessageField);
        y += spacing;
        
        // Disconnect Phrase Toggle
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Disconnect Phrase: " + (config.disconnectPhraseEnabled ? "On" : "Off")),
                button -> {
                    config.disconnectPhraseEnabled = !config.disconnectPhraseEnabled;
                    button.setMessage(Text.literal("Disconnect Phrase: " + (config.disconnectPhraseEnabled ? "On" : "Off")));
                    updateWidgetVisibility();
                })
                .dimensions(this.width / 2 - 100, y, 200, 20)
                .build());
        y += spacing;
        
        // Disconnect Phrase Field
        this.disconnectPhraseField = new TextFieldWidget(
            this.textRenderer, this.width / 2 - 100, y, 200, 20,
            Text.literal("Disconnect Phrase"));
        this.disconnectPhraseField.setText(config.disconnectPhrase);
        this.disconnectPhraseField.setChangedListener(value -> config.disconnectPhrase = value);
        this.disconnectPhraseField.setMaxLength(64);
        this.disconnectPhraseField.setEditable(true);  // Ensure it's editable
        this.addDrawableChild(disconnectPhraseField);
        y += spacing + 10;
        
        // AutoEat Section
        y += 10; // Extra spacing before AutoEat section
        
        // AutoEat Enabled Toggle
        this.autoEatEnabledButton = this.addDrawableChild(
            CyclingButtonWidget.onOffBuilder(config.autoEatEnabled)
                .build(this.width / 2 - 100, y, 200, 20,
                    Text.literal("AutoEat"),
                    (button, value) -> {
                        config.autoEatEnabled = value;
                        updateWidgetVisibility();
                    }));
        y += spacing;
        
        // Hunger Threshold
        this.autoEatThresholdButton = this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Hunger Threshold: " + config.autoEatHungerThreshold + "/20"),
                button -> {
                    config.autoEatHungerThreshold = (config.autoEatHungerThreshold % 19) + 1;
                    button.setMessage(Text.literal("Hunger Threshold: " + config.autoEatHungerThreshold + "/20"));
                })
                .dimensions(this.width / 2 - 100, y, 200, 20)
                .build());
        y += spacing;
        
        // Eat Stews Toggle
        this.autoEatStewsButton = this.addDrawableChild(
            CyclingButtonWidget.onOffBuilder(config.autoEatStews)
                .build(this.width / 2 - 100, y, 200, 20,
                    Text.literal("Eat Stews/Soups"),
                    (button, value) -> config.autoEatStews = value));
        y += spacing;
        
        // Minimum Food Value
        this.autoEatMinFoodButton = this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Min Food Value: " + config.autoEatMinFoodValue),
                button -> {
                    config.autoEatMinFoodValue = (config.autoEatMinFoodValue % 10) + 1;
                    button.setMessage(Text.literal("Min Food Value: " + config.autoEatMinFoodValue));
                })
                .dimensions(this.width / 2 - 100, y, 200, 20)
                .build());
        y += spacing;
        
        // Disconnect on No Food Toggle
        this.autoEatDisconnectButton = this.addDrawableChild(
            CyclingButtonWidget.onOffBuilder(config.autoEatDisconnectOnNoFood)
                .build(this.width / 2 - 100, y, 200, 20,
                    Text.literal("Disconnect if No Food"),
                    (button, value) -> config.autoEatDisconnectOnNoFood = value));
        y += spacing + 10;
        
        // Save Button
        // Instructions Button
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Instructions"),
            button -> {
                this.client.setScreen(new InstructionsScreen(this));
            })
            .dimensions(this.width / 2 - 100, y, 95, 20)
            .build());
        
        // Done Button
        this.addDrawableChild(ButtonWidget.builder(
            ScreenTexts.DONE,
            button -> {
                AutoAFKSleep.getInstance().saveConfig();
                this.close();
            })
            .dimensions(this.width / 2 + 5, y, 95, 20)
            .build());
        
        updateWidgetVisibility();
    }
    
    private void updateWidgetVisibility() {
        // Only show custom command field when CUSTOM_COMMAND is selected
        this.customCommandField.visible = config.sleepFailureAction == ModConfig.SleepFailureAction.CUSTOM_COMMAND;
        this.customCommandField.setEditable(this.customCommandField.visible);
        
        // Only show response message field when auto respond is enabled
        this.responseMessageField.visible = config.autoRespond;
        this.responseMessageField.setEditable(config.autoRespond);
        
        // Only show disconnect phrase field when disconnect phrase is enabled
        this.disconnectPhraseField.visible = config.disconnectPhraseEnabled;
        this.disconnectPhraseField.setEditable(config.disconnectPhraseEnabled);
        
        // Only show AutoEat options when AutoEat is enabled
        boolean autoEatEnabled = config.autoEatEnabled;
        this.autoEatThresholdButton.visible = autoEatEnabled;
        this.autoEatStewsButton.visible = autoEatEnabled;
        this.autoEatMinFoodButton.visible = autoEatEnabled;
        this.autoEatDisconnectButton.visible = autoEatEnabled;
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Don't call super.renderBackground here - it's handled by parent render
        super.render(context, mouseX, mouseY, delta);
        
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, 
            this.width / 2, 5, 0xFFFFFF);
        
        // Draw labels for text fields
        if (customCommandField.visible) {
            context.drawTextWithShadow(this.textRenderer, 
                Text.literal("Custom Command:"),
                this.width / 2 - 100, customCommandField.getY() - 10, 0xA0A0A0);
        }
        
        if (responseMessageField.visible) {
            context.drawTextWithShadow(this.textRenderer,
                Text.literal("Response Message:"),
                this.width / 2 - 175, responseMessageField.getY() - 10, 0xA0A0A0);
        }
        
        if (disconnectPhraseField.visible) {
            context.drawTextWithShadow(this.textRenderer,
                Text.literal("Disconnect Phrase (any chat):"),
                this.width / 2 - 100, disconnectPhraseField.getY() - 10, 0xA0A0A0);
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Set focus to clicked text field
        if (this.customCommandField.visible && this.customCommandField.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.customCommandField);
            return true;
        }
        if (this.responseMessageField.visible && this.responseMessageField.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.responseMessageField);
            return true;
        }
        if (this.disconnectPhraseField.visible && this.disconnectPhraseField.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.disconnectPhraseField);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Allow text fields to handle key input first
        if (this.getFocused() instanceof TextFieldWidget) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public void close() {
        this.client.setScreen(parent);
    }
    
    @Override
    public boolean shouldPause() {
        return false;
    }
}