package com.b1progame.adminmod.gui.menu;

import com.b1progame.adminmod.gui.AdminGuiService;
import com.b1progame.adminmod.gui.GuiItemFactory;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;

public final class TempOpDurationMenuHandler extends AbstractActionMenuScreenHandler {
    private static final int SLOT_5M = 10;
    private static final int SLOT_15M = 12;
    private static final int SLOT_30M = 14;
    private static final int SLOT_1H = 16;
    private static final int SLOT_BACK = 22;

    private final AdminGuiService guiService;
    private final UUID targetUuid;

    public TempOpDurationMenuHandler(int syncId, PlayerInventory playerInventory, AdminGuiService guiService, UUID targetUuid) {
        super(syncId, playerInventory, 3);
        this.guiService = guiService;
        this.targetUuid = targetUuid;
        buildMenu();
    }

    @Override
    protected void buildMenu() {
        this.menuInventory.setStack(SLOT_5M, GuiItemFactory.button(Items.CLOCK, Text.literal("5 Minutes").formatted(Formatting.YELLOW), List.of(Text.literal("Choose a predefined duration"))));
        this.menuInventory.setStack(SLOT_15M, GuiItemFactory.button(Items.CLOCK, Text.literal("15 Minutes").formatted(Formatting.YELLOW), List.of(Text.literal("Choose a predefined duration"))));
        this.menuInventory.setStack(SLOT_30M, GuiItemFactory.button(Items.CLOCK, Text.literal("30 Minutes").formatted(Formatting.YELLOW), List.of(Text.literal("Choose a predefined duration"))));
        this.menuInventory.setStack(SLOT_1H, GuiItemFactory.button(Items.CLOCK, Text.literal("1 Hour").formatted(Formatting.YELLOW), List.of(Text.literal("Choose a predefined duration"))));
        this.menuInventory.setStack(SLOT_BACK, GuiItemFactory.backButton(this.guiService.configManager()));
    }

    @Override
    protected void onMenuSlotClick(int slot) {
        if (slot == SLOT_BACK) {
            this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_5M) {
            this.guiService.openTempOpConfirmation(this.viewer, this.targetUuid, 5 * 60L);
            return;
        }
        if (slot == SLOT_15M) {
            this.guiService.openTempOpConfirmation(this.viewer, this.targetUuid, 15 * 60L);
            return;
        }
        if (slot == SLOT_30M) {
            this.guiService.openTempOpConfirmation(this.viewer, this.targetUuid, 30 * 60L);
            return;
        }
        if (slot == SLOT_1H) {
            this.guiService.openTempOpConfirmation(this.viewer, this.targetUuid, 60 * 60L);
        }
    }
}
