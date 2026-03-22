package com.b1progame.adminmod.gui.menu;

import com.b1progame.adminmod.gui.AdminGuiService;
import com.b1progame.adminmod.gui.GuiItemFactory;
import com.b1progame.adminmod.util.AuditLogger;
import com.b1progame.adminmod.util.FeedbackUtil;
import com.b1progame.adminmod.util.ServerAccess;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;

public final class OpConfirmationMenuHandler extends AbstractActionMenuScreenHandler {
    private static final int SLOT_TARGET = 13;
    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_CANCEL = 15;

    private final AdminGuiService guiService;
    private final UUID targetUuid;

    public OpConfirmationMenuHandler(int syncId, PlayerInventory playerInventory, AdminGuiService guiService, UUID targetUuid) {
        super(syncId, playerInventory, 3);
        this.guiService = guiService;
        this.targetUuid = targetUuid;
        buildMenu();
    }

    @Override
    protected void buildMenu() {
        ServerPlayerEntity target = ServerAccess.server(this.viewer).getPlayerManager().getPlayer(this.targetUuid);
        String targetName = target == null ? "Offline Player" : target.getGameProfile().name();
        this.menuInventory.setStack(SLOT_TARGET, GuiItemFactory.playerHead(
                this.targetUuid,
                targetName,
                List.of("Requires confirmation")
        ));
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
        if (target == null) {
            FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.target_offline"));
            this.guiService.openPlayerList(this.viewer);
            return;
        }

        ServerAccess.server(target).getPlayerManager().addToOperators(new PlayerConfigEntry(target.getGameProfile()));
        FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.op.granted", target.getGameProfile().name()));
        AuditLogger.sensitive(
                this.guiService.configManager(),
                AuditLogger.actor(this.viewer) + " granted OP to " + target.getGameProfile().name() + " (" + target.getUuidAsString() + ")"
        );
        this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
    }
}
