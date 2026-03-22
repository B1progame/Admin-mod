package com.b1progame.adminmod.gui;

import com.b1progame.adminmod.config.ConfigManager;
import com.b1progame.adminmod.gui.browser.PlayerBrowserSessionManager;
import com.b1progame.adminmod.gui.data.PlayerDataRepository;
import com.b1progame.adminmod.gui.menu.AdminToolsMenuHandler;
import com.b1progame.adminmod.gui.menu.ConfirmActionMenuHandler;
import com.b1progame.adminmod.gui.menu.ConfirmActionType;
import com.b1progame.adminmod.gui.menu.InventoryAccessMenuHandler;
import com.b1progame.adminmod.gui.menu.MainAdminMenuHandler;
import com.b1progame.adminmod.gui.menu.OpConfirmationMenuHandler;
import com.b1progame.adminmod.gui.menu.PlayerNotesMenuHandler;
import com.b1progame.adminmod.gui.menu.PlayerListMenuHandler;
import com.b1progame.adminmod.gui.menu.PlayerManagementMenuHandler;
import com.b1progame.adminmod.gui.menu.PlayerActionHistoryMenuHandler;
import com.b1progame.adminmod.gui.menu.PlayerOverviewMenuHandler;
import com.b1progame.adminmod.gui.menu.PlayerSnapshotsMenuHandler;
import com.b1progame.adminmod.gui.menu.RollbackLiteMenuHandler;
import com.b1progame.adminmod.gui.menu.StaffMailMenuHandler;
import com.b1progame.adminmod.gui.menu.TempOpConfirmMenuHandler;
import com.b1progame.adminmod.gui.menu.TempOpDurationMenuHandler;
import com.b1progame.adminmod.gui.menu.TempBanDurationMenuHandler;
import com.b1progame.adminmod.gui.menu.WorldMenuHandler;
import com.b1progame.adminmod.gui.menu.XraySettingsMenuHandler;
import com.b1progame.adminmod.gui.search.PlayerSearchInputManager;
import com.b1progame.adminmod.gui.view.EnderChestInspectionScreenHandler;
import com.b1progame.adminmod.gui.view.PlayerInventoryInspectionScreenHandler;
import com.b1progame.adminmod.maintenance.MaintenanceManager;
import com.b1progame.adminmod.moderation.ModerationManager;
import com.b1progame.adminmod.util.AuditLogger;
import com.b1progame.adminmod.vanish.VanishManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

public final class AdminGuiService {
    private final ConfigManager configManager;
    private final MaintenanceManager maintenanceManager;
    private final VanishManager vanishManager;
    private final ModerationManager moderationManager;
    private final PlayerBrowserSessionManager browserSessions = new PlayerBrowserSessionManager();
    private final PlayerDataRepository playerDataRepository = new PlayerDataRepository();
    private final PlayerSearchInputManager playerSearchInputManager = new PlayerSearchInputManager(this);

    public AdminGuiService(
            ConfigManager configManager,
            MaintenanceManager maintenanceManager,
            VanishManager vanishManager,
            ModerationManager moderationManager
    ) {
        this.configManager = configManager;
        this.maintenanceManager = maintenanceManager;
        this.vanishManager = vanishManager;
        this.moderationManager = moderationManager;
    }

    public void openMain(ServerPlayerEntity viewer) {
        viewer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new MainAdminMenuHandler(syncId, inventory, this),
                guiTitle(this.configManager.get().gui_titles.main)
        ));
    }

    public void openPlayerList(ServerPlayerEntity viewer) {
        viewer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new PlayerListMenuHandler(syncId, inventory, this),
                guiTitle(this.configManager.get().gui_titles.player_list)
        ));
    }

    public void openPlayerSearchInput(ServerPlayerEntity viewer) {
        this.playerSearchInputManager.openSignInput(viewer);
    }

    public void openPlayerManagement(ServerPlayerEntity viewer, UUID targetUuid) {
        viewer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new PlayerManagementMenuHandler(syncId, inventory, this, targetUuid),
                guiTitle(this.configManager.get().gui_titles.player_manage)
        ));
    }

    public void openInventoryMenu(ServerPlayerEntity viewer, UUID targetUuid) {
        viewer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new InventoryAccessMenuHandler(syncId, inventory, this, targetUuid),
                guiTitle(this.configManager.get().gui_titles.inventory_menu)
        ));
    }

    public void openOpConfirmation(ServerPlayerEntity viewer, UUID targetUuid) {
        viewer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new OpConfirmationMenuHandler(syncId, inventory, this, targetUuid),
                guiTitle(this.configManager.get().gui_titles.op_confirm)
        ));
    }

    public void openTempOpDuration(ServerPlayerEntity viewer, UUID targetUuid) {
        viewer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new TempOpDurationMenuHandler(syncId, inventory, this, targetUuid),
                guiTitle(this.configManager.get().gui_titles.temp_op_duration)
        ));
    }

    public void openTempOpConfirmation(ServerPlayerEntity viewer, UUID targetUuid, long durationSeconds) {
        viewer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new TempOpConfirmMenuHandler(syncId, inventory, this, targetUuid, durationSeconds),
                guiTitle(this.configManager.get().gui_titles.temp_op_confirm)
        ));
    }

    public void openTempBanDuration(ServerPlayerEntity viewer, UUID targetUuid) {
        viewer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new TempBanDurationMenuHandler(syncId, inventory, this, targetUuid),
                guiTitle(this.configManager.get().gui_titles.temp_ban_duration)
        ));
    }

    public void openNotes(ServerPlayerEntity viewer, UUID targetUuid, int page) {
        viewer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new PlayerNotesMenuHandler(syncId, inventory, this, targetUuid, page),
                guiTitle(this.configManager.get().gui_titles.notes)
        ));
    }

    public void openInventoryInspection(ServerPlayerEntity viewer, UUID targetUuid) {
        this.moderationManager.recordStaffAction(viewer, "inventory_inspect_open", targetUuid.toString());
        viewer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new PlayerInventoryInspectionScreenHandler(syncId, inventory, this, targetUuid),
                guiTitle(this.configManager.get().gui_titles.inventory_view)
        ));
    }

    public void openPlayerOverview(ServerPlayerEntity viewer, UUID targetUuid) {
        viewer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new PlayerOverviewMenuHandler(syncId, inventory, this, targetUuid),
                guiTitle(this.configManager.get().gui_titles.player_overview)
        ));
    }

    public void openPlayerActionHistory(ServerPlayerEntity viewer, UUID targetUuid, int page) {
        viewer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new PlayerActionHistoryMenuHandler(syncId, inventory, this, targetUuid, page),
                guiTitle(this.configManager.get().gui_titles.action_history)
        ));
    }

    public void openPlayerSnapshots(ServerPlayerEntity viewer, UUID targetUuid, int page) {
        viewer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new PlayerSnapshotsMenuHandler(syncId, inventory, this, targetUuid, page),
                guiTitle(this.configManager.get().gui_titles.snapshots)
        ));
    }

    public void openEnderInspection(ServerPlayerEntity viewer, UUID targetUuid) {
        this.moderationManager.recordStaffAction(viewer, "ender_inspect_open", targetUuid.toString());
        viewer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new EnderChestInspectionScreenHandler(syncId, inventory, this, targetUuid),
                guiTitle(this.configManager.get().gui_titles.ender_view)
        ));
    }

    public void openRollbackLite(ServerPlayerEntity viewer, UUID targetUuid, int page) {
        viewer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new RollbackLiteMenuHandler(syncId, inventory, this, targetUuid, page),
                guiTitle(this.configManager.get().gui_titles.rollback_lite)
        ));
    }

    public void openConfirmAction(ServerPlayerEntity viewer, UUID targetUuid, ConfirmActionType type) {
        viewer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new ConfirmActionMenuHandler(syncId, inventory, this, targetUuid, type),
                guiTitle(this.configManager.get().gui_titles.confirm_action)
        ));
    }

    public void openWorldMenu(ServerPlayerEntity viewer) {
        viewer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new WorldMenuHandler(syncId, inventory, this),
                guiTitle(this.configManager.get().gui_titles.world)
        ));
    }

    public void openAdminToolsMenu(ServerPlayerEntity viewer) {
        viewer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new AdminToolsMenuHandler(syncId, inventory, this),
                guiTitle(this.configManager.get().gui_titles.admin)
        ));
    }

    public void openXraySettings(ServerPlayerEntity viewer) {
        viewer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new XraySettingsMenuHandler(syncId, inventory, this),
                guiTitle(this.configManager.get().gui_titles.xray_settings)
        ));
    }

    public void openStaffMail(ServerPlayerEntity viewer, int page) {
        viewer.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new StaffMailMenuHandler(syncId, inventory, this, page),
                Text.literal("Staff Mail")
        ));
    }

    public ConfigManager configManager() {
        return this.configManager;
    }

    public MaintenanceManager maintenanceManager() {
        return this.maintenanceManager;
    }

    public VanishManager vanishManager() {
        return this.vanishManager;
    }

    public ModerationManager moderationManager() {
        return this.moderationManager;
    }

    public PlayerBrowserSessionManager browserSessions() {
        return this.browserSessions;
    }

    public PlayerDataRepository playerDataRepository() {
        return this.playerDataRepository;
    }

    public PlayerSearchInputManager playerSearchInputManager() {
        return this.playerSearchInputManager;
    }

    public void auditInventoryAccess(ServerPlayerEntity actor, ServerPlayerEntity target, String kind) {
        AuditLogger.inventory(
                this.configManager,
                AuditLogger.actor(actor) + " opened " + kind + " of " + target.getGameProfile().name() + " (" + target.getUuidAsString() + ")"
        );
    }

    private Text guiTitle(String key) {
        return Text.literal(switch (key) {
            case "gui.adminmod.main.title" -> "Admin Panel";
            case "gui.adminmod.player_browser.title" -> "Player Browser";
            case "gui.adminmod.player_manage.title" -> "Manage Player";
            case "gui.adminmod.inventory_menu.title" -> "Inventory Tools";
            case "gui.adminmod.world.title" -> "World Tools";
            case "gui.adminmod.admin.title" -> "Admin Tools";
            case "gui.adminmod.confirm.op.title" -> "Confirm OP";
            case "gui.adminmod.temp_op.duration.title" -> "Temp OP Duration";
            case "gui.adminmod.temp_op.confirm.title" -> "Confirm Temp OP";
            case "gui.adminmod.notes.title" -> "Player Notes";
            case "gui.adminmod.inventory.title" -> "Inventory View";
            case "gui.adminmod.ender.title" -> "Ender Chest View";
            case "gui.adminmod.confirm.action.title" -> "Confirm Action";
            case "gui.adminmod.player_overview.title" -> "Player Overview";
            case "gui.adminmod.action_history.title" -> "Action History";
            case "gui.adminmod.snapshots.title" -> "Snapshots";
            case "gui.adminmod.temp_ban.duration.title" -> "Temp Ban Duration";
            case "gui.adminmod.rollback.title" -> "Rollback Lite";
            case "gui.adminmod.xray_settings.title" -> "Xray Settings";
            default -> key;
        });
    }
}
