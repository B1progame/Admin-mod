package com.b1progame.adminmod.gui.menu;

import com.b1progame.adminmod.gui.AdminGuiService;
import com.b1progame.adminmod.gui.GuiItemFactory;
import com.b1progame.adminmod.util.FeedbackUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public final class AdminToolsMenuHandler extends AbstractActionMenuScreenHandler {
    private static final int SLOT_VANISH_SETTINGS = 10;
    private static final int SLOT_STAFF_CHAT = 12;
    private static final int SLOT_RELOAD = 14;
    private static final int SLOT_SILENT_JOIN = 16;
    private static final int SLOT_SILENT_QUIT = 20;
    private static final int SLOT_CONSOLE_CHAT = 22;
    private static final int SLOT_XRAY_SETTINGS = 24;
    private static final int SLOT_BACK = 31;

    private final AdminGuiService guiService;

    public AdminToolsMenuHandler(int syncId, PlayerInventory playerInventory, AdminGuiService guiService) {
        super(syncId, playerInventory, 4);
        this.guiService = guiService;
        buildMenu();
    }

    @Override
    protected void buildMenu() {
        boolean vanished = this.guiService.vanishManager().isVanished(this.viewer.getUuid());
        this.menuInventory.setStack(SLOT_VANISH_SETTINGS, GuiItemFactory.button(
                Items.ENDER_EYE,
                Text.literal("Vanish Settings").formatted(Formatting.AQUA),
                List.of(Text.literal(vanished ? "Vanish is currently ON" : "Vanish is currently OFF"), Text.literal("Open vanish controls"))
        ));
        boolean staffChat = this.guiService.moderationManager().isStaffChatEnabled(this.viewer.getUuid());
        this.menuInventory.setStack(SLOT_STAFF_CHAT, GuiItemFactory.button(
                staffChat ? Items.WRITABLE_BOOK : Items.BOOK,
                Text.literal("Staff Chat").formatted(Formatting.AQUA),
                List.of(Text.literal(staffChat ? "State: ON" : "State: OFF"))
        ));
        this.menuInventory.setStack(SLOT_RELOAD, GuiItemFactory.button(
                Items.REPEATER,
                Text.literal("Reload Config").formatted(Formatting.GOLD),
                List.of(Text.literal("Reload adminmod configuration"))
        ));
        boolean silentJoin = this.guiService.moderationManager().isSilentJoinEnabled(this.viewer.getUuid());
        this.menuInventory.setStack(SLOT_SILENT_JOIN, GuiItemFactory.button(
                silentJoin ? Items.LIME_CANDLE : Items.RED_CANDLE,
                Text.literal("Silent Join").formatted(silentJoin ? Formatting.GREEN : Formatting.RED),
                List.of(Text.literal(silentJoin ? "ON" : "OFF"))
        ));
        boolean consoleChat = this.guiService.moderationManager().isConsoleChatEnabled(this.viewer.getUuid());
        this.menuInventory.setStack(SLOT_CONSOLE_CHAT, GuiItemFactory.button(
                consoleChat ? Items.COMMAND_BLOCK : Items.CHAIN_COMMAND_BLOCK,
                Text.literal("Console Chat").formatted(consoleChat ? Formatting.GREEN : Formatting.RED),
                List.of(Text.literal(consoleChat ? "ON" : "OFF"), Text.literal("Send normal chat as nameless server message"))
        ));
        boolean silentQuit = this.guiService.moderationManager().isSilentDisconnectEnabled(this.viewer.getUuid());
        this.menuInventory.setStack(SLOT_SILENT_QUIT, GuiItemFactory.button(
                silentQuit ? Items.LIME_BANNER : Items.RED_BANNER,
                Text.literal("Silent Disconnect").formatted(silentQuit ? Formatting.GREEN : Formatting.RED),
                List.of(Text.literal(silentQuit ? "ON" : "OFF"))
        ));
        this.menuInventory.setStack(SLOT_XRAY_SETTINGS, GuiItemFactory.button(
                Items.DEEPSLATE_DIAMOND_ORE,
                Text.literal("Xray Settings").formatted(Formatting.AQUA),
                List.of(Text.literal("Open tracker settings"))
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
        if (slot == SLOT_VANISH_SETTINGS) {
            this.guiService.openVanishSettings(viewer);
            return;
        }
        if (slot == SLOT_STAFF_CHAT) {
            boolean enabled = this.guiService.moderationManager().toggleStaffChat(viewer);
            FeedbackUtil.success(viewer, this.guiService.configManager(),
                    Text.translatable(enabled ? "message.adminmod.staffchat.on" : "message.adminmod.staffchat.off"));
            this.guiService.openAdminToolsMenu(viewer);
            return;
        }
        if (slot == SLOT_RELOAD) {
            this.guiService.configManager().load();
            FeedbackUtil.success(viewer, this.guiService.configManager(), Text.translatable("message.adminmod.config.reloaded"));
            this.guiService.openAdminToolsMenu(viewer);
            return;
        }
        if (slot == SLOT_SILENT_JOIN) {
            boolean next = !this.guiService.moderationManager().isSilentJoinEnabled(viewer.getUuid());
            this.guiService.moderationManager().setSilentJoin(viewer, next);
            FeedbackUtil.success(viewer, this.guiService.configManager(), Text.literal("Silent join: " + (next ? "ON" : "OFF")));
            this.guiService.openAdminToolsMenu(viewer);
            return;
        }
        if (slot == SLOT_SILENT_QUIT) {
            boolean next = !this.guiService.moderationManager().isSilentDisconnectEnabled(viewer.getUuid());
            this.guiService.moderationManager().setSilentDisconnect(viewer, next);
            FeedbackUtil.success(viewer, this.guiService.configManager(), Text.literal("Silent disconnect: " + (next ? "ON" : "OFF")));
            this.guiService.openAdminToolsMenu(viewer);
            return;
        }
        if (slot == SLOT_CONSOLE_CHAT) {
            boolean next = !this.guiService.moderationManager().isConsoleChatEnabled(viewer.getUuid());
            this.guiService.moderationManager().setConsoleChat(viewer, next);
            FeedbackUtil.success(viewer, this.guiService.configManager(), Text.literal("Console chat: " + (next ? "ON" : "OFF")));
            this.guiService.openAdminToolsMenu(viewer);
            return;
        }
        if (slot == SLOT_XRAY_SETTINGS) {
            this.guiService.openXraySettings(viewer);
        }
    }
}
