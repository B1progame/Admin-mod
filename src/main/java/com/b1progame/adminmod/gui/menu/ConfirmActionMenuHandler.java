package com.b1progame.adminmod.gui.menu;

import com.b1progame.adminmod.gui.AdminGuiService;
import com.b1progame.adminmod.gui.GuiItemFactory;
import com.b1progame.adminmod.util.AuditLogger;
import com.b1progame.adminmod.util.FeedbackUtil;
import com.b1progame.adminmod.util.ServerAccess;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;

public final class ConfirmActionMenuHandler extends AbstractActionMenuScreenHandler {
    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_INFO = 13;
    private static final int SLOT_CANCEL = 15;

    private final AdminGuiService guiService;
    private final UUID targetUuid;
    private final ConfirmActionType type;

    public ConfirmActionMenuHandler(int syncId, PlayerInventory playerInventory, AdminGuiService guiService, UUID targetUuid, ConfirmActionType type) {
        super(syncId, playerInventory, 3);
        this.guiService = guiService;
        this.targetUuid = targetUuid;
        this.type = type;
        buildMenu();
    }

    @Override
    protected void buildMenu() {
        this.menuInventory.setStack(SLOT_CONFIRM, GuiItemFactory.button(
                Items.LIME_STAINED_GLASS_PANE,
                Text.literal("Confirm").formatted(Formatting.GREEN),
                List.of(Text.literal("Execute this action"))
        ));
        this.menuInventory.setStack(SLOT_CANCEL, GuiItemFactory.button(
                Items.RED_STAINED_GLASS_PANE,
                Text.literal("Cancel").formatted(Formatting.RED),
                List.of(Text.literal("Abort and go back"))
        ));
        this.menuInventory.setStack(SLOT_INFO, GuiItemFactory.playerHead(
                this.targetUuid,
                actionLabel().getString(),
                List.of("Target: " + this.targetUuid)
        ));
    }

    @Override
    protected void onMenuSlotClick(int slot) {
        if (slot == SLOT_CANCEL) {
            this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
            return;
        }
        if (slot != SLOT_CONFIRM) {
            return;
        }

        ServerPlayerEntity target = ServerAccess.server(this.viewer).getPlayerManager().getPlayer(this.targetUuid);
        if (this.type == ConfirmActionType.KICK) {
            if (target == null) {
                FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.target_offline"));
                this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
                return;
            }
            target.networkHandler.disconnect(Text.translatable("message.adminmod.kicked"));
            AuditLogger.sensitive(this.guiService.configManager(),
                    AuditLogger.actor(this.viewer) + " kicked " + target.getGameProfile().name() + " (" + target.getUuidAsString() + ")");
            FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.kick.done"));
            this.guiService.openPlayerList(this.viewer);
            return;
        }
        if (this.type == ConfirmActionType.CLEAR_INVENTORY) {
            if (target != null) {
                this.guiService.moderationManager().recordClearRollback(
                        this.viewer,
                        target.getUuid(),
                        target.getGameProfile().name(),
                        "inventory",
                        target.getInventory(),
                        41
                );
                target.getInventory().clear();
                target.getInventory().markDirty();
                this.guiService.moderationManager().recordStaffAction(this.viewer, "inventory_clear", target.getGameProfile().name());
            }
            AuditLogger.inventory(this.guiService.configManager(),
                    AuditLogger.actor(this.viewer) + " cleared inventory of " + this.targetUuid);
            FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.inventory.cleared"));
            this.guiService.openInventoryMenu(this.viewer, this.targetUuid);
            return;
        }
        if (this.type == ConfirmActionType.CLEAR_ENDER_CHEST) {
            if (target != null) {
                this.guiService.moderationManager().recordClearRollback(
                        this.viewer,
                        target.getUuid(),
                        target.getGameProfile().name(),
                        "ender",
                        target.getEnderChestInventory(),
                        27
                );
                target.getEnderChestInventory().clear();
                target.getEnderChestInventory().markDirty();
                this.guiService.moderationManager().recordStaffAction(this.viewer, "ender_clear", target.getGameProfile().name());
            }
            AuditLogger.inventory(this.guiService.configManager(),
                    AuditLogger.actor(this.viewer) + " cleared ender chest of " + this.targetUuid);
            FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.ender.cleared"));
            this.guiService.openInventoryMenu(this.viewer, this.targetUuid);
        }
    }

    private Text actionLabel() {
        return switch (this.type) {
            case KICK -> Text.literal("Kick");
            case CLEAR_INVENTORY -> Text.literal("Clear Inventory");
            case CLEAR_ENDER_CHEST -> Text.literal("Clear Ender Chest");
        };
    }
}
