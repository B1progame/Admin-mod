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

public final class MainAdminMenuHandler extends AbstractActionMenuScreenHandler {
    private static final int SLOT_PLAYER = 11;
    private static final int SLOT_WORLD = 13;
    private static final int SLOT_ADMIN = 15;
    private static final int SLOT_MAINTENANCE = 4;
    private static final int SLOT_VANISH = 22;

    private final AdminGuiService guiService;

    public MainAdminMenuHandler(int syncId, PlayerInventory playerInventory, AdminGuiService guiService) {
        super(syncId, playerInventory, 3);
        this.guiService = guiService;
        buildMenu();
    }

    @Override
    protected void buildMenu() {
        this.menuInventory.setStack(SLOT_PLAYER, GuiItemFactory.button(
                Items.PLAYER_HEAD,
                Text.literal("Player").formatted(Formatting.BLUE),
                List.of(Text.literal("Open player browser and moderation tools"))
        ));
        this.menuInventory.setStack(SLOT_WORLD, GuiItemFactory.button(
                Items.COMPASS,
                Text.literal("World").formatted(Formatting.YELLOW),
                List.of(Text.literal("Open world and maintenance tools"))
        ));
        this.menuInventory.setStack(SLOT_ADMIN, GuiItemFactory.button(
                Items.NETHER_STAR,
                Text.literal("Admin").formatted(Formatting.LIGHT_PURPLE),
                List.of(Text.literal("Open staff and self utilities"))
        ));

        boolean maintenanceEnabled = this.guiService.maintenanceManager().isEnabled();
        this.menuInventory.setStack(SLOT_MAINTENANCE, GuiItemFactory.button(
                maintenanceEnabled ? Items.REDSTONE_TORCH : Items.LIME_DYE,
                Text.literal("Maintenance").formatted(maintenanceEnabled ? Formatting.RED : Formatting.GREEN),
                List.of(Text.literal(maintenanceEnabled ? "State: ON" : "State: OFF"))
        ));

        boolean vanishEnabled = this.guiService.vanishManager().isVanished(this.viewer.getUuid());
        this.menuInventory.setStack(SLOT_VANISH, GuiItemFactory.button(
                vanishEnabled ? Items.RED_DYE : Items.LIME_DYE,
                Text.literal("Vanish").formatted(vanishEnabled ? Formatting.RED : Formatting.GREEN),
                List.of(Text.literal(vanishEnabled ? "State: ON" : "State: OFF"))
        ));
    }

    @Override
    protected void onMenuSlotClick(int slot) {
        ServerPlayerEntity viewer = this.viewer;
        if (slot == SLOT_PLAYER) {
            this.guiService.openPlayerList(viewer);
            return;
        }
        if (slot == SLOT_WORLD) {
            this.guiService.openWorldMenu(viewer);
            return;
        }
        if (slot == SLOT_ADMIN) {
            this.guiService.openAdminToolsMenu(viewer);
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
            this.guiService.openMain(viewer);
            return;
        }
        if (slot == SLOT_VANISH) {
            boolean vanished = this.guiService.vanishManager().toggleVanish(viewer, viewer);
            FeedbackUtil.success(viewer, this.guiService.configManager(),
                    Text.translatable(vanished ? "message.adminmod.vanish.on" : "message.adminmod.vanish.off"));
            this.guiService.openMain(viewer);
        }
    }
}
