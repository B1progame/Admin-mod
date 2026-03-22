package com.b1progame.adminmod.gui.menu;

import com.b1progame.adminmod.gui.AdminGuiService;
import com.b1progame.adminmod.gui.GuiItemFactory;
import com.b1progame.adminmod.state.PlayerHistoryData;
import com.b1progame.adminmod.util.FeedbackUtil;
import com.b1progame.adminmod.util.ServerAccess;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;

public final class TempBanDurationMenuHandler extends AbstractActionMenuScreenHandler {
    private static final int SLOT_10M = 10;
    private static final int SLOT_30M = 12;
    private static final int SLOT_2H = 14;
    private static final int SLOT_1D = 16;
    private static final int SLOT_7D = 22;
    private static final int SLOT_BACK = 26;

    private final AdminGuiService guiService;
    private final UUID targetUuid;

    public TempBanDurationMenuHandler(int syncId, PlayerInventory playerInventory, AdminGuiService guiService, UUID targetUuid) {
        super(syncId, playerInventory, 3);
        this.guiService = guiService;
        this.targetUuid = targetUuid;
        buildMenu();
    }

    @Override
    protected void buildMenu() {
        this.menuInventory.setStack(SLOT_10M, GuiItemFactory.button(Items.CLOCK, Text.literal("10m").formatted(Formatting.YELLOW), List.of()));
        this.menuInventory.setStack(SLOT_30M, GuiItemFactory.button(Items.CLOCK, Text.literal("30m").formatted(Formatting.YELLOW), List.of()));
        this.menuInventory.setStack(SLOT_2H, GuiItemFactory.button(Items.CLOCK, Text.literal("2h").formatted(Formatting.YELLOW), List.of()));
        this.menuInventory.setStack(SLOT_1D, GuiItemFactory.button(Items.CLOCK, Text.literal("1d").formatted(Formatting.YELLOW), List.of()));
        this.menuInventory.setStack(SLOT_7D, GuiItemFactory.button(Items.CLOCK, Text.literal("7d").formatted(Formatting.YELLOW), List.of()));
        this.menuInventory.setStack(SLOT_BACK, GuiItemFactory.backButton(this.guiService.configManager()));
    }

    @Override
    protected void onMenuSlotClick(int slot) {
        if (slot == SLOT_BACK) {
            this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
            return;
        }
        long duration = 0L;
        if (slot == SLOT_10M) {
            duration = 10L * 60_000L;
        } else if (slot == SLOT_30M) {
            duration = 30L * 60_000L;
        } else if (slot == SLOT_2H) {
            duration = 2L * 3_600_000L;
        } else if (slot == SLOT_1D) {
            duration = 86_400_000L;
        } else if (slot == SLOT_7D) {
            duration = 7L * 86_400_000L;
        }
        if (duration <= 0L) {
            return;
        }
        PlayerHistoryData history = this.guiService.moderationManager().getPlayerHistory(this.targetUuid);
        String targetName = history == null || history.lastKnownName.isBlank() ? this.targetUuid.toString() : history.lastKnownName;
        long expiresAt = System.currentTimeMillis() + duration;
        this.guiService.moderationManager().ban(this.viewer, this.targetUuid, targetName, "", false, expiresAt);
        var online = ServerAccess.server(this.viewer).getPlayerManager().getPlayer(this.targetUuid);
        if (online != null) {
            online.networkHandler.disconnect(Text.literal(this.guiService.configManager().get().bans.temp_ban_join_denied_message));
        }
        FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.literal("Temporary ban applied."));
        this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
    }
}
