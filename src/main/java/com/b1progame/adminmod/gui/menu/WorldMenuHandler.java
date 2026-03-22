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
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Set;

public final class WorldMenuHandler extends AbstractActionMenuScreenHandler {
    private static final int SLOT_MAINTENANCE = 11;
    private static final int SLOT_SPAWN = 13;
    private static final int SLOT_BACK = 18;

    private final AdminGuiService guiService;

    public WorldMenuHandler(int syncId, PlayerInventory playerInventory, AdminGuiService guiService) {
        super(syncId, playerInventory, 3);
        this.guiService = guiService;
        buildMenu();
    }

    @Override
    protected void buildMenu() {
        boolean enabled = this.guiService.maintenanceManager().isEnabled();
        this.menuInventory.setStack(SLOT_MAINTENANCE, GuiItemFactory.button(
                enabled ? Items.REDSTONE_TORCH : Items.LEVER,
                Text.literal("Maintenance").formatted(enabled ? Formatting.RED : Formatting.GREEN),
                List.of(Text.literal(enabled ? "State: ON" : "State: OFF"))
        ));
        this.menuInventory.setStack(SLOT_SPAWN, GuiItemFactory.button(
                Items.COMPASS,
                Text.literal("Teleport To Spawn").formatted(Formatting.AQUA),
                List.of(Text.literal("Move yourself to overworld spawn"))
        ));
        this.menuInventory.setStack(SLOT_BACK, GuiItemFactory.backButton(this.guiService.configManager()));
    }

    @Override
    protected void onMenuSlotClick(int slot) {
        ServerPlayerEntity viewer = this.viewer;
        if (slot == SLOT_BACK) {
            this.guiService.openMain(viewer);
            return;
        }
        if (slot == SLOT_MAINTENANCE) {
            if (this.guiService.maintenanceManager().isEnabled()) {
                this.guiService.maintenanceManager().disable(ServerAccess.server(viewer), viewer);
                FeedbackUtil.success(viewer, this.guiService.configManager(), Text.translatable("message.adminmod.maintenance.off"));
            } else {
                this.guiService.maintenanceManager().enable(ServerAccess.server(viewer), viewer);
                FeedbackUtil.success(viewer, this.guiService.configManager(), Text.translatable("message.adminmod.maintenance.on"));
            }
            this.guiService.openWorldMenu(viewer);
            return;
        }
        if (slot == SLOT_SPAWN) {
            BlockPos spawn = ServerAccess.server(viewer).getOverworld().getSpawnPoint().getPos();
            viewer.teleport(ServerAccess.server(viewer).getOverworld(),
                    spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D,
                    Set.of(), viewer.getYaw(), viewer.getPitch(), false);
            FeedbackUtil.success(viewer, this.guiService.configManager(), Text.translatable("message.adminmod.tp.spawn"));
        }
    }
}
