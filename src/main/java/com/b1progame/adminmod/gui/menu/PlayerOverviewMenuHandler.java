package com.b1progame.adminmod.gui.menu;

import com.b1progame.adminmod.gui.AdminGuiService;
import com.b1progame.adminmod.gui.GuiItemFactory;
import com.b1progame.adminmod.state.PlayerHistoryData;
import com.b1progame.adminmod.util.AuditLogger;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayerOverviewMenuHandler extends AbstractActionMenuScreenHandler {
    private static final int SLOT_HEAD = 4;
    private static final int SLOT_STATUS = 10;
    private static final int SLOT_LOCATION = 12;
    private static final int SLOT_STATS = 14;
    private static final int SLOT_FLAGS = 16;
    private static final int SLOT_BACK = 31;

    private final AdminGuiService guiService;
    private final UUID targetUuid;

    public PlayerOverviewMenuHandler(int syncId, PlayerInventory playerInventory, AdminGuiService guiService, UUID targetUuid) {
        super(syncId, playerInventory, 4);
        this.guiService = guiService;
        this.targetUuid = targetUuid;
        buildMenu();
    }

    @Override
    protected void buildMenu() {
        PlayerHistoryData history = this.guiService.moderationManager().getPlayerHistory(this.targetUuid);
        String name = history == null || history.lastKnownName.isBlank() ? this.targetUuid.toString() : history.lastKnownName;

        this.menuInventory.setStack(SLOT_HEAD, GuiItemFactory.playerHead(
                this.targetUuid,
                name,
                List.of("UUID: " + this.targetUuid)
        ));
        this.menuInventory.setStack(SLOT_STATUS, GuiItemFactory.button(
                Items.COMPARATOR,
                Text.literal("Connection").formatted(Formatting.AQUA),
                List.of(
                        Text.literal("Online: " + (history != null && history.online)),
                        Text.literal("Last join: " + (history == null ? "-" : formatEpoch(history.lastJoinEpochMillis))),
                        Text.literal("Last quit: " + (history == null ? "-" : formatEpoch(history.lastQuitEpochMillis)))
                )
        ));
        this.menuInventory.setStack(SLOT_LOCATION, GuiItemFactory.button(
                Items.COMPASS,
                Text.literal("Location").formatted(Formatting.BLUE),
                List.of(
                        Text.literal("World: " + (history == null ? "-" : history.lastWorld)),
                        Text.literal("Dimension: " + (history == null ? "-" : history.lastDimension)),
                        Text.literal("XYZ: " + (history == null ? "-" : ((int) history.lastX + " " + (int) history.lastY + " " + (int) history.lastZ))),
                        Text.literal("IP: " + renderIp(history))
                )
        ));
        this.menuInventory.setStack(SLOT_STATS, GuiItemFactory.button(
                Items.GOLDEN_APPLE,
                Text.literal("Stats").formatted(Formatting.GOLD),
                List.of(
                        Text.literal("Health: " + (history == null ? "-" : history.lastHealth)),
                        Text.literal("Hunger: " + (history == null ? "-" : history.lastHunger)),
                        Text.literal("XP Level: " + (history == null ? "-" : history.lastXpLevel)),
                        Text.literal("Ping: " + (history == null ? "-" : history.lastPing)),
                        Text.literal("Last seen: " + (history == null ? "-" : formatEpoch(history.lastSeenEpochMillis)))
                )
        ));

        List<Text> flags = new ArrayList<>();
        flags.add(Text.literal("Muted: " + this.guiService.moderationManager().isMuted(this.targetUuid)));
        flags.add(Text.literal("Frozen: " + this.guiService.moderationManager().isFrozen(this.targetUuid)));
        flags.add(Text.literal("Banned: " + this.guiService.moderationManager().isBanned(this.targetUuid)));
        flags.add(Text.literal("Vanished: " + this.guiService.vanishManager().isVanished(this.targetUuid)));
        flags.add(Text.literal("Watchlisted: " + this.guiService.moderationManager().isWatchlisted(this.targetUuid)));
        int suspicion = this.guiService.moderationManager().calculateXraySuspicionScore(this.targetUuid);
        flags.add(Text.literal("Xray suspicion: " + suspicion + " (" + this.guiService.moderationManager().suspicionLabel(suspicion) + ")"));
        int punishments = this.guiService.moderationManager().listActionHistory(this.targetUuid).size();
        flags.add(Text.literal("Punishment/action records: " + punishments));
        this.menuInventory.setStack(SLOT_FLAGS, GuiItemFactory.button(
                Items.NAME_TAG,
                Text.literal("Moderation Flags").formatted(Formatting.RED),
                flags
        ));

        this.menuInventory.setStack(SLOT_BACK, GuiItemFactory.backButton(this.guiService.configManager()));
        if (this.guiService.configManager().get().logging.ip_view_access) {
            AuditLogger.sensitive(this.guiService.configManager(), "IP view access by " + this.viewer.getGameProfile().name() + " for " + name);
        }
    }

    @Override
    protected void onMenuSlotClick(int slot) {
        if (slot == SLOT_BACK) {
            this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
        }
    }

    private String formatEpoch(long epochMillis) {
        if (epochMillis <= 0L) {
            return "-";
        }
        return Instant.ofEpochMilli(epochMillis).toString();
    }

    private String renderIp(PlayerHistoryData history) {
        if (!this.guiService.configManager().get().ip_view.ip_view_enabled) {
            return "disabled";
        }
        if (history == null || history.lastKnownIp == null || history.lastKnownIp.isBlank()) {
            return "-";
        }
        return history.lastKnownIp;
    }
}
