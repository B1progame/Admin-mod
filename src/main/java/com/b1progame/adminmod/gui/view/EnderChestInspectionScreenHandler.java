package com.b1progame.adminmod.gui.view;

import com.b1progame.adminmod.gui.AdminGuiService;
import com.b1progame.adminmod.gui.GuiItemFactory;
import com.b1progame.adminmod.util.AuditLogger;
import com.b1progame.adminmod.util.FeedbackUtil;
import com.b1progame.adminmod.util.ServerAccess;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;

public final class EnderChestInspectionScreenHandler extends GenericContainerScreenHandler {
    private static final int SLOT_BACK = 49;
    private final ServerPlayerEntity viewer;
    private final AdminGuiService guiService;
    private final UUID targetUuid;
    private final TargetEnderChestInventoryView inventoryView;

    public EnderChestInspectionScreenHandler(int syncId, PlayerInventory playerInventory, AdminGuiService guiService, UUID targetUuid) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, new TargetEnderChestInventoryView((ServerPlayerEntity) playerInventory.player, guiService, targetUuid), 6);
        this.viewer = (ServerPlayerEntity) playerInventory.player;
        this.guiService = guiService;
        this.targetUuid = targetUuid;
        this.inventoryView = (TargetEnderChestInventoryView) this.getInventory();
        this.getInventory().setStack(SLOT_BACK, GuiItemFactory.button(Items.BARRIER, "Back", Formatting.RED, List.of("Return to player management")));
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex == SLOT_BACK) {
            this.guiService.openInventoryMenu(this.viewer, this.targetUuid);
            return;
        }
        if (slotIndex >= 27 && slotIndex <= 53) {
            return;
        }
        if (!this.inventoryView.isEditable() && actionType != SlotActionType.PICKUP && actionType != SlotActionType.QUICK_MOVE) {
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        ServerPlayerEntity target = ServerAccess.server(this.viewer).getPlayerManager().getPlayer(this.targetUuid);
        if (target != null) {
            target.getEnderChestInventory().markDirty();
            AuditLogger.inventory(
                    this.guiService.configManager(),
                    AuditLogger.actor(this.viewer) + " closed ender view for " + target.getGameProfile().name() + " (" + target.getUuidAsString() + ")"
            );
            return;
        }
        if (this.inventoryView.loadedOfflineData()) {
            return;
        }
        FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.inventory.unavailable"));
    }
}
