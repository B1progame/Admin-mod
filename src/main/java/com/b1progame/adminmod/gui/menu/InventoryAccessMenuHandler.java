package com.b1progame.adminmod.gui.menu;

import com.b1progame.adminmod.gui.AdminGuiService;
import com.b1progame.adminmod.gui.GuiItemFactory;
import com.b1progame.adminmod.util.FeedbackUtil;
import com.b1progame.adminmod.util.ServerAccess;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;

public final class InventoryAccessMenuHandler extends AbstractActionMenuScreenHandler {
    private static final int SLOT_INVENTORY = 10;
    private static final int SLOT_ENDER = 12;
    private static final int SLOT_CLEAR_INV = 14;
    private static final int SLOT_CLEAR_ENDER = 16;
    private static final int SLOT_BACK = 22;

    private final AdminGuiService guiService;
    private final UUID targetUuid;

    public InventoryAccessMenuHandler(int syncId, PlayerInventory playerInventory, AdminGuiService guiService, UUID targetUuid) {
        super(syncId, playerInventory, 3);
        this.guiService = guiService;
        this.targetUuid = targetUuid;
        buildMenu();
    }

    @Override
    protected void buildMenu() {
        boolean online = ServerAccess.server(this.viewer).getPlayerManager().getPlayer(this.targetUuid) != null;
        this.menuInventory.setStack(SLOT_INVENTORY, GuiItemFactory.button(
                Items.CHEST,
                Text.literal("Open Inventory").formatted(Formatting.GOLD),
                List.of(Text.literal(online ? "Online" : "Offline"))
        ));
        this.menuInventory.setStack(SLOT_ENDER, GuiItemFactory.button(
                Items.ENDER_CHEST,
                Text.literal("Open Ender Chest").formatted(Formatting.DARK_PURPLE),
                List.of(Text.literal(online ? "Online" : "Offline"))
        ));
        this.menuInventory.setStack(SLOT_CLEAR_INV, GuiItemFactory.button(
                Items.LAVA_BUCKET,
                Text.literal("Clear Inventory").formatted(Formatting.RED),
                List.of(Text.literal("Requires confirmation"))
        ));
        this.menuInventory.setStack(SLOT_CLEAR_ENDER, GuiItemFactory.button(
                Items.FIRE_CHARGE,
                Text.literal("Clear Ender Chest").formatted(Formatting.RED),
                List.of(Text.literal("Requires confirmation"))
        ));
        this.menuInventory.setStack(SLOT_BACK, GuiItemFactory.backButton(this.guiService.configManager()));
    }

    @Override
    protected void onMenuSlotClick(int slot) {
        if (slot == SLOT_BACK) {
            this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_INVENTORY) {
            this.guiService.openInventoryInspection(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_ENDER) {
            this.guiService.openEnderInspection(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_CLEAR_INV) {
            if (ServerAccess.server(this.viewer).getPlayerManager().getPlayer(this.targetUuid) == null) {
                FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.target_offline"));
                return;
            }
            this.guiService.openConfirmAction(this.viewer, this.targetUuid, ConfirmActionType.CLEAR_INVENTORY);
            return;
        }
        if (slot == SLOT_CLEAR_ENDER) {
            if (ServerAccess.server(this.viewer).getPlayerManager().getPlayer(this.targetUuid) == null) {
                FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.target_offline"));
                return;
            }
            this.guiService.openConfirmAction(this.viewer, this.targetUuid, ConfirmActionType.CLEAR_ENDER_CHEST);
        }
    }
}
