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

public final class VanishSettingsMenuHandler extends AbstractActionMenuScreenHandler {
    private static final int SLOT_VANISH = 10;
    private static final int SLOT_LEAVE_MESSAGE = 12;
    private static final int SLOT_NOCLIP = 14;
    private static final int SLOT_LOAD_CHUNKS = 16;
    private static final int SLOT_LOAD_ENTITIES = 20;
    private static final int SLOT_FLY_SPEED = 22;
    private static final int SLOT_NIGHT_VISION = 24;
    private static final int SLOT_BACK = 31;

    private final AdminGuiService guiService;

    public VanishSettingsMenuHandler(int syncId, PlayerInventory playerInventory, AdminGuiService guiService) {
        super(syncId, playerInventory, 4);
        this.guiService = guiService;
        buildMenu();
    }

    @Override
    protected void buildMenu() {
        boolean vanished = this.guiService.vanishManager().isVanished(this.viewer.getUuid());
        this.menuInventory.setStack(SLOT_VANISH, GuiItemFactory.button(
                vanished ? Items.RED_DYE : Items.LIME_DYE,
                Text.literal("Vanish").formatted(vanished ? Formatting.RED : Formatting.GREEN),
                List.of(Text.literal(vanished ? "State: ON" : "State: OFF"))
        ));

        boolean leaveMessage = this.guiService.vanishManager().isLeaveMessageEnabled(this.viewer.getUuid());
        this.menuInventory.setStack(SLOT_LEAVE_MESSAGE, GuiItemFactory.button(
                leaveMessage ? Items.LIME_DYE : Items.GRAY_DYE,
                Text.literal("Vanish Leave Message").formatted(leaveMessage ? Formatting.GREEN : Formatting.RED),
                List.of(Text.literal(leaveMessage ? "ON" : "OFF"))
        ));

        boolean noClip = this.guiService.configManager().get().vanish_noclip_enabled;
        this.menuInventory.setStack(SLOT_NOCLIP, GuiItemFactory.button(
                noClip ? Items.ENDER_EYE : Items.IRON_BARS,
                Text.literal("Spectator NoClip").formatted(noClip ? Formatting.GREEN : Formatting.RED),
                List.of(
                        Text.literal(noClip ? "ON" : "OFF"),
                        Text.literal("ON: uses Spectator while vanished"),
                        Text.literal("OFF: keeps normal collision")
                )
        ));

        boolean loadChunks = this.guiService.configManager().get().vanish_load_chunks_enabled;
        this.menuInventory.setStack(SLOT_LOAD_CHUNKS, GuiItemFactory.button(
                loadChunks ? Items.MAP : Items.PAPER,
                Text.literal("Load Chunks").formatted(loadChunks ? Formatting.GREEN : Formatting.RED),
                List.of(Text.literal(loadChunks ? "ON" : "OFF"), Text.literal("OFF lowers chunk send distance while vanished"))
        ));

        boolean loadEntities = this.guiService.configManager().get().vanish_load_entities_enabled;
        this.menuInventory.setStack(SLOT_LOAD_ENTITIES, GuiItemFactory.button(
                loadEntities ? Items.ARMOR_STAND : Items.BARRIER,
                Text.literal("Load Entities").formatted(loadEntities ? Formatting.GREEN : Formatting.RED),
                List.of(Text.literal(loadEntities ? "ON" : "OFF"), Text.literal("OFF lowers simulation distance while vanished"))
        ));

        int flySpeedLevel = this.guiService.vanishManager().getVanishFlySpeedLevel(this.viewer.getUuid());
        this.menuInventory.setStack(SLOT_FLY_SPEED, GuiItemFactory.button(
                flySpeedLevel <= 5 ? Items.FEATHER : Items.FIREWORK_ROCKET,
                Text.literal("Vanish Fly Speed").formatted(Formatting.AQUA),
                List.of(Text.literal("Level: " + flySpeedLevel + " / 10"), Text.literal("Click to cycle 1 -> 10 -> 1"))
        ));

        boolean nightVision = this.guiService.vanishManager().isNightVisionEnabled(this.viewer.getUuid());
        this.menuInventory.setStack(SLOT_NIGHT_VISION, GuiItemFactory.button(
                nightVision ? Items.SEA_LANTERN : Items.BLACK_STAINED_GLASS,
                Text.literal("Night Vision").formatted(nightVision ? Formatting.GREEN : Formatting.RED),
                List.of(Text.literal(nightVision ? "ON" : "OFF"), Text.literal("Applies while vanished"))
        ));

        this.menuInventory.setStack(SLOT_BACK, GuiItemFactory.backButton(this.guiService.configManager()));
    }

    @Override
    protected void onMenuSlotClick(int slot) {
        ServerPlayerEntity viewer = this.viewer;
        if (slot == SLOT_BACK) {
            this.guiService.openAdminToolsMenu(viewer);
            return;
        }
        if (slot == SLOT_VANISH) {
            boolean vanished = this.guiService.vanishManager().toggleVanish(viewer, viewer);
            FeedbackUtil.success(viewer, this.guiService.configManager(),
                    Text.translatable(vanished ? "message.adminmod.vanish.on" : "message.adminmod.vanish.off"));
            this.guiService.openVanishSettings(viewer);
            return;
        }
        if (slot == SLOT_LEAVE_MESSAGE) {
            boolean next = !this.guiService.vanishManager().isLeaveMessageEnabled(viewer.getUuid());
            this.guiService.vanishManager().setLeaveMessageEnabled(viewer, next);
            FeedbackUtil.success(viewer, this.guiService.configManager(), Text.literal("Vanish leave-message: " + (next ? "ON" : "OFF")));
            this.guiService.openVanishSettings(viewer);
            return;
        }
        if (slot == SLOT_NOCLIP) {
            boolean next = !this.guiService.configManager().get().vanish_noclip_enabled;
            this.guiService.configManager().get().vanish_noclip_enabled = next;
            this.guiService.configManager().save();
            this.guiService.vanishManager().refreshVanishRuntime(ServerAccess.server(viewer));
            FeedbackUtil.success(viewer, this.guiService.configManager(), Text.literal("Spectator NoClip: " + (next ? "ON" : "OFF")));
            this.guiService.openVanishSettings(viewer);
            return;
        }
        if (slot == SLOT_LOAD_CHUNKS) {
            boolean next = !this.guiService.configManager().get().vanish_load_chunks_enabled;
            this.guiService.configManager().get().vanish_load_chunks_enabled = next;
            this.guiService.configManager().save();
            this.guiService.vanishManager().refreshVanishRuntime(ServerAccess.server(viewer));
            FeedbackUtil.success(viewer, this.guiService.configManager(), Text.literal("Vanish Load Chunks: " + (next ? "ON" : "OFF")));
            this.guiService.openVanishSettings(viewer);
            return;
        }
        if (slot == SLOT_LOAD_ENTITIES) {
            boolean next = !this.guiService.configManager().get().vanish_load_entities_enabled;
            this.guiService.configManager().get().vanish_load_entities_enabled = next;
            this.guiService.configManager().save();
            this.guiService.vanishManager().refreshVanishRuntime(ServerAccess.server(viewer));
            FeedbackUtil.success(viewer, this.guiService.configManager(), Text.literal("Vanish Load Entities: " + (next ? "ON" : "OFF")));
            this.guiService.openVanishSettings(viewer);
            return;
        }
        if (slot == SLOT_FLY_SPEED) {
            int nextLevel = this.guiService.vanishManager().cycleVanishFlySpeedLevel(viewer);
            FeedbackUtil.success(viewer, this.guiService.configManager(), Text.literal("Vanish fly speed level: " + nextLevel + " / 10"));
            this.guiService.openVanishSettings(viewer);
            return;
        }
        if (slot == SLOT_NIGHT_VISION) {
            boolean next = !this.guiService.vanishManager().isNightVisionEnabled(viewer.getUuid());
            this.guiService.vanishManager().setNightVisionEnabled(viewer, next);
            FeedbackUtil.success(viewer, this.guiService.configManager(), Text.literal("Vanish night vision: " + (next ? "ON" : "OFF")));
            this.guiService.openVanishSettings(viewer);
        }
    }
}
