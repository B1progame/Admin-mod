package com.b1progame.adminmod.gui.menu;

import com.b1progame.adminmod.gui.AdminGuiService;
import com.b1progame.adminmod.gui.GuiItemFactory;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class XraySettingsMenuHandler extends AbstractActionMenuScreenHandler {
    private static final int SLOT_TRACKER_TOGGLE = 4;
    private static final int SLOT_SOUND = 5;
    private static final int SLOT_CLICK_TP = 6;
    private static final int SLOT_BACK = 22;

    private static final Map<Integer, String> ORE_BY_SLOT = new LinkedHashMap<>();

    static {
        ORE_BY_SLOT.put(9, "minecraft:coal_ore");
        ORE_BY_SLOT.put(10, "minecraft:deepslate_coal_ore");
        ORE_BY_SLOT.put(11, "minecraft:iron_ore");
        ORE_BY_SLOT.put(12, "minecraft:deepslate_iron_ore");
        ORE_BY_SLOT.put(13, "minecraft:gold_ore");
        ORE_BY_SLOT.put(14, "minecraft:deepslate_gold_ore");
        ORE_BY_SLOT.put(15, "minecraft:diamond_ore");
        ORE_BY_SLOT.put(16, "minecraft:deepslate_diamond_ore");
        ORE_BY_SLOT.put(17, "minecraft:emerald_ore");
        ORE_BY_SLOT.put(18, "minecraft:deepslate_emerald_ore");
        ORE_BY_SLOT.put(19, "minecraft:redstone_ore");
        ORE_BY_SLOT.put(20, "minecraft:deepslate_redstone_ore");
        ORE_BY_SLOT.put(21, "minecraft:lapis_ore");
        ORE_BY_SLOT.put(23, "minecraft:deepslate_lapis_ore");
        ORE_BY_SLOT.put(24, "minecraft:ancient_debris");
    }

    private final AdminGuiService guiService;

    public XraySettingsMenuHandler(int syncId, PlayerInventory playerInventory, AdminGuiService guiService) {
        super(syncId, playerInventory, 3);
        this.guiService = guiService;
        buildMenu();
    }

    @Override
    protected void buildMenu() {
        boolean trackerEnabled = this.guiService.moderationManager().isXrayTrackerEnabled();
        this.menuInventory.setStack(SLOT_TRACKER_TOGGLE, GuiItemFactory.button(
                trackerEnabled ? Items.LIME_DYE : Items.RED_DYE,
                Text.literal("Tracker: " + (trackerEnabled ? "ON" : "OFF")).formatted(trackerEnabled ? Formatting.GREEN : Formatting.RED),
                List.of(Text.literal("Global xray tracker toggle"))
        ));
        boolean sound = this.guiService.configManager().get().xray_tracker.alert_sounds;
        this.menuInventory.setStack(SLOT_SOUND, GuiItemFactory.button(
                sound ? Items.NOTE_BLOCK : Items.OAK_BUTTON,
                Text.literal("Alert Sound: " + (sound ? "ON" : "OFF")).formatted(sound ? Formatting.GREEN : Formatting.RED),
                List.of()
        ));
        boolean clickTp = this.guiService.configManager().get().xray_tracker.click_to_teleport_enabled;
        this.menuInventory.setStack(SLOT_CLICK_TP, GuiItemFactory.button(
                clickTp ? Items.ENDER_PEARL : Items.ENDER_EYE,
                Text.literal("Click TP: " + (clickTp ? "ON" : "OFF")).formatted(clickTp ? Formatting.GREEN : Formatting.RED),
                List.of()
        ));

        for (Map.Entry<Integer, String> entry : ORE_BY_SLOT.entrySet()) {
            boolean enabled = this.guiService.moderationManager().isXrayOreEnabled(entry.getValue());
            this.menuInventory.setStack(entry.getKey(), GuiItemFactory.button(
                    enabled ? Items.LIME_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE,
                    Text.literal(shortOre(entry.getValue()) + ": " + (enabled ? "ON" : "OFF"))
                            .formatted(enabled ? Formatting.GREEN : Formatting.RED),
                    List.of(Text.literal(entry.getValue()))
            ));
        }

        this.menuInventory.setStack(SLOT_BACK, GuiItemFactory.backButton(this.guiService.configManager()));
    }

    @Override
    protected void onMenuSlotClick(int slot) {
        if (slot == SLOT_BACK) {
            this.guiService.openAdminToolsMenu(this.viewer);
            return;
        }
        if (slot == SLOT_TRACKER_TOGGLE) {
            this.guiService.moderationManager().setXrayTrackerEnabled(this.viewer, !this.guiService.moderationManager().isXrayTrackerEnabled());
            this.guiService.openXraySettings(this.viewer);
            return;
        }
        if (slot == SLOT_SOUND) {
            this.guiService.configManager().get().xray_tracker.alert_sounds = !this.guiService.configManager().get().xray_tracker.alert_sounds;
            this.guiService.configManager().save();
            this.guiService.moderationManager().recordStaffAction(this.viewer, "xray_setting_sound", String.valueOf(this.guiService.configManager().get().xray_tracker.alert_sounds));
            this.guiService.openXraySettings(this.viewer);
            return;
        }
        if (slot == SLOT_CLICK_TP) {
            this.guiService.configManager().get().xray_tracker.click_to_teleport_enabled = !this.guiService.configManager().get().xray_tracker.click_to_teleport_enabled;
            this.guiService.configManager().save();
            this.guiService.moderationManager().recordStaffAction(this.viewer, "xray_setting_clicktp", String.valueOf(this.guiService.configManager().get().xray_tracker.click_to_teleport_enabled));
            this.guiService.openXraySettings(this.viewer);
            return;
        }
        String ore = ORE_BY_SLOT.get(slot);
        if (ore != null) {
            boolean next = !this.guiService.moderationManager().isXrayOreEnabled(ore);
            this.guiService.moderationManager().setXrayOreEnabled(this.viewer, ore, next);
            this.guiService.openXraySettings(this.viewer);
        }
    }

    private String shortOre(String id) {
        int idx = id.indexOf(':');
        return idx < 0 ? id : id.substring(idx + 1).toUpperCase();
    }
}
