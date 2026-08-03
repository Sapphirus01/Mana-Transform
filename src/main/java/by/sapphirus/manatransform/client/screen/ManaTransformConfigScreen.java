package by.sapphirus.manatransform.client.screen;

import by.sapphirus.manatransform.config.ConversionSettings;
import by.sapphirus.manatransform.config.ManaTransformConfig;
import by.sapphirus.manatransform.network.ConversionConfigNetwork.RequestSettingsPayload;
import by.sapphirus.manatransform.network.ConversionConfigNetwork.UpdateSettingsPayload;
import java.text.DecimalFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ManaTransformConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int FIELD_WIDTH = 120;
    private static final DecimalFormat DISPLAY_NUMBER = new DecimalFormat("0.###");

    private final Screen parent;
    private Button enabledButton;
    private Button saveButton;
    private EditBox ratioField;
    private boolean enabled;
    private boolean applyingServerValues;
    private boolean userEdited;
    private int observedRevision;
    private Component validationMessage = Component.empty();

    public ManaTransformConfigScreen(Screen parent) {
        super(Component.translatable("screen.mana_transform.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int rowWidth = PANEL_WIDTH;
        int top = Math.max(45, height / 2 - 85);

        applyServerValues();
        if (minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(new RequestSettingsPayload());
        }

        enabledButton = addRenderableWidget(Button.builder(enabledLabel(), button -> {
                    enabled = !enabled;
                    userEdited = true;
                    button.setMessage(enabledLabel());
                    validateInput();
                })
                .bounds(left, top + 24, rowWidth, 20)
                .build());

        ratioField = new EditBox(
                font,
                left + rowWidth - FIELD_WIDTH,
                top + 68,
                FIELD_WIDTH,
                20,
                Component.translatable("screen.mana_transform.config.ratio"));
        ratioField.setMaxLength(8);
        ratioField.setFilter(value -> value.matches("\\d{0,4}(\\.\\d{0,3})?"));
        ratioField.setValue(DISPLAY_NUMBER.format(ConversionSettings.clientRatio()));
        ratioField.setResponder(value -> {
            if (!applyingServerValues) {
                userEdited = true;
                validateInput();
            }
        });
        addRenderableWidget(ratioField);

        saveButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.mana_transform.config.save"),
                        button -> save())
                .bounds(left, top + 132, 82, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.mana_transform.config.reset"),
                        button -> resetToDefaults())
                .bounds(left + 89, top + 132, 82, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left + 178, top + 132, 82, 20)
                .build());

        updateEditableState();
        validateInput();
    }

    @Override
    public void tick() {
        super.tick();
        if (!userEdited && observedRevision != ConversionSettings.clientRevision()) {
            applyServerValues();
            if (enabledButton != null) {
                enabledButton.setMessage(enabledLabel());
            }
            if (ratioField != null) {
                applyingServerValues = true;
                ratioField.setValue(DISPLAY_NUMBER.format(ConversionSettings.clientRatio()));
                applyingServerValues = false;
            }
            validateInput();
        }
        updateEditableState();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Screen#render draws the blurred background and all widgets. Draw the explanatory
        // text afterwards so it belongs to the GUI pass and is not blurred with the world.
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(45, height / 2 - 85);

        graphics.drawCenteredString(font, title, width / 2, top - 8, 0xFFFFFF);
        graphics.drawString(
                font,
                Component.translatable("screen.mana_transform.config.enabled"),
                left,
                top + 10,
                0xA0A0A0,
                false);
        graphics.drawString(
                font,
                Component.translatable("screen.mana_transform.config.ratio"),
                left,
                top + 74,
                0xFFFFFF,
                false);

        Float ratio = parsedRatio();
        if (ratio != null) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable(
                            "screen.mana_transform.config.preview.forward",
                            DISPLAY_NUMBER.format(ratio)),
                    width / 2,
                    top + 96,
                    0x80D8FF);
            graphics.drawCenteredString(
                    font,
                    Component.translatable(
                            "screen.mana_transform.config.preview.reverse",
                            DISPLAY_NUMBER.format(1.0F / ratio)),
                    width / 2,
                    top + 108,
                    0x80D8FF);
        }

        if (validationMessage.getString().isEmpty()) {
            Component permissionMessage = editable()
                    ? Component.translatable("screen.mana_transform.config.server_authoritative")
                    : Component.translatable("screen.mana_transform.config.read_only");
            graphics.drawCenteredString(font, permissionMessage, width / 2, top + 158, 0xA0A0A0);
        } else {
            graphics.drawCenteredString(font, validationMessage, width / 2, top + 158, 0xFF5555);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void save() {
        Float ratio = parsedRatio();
        if (!editable() || ratio == null) {
            return;
        }

        PacketDistributor.sendToServer(new UpdateSettingsPayload(enabled, ratio));
        onClose();
    }

    private void resetToDefaults() {
        if (!editable()) {
            return;
        }

        enabled = ManaTransformConfig.DEFAULT_ENABLED;
        enabledButton.setMessage(enabledLabel());
        ratioField.setValue(DISPLAY_NUMBER.format(
                ManaTransformConfig.DEFAULT_IRON_MANA_PER_DRAGON_MANA));
        userEdited = true;
        validateInput();
    }

    private void applyServerValues() {
        enabled = ConversionSettings.clientEnabled();
        observedRevision = ConversionSettings.clientRevision();
    }

    private void updateEditableState() {
        boolean editable = editable();
        if (enabledButton != null) {
            enabledButton.active = editable;
        }
        if (ratioField != null) {
            ratioField.setEditable(editable);
        }
        if (saveButton != null) {
            saveButton.active = editable && parsedRatio() != null;
        }
    }

    private boolean editable() {
        return minecraft.player != null
                && minecraft.getConnection() != null
                && minecraft.player.hasPermissions(2)
                && ConversionSettings.clientHasServerValues();
    }

    private void validateInput() {
        Float ratio = parsedRatio();
        if (ratio == null) {
            validationMessage = Component.translatable(
                    "screen.mana_transform.config.invalid_ratio",
                    DISPLAY_NUMBER.format(ManaTransformConfig.MIN_IRON_MANA_PER_DRAGON_MANA),
                    DISPLAY_NUMBER.format(ManaTransformConfig.MAX_IRON_MANA_PER_DRAGON_MANA));
        } else {
            validationMessage = Component.empty();
        }
        updateEditableState();
    }

    private Float parsedRatio() {
        if (ratioField == null || ratioField.getValue().isBlank()) {
            return null;
        }

        try {
            float ratio = Float.parseFloat(ratioField.getValue());
            if (!Float.isFinite(ratio)
                    || ratio < ManaTransformConfig.MIN_IRON_MANA_PER_DRAGON_MANA
                    || ratio > ManaTransformConfig.MAX_IRON_MANA_PER_DRAGON_MANA) {
                return null;
            }
            return ratio;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Component enabledLabel() {
        return Component.translatable(enabled ? "options.on" : "options.off");
    }
}
