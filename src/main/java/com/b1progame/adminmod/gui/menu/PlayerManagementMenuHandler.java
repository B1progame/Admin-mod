package com.b1progame.adminmod.gui.menu;

import com.b1progame.adminmod.gui.AdminGuiService;
import com.b1progame.adminmod.gui.GuiItemFactory;
import com.b1progame.adminmod.gui.browser.PlayerBrowserEntry;
import com.b1progame.adminmod.util.AuditLogger;
import com.b1progame.adminmod.util.FeedbackUtil;
import com.b1progame.adminmod.util.ServerAccess;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class PlayerManagementMenuHandler extends AbstractActionMenuScreenHandler {
    private static final int SLOT_TARGET_HEAD = 4;
    private static final int SLOT_INV_MENU = 10;
    private static final int SLOT_KICK = 11;
    private static final int SLOT_CREATIVE = 12;
    private static final int SLOT_OP_PERM = 13;
    private static final int SLOT_OP_TEMP = 14;
    private static final int SLOT_FREEZE = 15;
    private static final int SLOT_MUTE = 16;
    private static final int SLOT_WATCHLIST = 17;
    private static final int SLOT_ROLLBACK = 18;
    private static final int SLOT_TP_TO_TARGET = 19;
    private static final int SLOT_TP_TARGET_TO_ADMIN = 20;
    private static final int SLOT_NOTES = 21;
    private static final int SLOT_OVERVIEW = 22;
    private static final int SLOT_HISTORY = 23;
    private static final int SLOT_SNAPSHOTS = 24;
    private static final int SLOT_BAN = 25;
    private static final int SLOT_TEMP_BAN = 26;
    private static final int SLOT_HEAL = 27;
    private static final int SLOT_FEED = 28;
    private static final int SLOT_STARVE = 29;
    private static final int SLOT_KILL = 30;
    private static final int SLOT_ALT_INFO = 31;
    private static final int SLOT_WHITELIST = 32;
    private static final int SLOT_BACK = 40;
    private static final int SLOT_REFRESH = 41;

    private final AdminGuiService guiService;
    private final UUID targetUuid;

    public PlayerManagementMenuHandler(int syncId, PlayerInventory playerInventory, AdminGuiService guiService, UUID targetUuid) {
        super(syncId, playerInventory, 5);
        this.guiService = guiService;
        this.targetUuid = targetUuid;
        buildMenu();
    }

    @Override
    protected void buildMenu() {
        PlayerBrowserEntry target = resolveEntry();
        String targetName = target == null ? this.targetUuid.toString() : target.name;

        List<String> stateLore = new ArrayList<>();
        stateLore.add(target != null && target.online ? "Online" : "Offline");
        stateLore.add("Ping: " + (target == null || target.ping < 0 ? "-" : target.ping));
        stateLore.add("World: " + (target == null ? "-" : target.worldName));
        stateLore.add("Health: " + (target == null || target.health < 0 ? "-" : String.format(Locale.ROOT, "%.1f", target.health)));
        if (this.guiService.moderationManager().isFrozen(this.targetUuid)) {
            stateLore.add("State: Frozen");
        }
        if (this.guiService.moderationManager().isMuted(this.targetUuid)) {
            stateLore.add("State: Muted");
        }
        if (this.guiService.vanishManager().isVanished(this.targetUuid)) {
            stateLore.add("State: Vanished");
        }
        if (this.guiService.moderationManager().hasActiveTempOp(this.targetUuid)) {
            stateLore.add("State: Temp OP");
        }
        if (this.guiService.moderationManager().isWatchlisted(this.targetUuid)) {
            stateLore.add("State: Watchlisted");
        }

        this.menuInventory.setStack(SLOT_TARGET_HEAD, GuiItemFactory.playerHead(this.targetUuid, targetName, stateLore));
        this.menuInventory.setStack(SLOT_INV_MENU, GuiItemFactory.button(
                Items.CHEST,
                Text.literal("Inventory Tools").formatted(Formatting.GOLD),
                List.of(Text.literal("Open inventory/ender subpage"))
        ));
        this.menuInventory.setStack(SLOT_KICK, GuiItemFactory.button(
                Items.IRON_SWORD,
                Text.literal("Kick").formatted(Formatting.RED),
                List.of(Text.literal("Requires confirmation"))
        ));
        boolean targetCreative = target != null && target.online && resolveOnlineTarget() != null
                && resolveOnlineTarget().interactionManager.getGameMode() == GameMode.CREATIVE;
        this.menuInventory.setStack(SLOT_CREATIVE, GuiItemFactory.button(
                targetCreative ? Items.IRON_SWORD : Items.GRASS_BLOCK,
                Text.literal(targetCreative ? "Set Survival" : "Set Creative").formatted(targetCreative ? Formatting.RED : Formatting.GREEN),
                List.of(Text.literal(targetCreative ? "Toggle target to survival mode" : "Toggle target to creative mode"))
        ));
        boolean hasPermOp = this.guiService.moderationManager().isPermanentOp(ServerAccess.server(this.viewer), this.targetUuid, targetName);
        boolean hasTempOp = this.guiService.moderationManager().hasActiveTempOp(this.targetUuid);
        long tempOpRemaining = this.guiService.moderationManager().tempOpRemainingMillis(this.targetUuid);
        this.menuInventory.setStack(SLOT_OP_PERM, GuiItemFactory.button(
                hasPermOp ? Items.REDSTONE_TORCH : Items.NETHER_STAR,
                Text.literal(hasPermOp ? "Remove Permanent OP" : "Grant Permanent OP").formatted(hasPermOp ? Formatting.RED : Formatting.LIGHT_PURPLE),
                List.of(Text.literal("State: " + (hasPermOp ? "ACTIVE" : "INACTIVE")))
        ));
        this.menuInventory.setStack(SLOT_OP_TEMP, GuiItemFactory.button(
                hasTempOp ? Items.BARRIER : Items.CLOCK,
                Text.literal(hasTempOp ? "Revoke Temp OP" : "Grant Temp OP").formatted(hasTempOp ? Formatting.RED : Formatting.YELLOW),
                List.of(Text.literal(hasTempOp ? "Remaining: " + com.b1progame.adminmod.util.DurationParser.formatMillis(tempOpRemaining) : "Open duration selection"))
        ));
        boolean frozen = this.guiService.moderationManager().isFrozen(this.targetUuid);
        this.menuInventory.setStack(SLOT_FREEZE, GuiItemFactory.button(
                frozen ? Items.PACKED_ICE : Items.ICE,
                Text.literal(frozen ? "Unfreeze" : "Freeze").formatted(Formatting.AQUA),
                List.of(Text.literal("Toggle frozen moderation state"))
        ));
        boolean muted = this.guiService.moderationManager().isMuted(this.targetUuid);
        this.menuInventory.setStack(SLOT_MUTE, GuiItemFactory.button(
                muted ? Items.BELL : Items.BARRIER,
                Text.literal(muted ? "Unmute" : "Mute").formatted(Formatting.BLUE),
                List.of(Text.literal("Toggle muted moderation state"))
        ));
        boolean watchlisted = this.guiService.moderationManager().isWatchlisted(this.targetUuid);
        this.menuInventory.setStack(SLOT_WATCHLIST, GuiItemFactory.button(
                watchlisted ? Items.LIME_BANNER : Items.RED_BANNER,
                Text.literal(watchlisted ? "Watchlist: Remove" : "Watchlist: Add").formatted(watchlisted ? Formatting.GREEN : Formatting.RED),
                List.of(Text.literal(watchlisted ? "Currently watchlisted" : "Mark this player for review"))
        ));
        this.menuInventory.setStack(SLOT_ROLLBACK, GuiItemFactory.button(
                Items.RECOVERY_COMPASS,
                Text.literal("Rollback Lite").formatted(Formatting.YELLOW),
                List.of(Text.literal("View/apply recent admin inventory rollbacks"))
        ));
        this.menuInventory.setStack(SLOT_TP_TO_TARGET, GuiItemFactory.button(
                Items.ENDER_PEARL,
                Text.literal("Teleport To Player").formatted(Formatting.GREEN),
                List.of(Text.literal("Move yourself to target player"))
        ));
        this.menuInventory.setStack(SLOT_TP_TARGET_TO_ADMIN, GuiItemFactory.button(
                Items.LEAD,
                Text.literal("Teleport Player To You").formatted(Formatting.GOLD),
                List.of(Text.literal("Bring target player to your location"))
        ));
        this.menuInventory.setStack(SLOT_NOTES, GuiItemFactory.button(
                Items.WRITABLE_BOOK,
                Text.literal("Notes").formatted(Formatting.YELLOW),
                List.of(Text.literal("Notes: " + this.guiService.moderationManager().notesCount(this.targetUuid)))
        ));
        this.menuInventory.setStack(SLOT_OVERVIEW, GuiItemFactory.button(
                Items.BOOK,
                Text.literal("Player Overview").formatted(Formatting.AQUA),
                List.of(Text.literal("History and player data"))
        ));
        this.menuInventory.setStack(SLOT_HISTORY, GuiItemFactory.button(
                Items.PAPER,
                Text.literal("Action History").formatted(Formatting.BLUE),
                List.of(Text.literal("Punishment/action records"))
        ));
        this.menuInventory.setStack(SLOT_SNAPSHOTS, GuiItemFactory.button(
                Items.CHEST_MINECART,
                Text.literal("Snapshots").formatted(Formatting.GOLD),
                List.of(Text.literal("Inventory snapshot tools"))
        ));
        boolean banned = this.guiService.moderationManager().isBanned(this.targetUuid);
        this.menuInventory.setStack(SLOT_BAN, GuiItemFactory.button(
                banned ? Items.LIME_DYE : Items.IRON_AXE,
                Text.literal(banned ? "Unban" : "Ban").formatted(banned ? Formatting.GREEN : Formatting.RED),
                List.of(Text.literal("Permanent quick toggle"))
        ));
        this.menuInventory.setStack(SLOT_TEMP_BAN, GuiItemFactory.button(
                Items.CLOCK,
                Text.literal("Temp Ban").formatted(Formatting.YELLOW),
                List.of(Text.literal("Open duration selection"))
        ));
        this.menuInventory.setStack(SLOT_HEAL, GuiItemFactory.button(
                Items.GOLDEN_APPLE,
                Text.literal("Heal").formatted(Formatting.GREEN),
                List.of(Text.literal("Set target health to maximum"))
        ));
        this.menuInventory.setStack(SLOT_FEED, GuiItemFactory.button(
                Items.COOKED_BEEF,
                Text.literal("Feed").formatted(Formatting.GOLD),
                List.of(Text.literal("Set hunger and saturation to full"))
        ));
        this.menuInventory.setStack(SLOT_STARVE, GuiItemFactory.button(
                Items.ROTTEN_FLESH,
                Text.literal("Starve").formatted(Formatting.RED),
                List.of(Text.literal("Set hunger and saturation to zero"))
        ));
        this.menuInventory.setStack(SLOT_KILL, GuiItemFactory.button(
                Items.WITHER_SKELETON_SKULL,
                Text.literal("Kill").formatted(Formatting.DARK_RED),
                List.of(Text.literal("Instantly kill the target player"))
        ));
        List<com.b1progame.adminmod.moderation.ModerationManager.AltMatch> altMatches =
                this.guiService.moderationManager().listPossibleAltMatches(this.targetUuid, 3);
        List<Text> altLore = new ArrayList<>();
        altLore.add(Text.literal("Possible alts: " + altMatches.size()));
        if (altMatches.isEmpty()) {
            altLore.add(Text.literal("No shared-IP matches found"));
        } else {
            for (com.b1progame.adminmod.moderation.ModerationManager.AltMatch match : altMatches) {
                altLore.add(Text.literal("Name: " + match.name()));
                altLore.add(Text.literal("UUID: " + match.uuid()));
                altLore.add(Text.literal("Last Seen: " + java.time.Instant.ofEpochMilli(match.lastSeenEpochMillis())));
            }
        }
        this.menuInventory.setStack(SLOT_ALT_INFO, GuiItemFactory.button(
                Items.SPYGLASS,
                Text.literal("Alt Info").formatted(Formatting.AQUA),
                altLore
        ));
        boolean whitelisted = this.guiService.moderationManager().isWhitelisted(ServerAccess.server(this.viewer), this.targetUuid, targetName);
        this.menuInventory.setStack(SLOT_WHITELIST, GuiItemFactory.button(
                whitelisted ? Items.LIME_DYE : Items.GRAY_DYE,
                Text.literal(whitelisted ? "Whitelist: ON" : "Whitelist: OFF").formatted(whitelisted ? Formatting.GREEN : Formatting.RED),
                List.of(Text.literal(whitelisted ? "Click to remove from whitelist" : "Click to add to whitelist"))
        ));
        this.menuInventory.setStack(SLOT_BACK, GuiItemFactory.backButton(this.guiService.configManager()));
        this.menuInventory.setStack(SLOT_REFRESH, GuiItemFactory.button(
                Items.SPYGLASS,
                Text.literal("Refresh").formatted(Formatting.AQUA),
                List.of(Text.literal("Reload latest player state"))
        ));
    }

    @Override
    protected void onMenuSlotClick(int slot) {
        if (slot == SLOT_BACK) {
            this.guiService.openPlayerList(this.viewer);
            return;
        }

        ServerPlayerEntity onlineTarget = ServerAccess.server(this.viewer).getPlayerManager().getPlayer(this.targetUuid);
        if (slot == SLOT_INV_MENU) {
            this.guiService.openInventoryMenu(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_KICK) {
            this.guiService.openConfirmAction(this.viewer, this.targetUuid, ConfirmActionType.KICK);
            return;
        }
        if (slot == SLOT_CREATIVE) {
            if (onlineTarget == null) {
                FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.target_offline"));
                return;
            }
            boolean setCreative = onlineTarget.interactionManager.getGameMode() != GameMode.CREATIVE;
            onlineTarget.changeGameMode(setCreative ? GameMode.CREATIVE : GameMode.SURVIVAL);
            AuditLogger.sensitive(this.guiService.configManager(),
                    AuditLogger.actor(this.viewer) + " set gamemode " + (setCreative ? "creative" : "survival")
                            + " for " + onlineTarget.getGameProfile().name() + " (" + onlineTarget.getUuidAsString() + ")");
            FeedbackUtil.success(this.viewer, this.guiService.configManager(),
                    Text.literal("Target gamemode set to " + (setCreative ? "creative." : "survival.")));
            this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_OP_PERM) {
            String name = targetName();
            boolean currently = this.guiService.moderationManager().isPermanentOp(ServerAccess.server(this.viewer), this.targetUuid, name);
            boolean changed = this.guiService.moderationManager().setPermanentOp(this.viewer, this.targetUuid, name, !currently);
            if (changed) {
                FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.literal(!currently ? "Permanent OP granted." : "Permanent OP removed."));
            } else {
                FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.literal("OP state unchanged."));
            }
            this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_OP_TEMP) {
            if (this.guiService.moderationManager().hasActiveTempOp(this.targetUuid)) {
                String name = targetName();
                boolean changed = this.guiService.moderationManager().revokeTempOp(this.viewer, this.targetUuid, name);
                if (changed) {
                    FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.literal("Temp OP revoked."));
                } else {
                    FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.literal("Temp OP not active."));
                }
                this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
            } else {
                this.guiService.openTempOpDuration(this.viewer, this.targetUuid);
            }
            return;
        }
        if (slot == SLOT_FREEZE) {
            if (onlineTarget == null) {
                FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.target_offline"));
                return;
            }
            boolean frozen = this.guiService.moderationManager().isFrozen(this.targetUuid);
            if (frozen) {
                this.guiService.moderationManager().unfreeze(this.viewer, onlineTarget);
                FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.unfreeze"));
            } else {
                this.guiService.moderationManager().freeze(this.viewer, onlineTarget);
                FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.freeze"));
            }
            this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_MUTE) {
            if (onlineTarget == null) {
                FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.target_offline"));
                return;
            }
            boolean muted = this.guiService.moderationManager().isMuted(this.targetUuid);
            if (muted) {
                this.guiService.moderationManager().unmute(this.viewer, onlineTarget);
                FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.unmute"));
            } else {
                this.guiService.moderationManager().mute(this.viewer, onlineTarget);
                FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.mute"));
            }
            this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_WATCHLIST) {
            String name = targetName();
            boolean watchlisted = this.guiService.moderationManager().isWatchlisted(this.targetUuid);
            if (watchlisted) {
                this.guiService.moderationManager().removeWatchlist(this.viewer, this.targetUuid);
                FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.literal("Removed from watchlist."));
            } else {
                this.guiService.moderationManager().addWatchlist(this.viewer, this.targetUuid, name, "Added via GUI");
                FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.literal("Added to watchlist."));
            }
            this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_ROLLBACK) {
            this.guiService.openRollbackLite(this.viewer, this.targetUuid, 0);
            return;
        }
        if (slot == SLOT_TP_TO_TARGET) {
            if (onlineTarget == null) {
                FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.target_offline"));
                return;
            }
            this.viewer.teleport(onlineTarget.getEntityWorld(), onlineTarget.getX(), onlineTarget.getY(), onlineTarget.getZ(), Set.of(), onlineTarget.getYaw(), onlineTarget.getPitch(), false);
            AuditLogger.sensitive(this.guiService.configManager(),
                    AuditLogger.actor(this.viewer) + " teleported to " + onlineTarget.getGameProfile().name() + " (" + onlineTarget.getUuidAsString() + ")");
            this.guiService.moderationManager().recordStaffAction(this.viewer, "teleport_review", "to " + onlineTarget.getGameProfile().name());
            FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.tp.to"));
            return;
        }
        if (slot == SLOT_TP_TARGET_TO_ADMIN) {
            if (onlineTarget == null) {
                FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.target_offline"));
                return;
            }
            Vec3d pos = new Vec3d(this.viewer.getX(), this.viewer.getY(), this.viewer.getZ());
            onlineTarget.teleport(this.viewer.getEntityWorld(), pos.x, pos.y, pos.z, Set.of(), this.viewer.getYaw(), this.viewer.getPitch(), false);
            AuditLogger.sensitive(this.guiService.configManager(),
                    AuditLogger.actor(this.viewer) + " teleported " + onlineTarget.getGameProfile().name() + " to self");
            this.guiService.moderationManager().recordStaffAction(this.viewer, "teleport_review", "summon " + onlineTarget.getGameProfile().name());
            FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.tp.here"));
            return;
        }
        if (slot == SLOT_NOTES) {
            this.guiService.openNotes(this.viewer, this.targetUuid, 0);
            return;
        }
        if (slot == SLOT_OVERVIEW) {
            this.guiService.openPlayerOverview(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_HISTORY) {
            this.guiService.openPlayerActionHistory(this.viewer, this.targetUuid, 0);
            return;
        }
        if (slot == SLOT_SNAPSHOTS) {
            this.guiService.openPlayerSnapshots(this.viewer, this.targetUuid, 0);
            return;
        }
        if (slot == SLOT_BAN) {
            if (this.guiService.moderationManager().isBanned(this.targetUuid)) {
                String name = onlineTarget == null ? this.targetUuid.toString() : onlineTarget.getGameProfile().name();
                boolean changed = this.guiService.moderationManager().unban(this.viewer, this.targetUuid, name);
                if (changed) {
                    FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.literal("Target unbanned."));
                } else {
                    FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.literal("Target is not banned."));
                }
            } else {
                String name = onlineTarget == null ? this.targetUuid.toString() : onlineTarget.getGameProfile().name();
                this.guiService.moderationManager().ban(this.viewer, this.targetUuid, name, "", true, 0L);
                FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.literal("Target permanently banned."));
            }
            this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_TEMP_BAN) {
            this.guiService.openTempBanDuration(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_HEAL) {
            if (onlineTarget == null) {
                FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.target_offline"));
                return;
            }
            onlineTarget.setHealth(onlineTarget.getMaxHealth());
            AuditLogger.sensitive(this.guiService.configManager(),
                    AuditLogger.actor(this.viewer) + " healed " + onlineTarget.getGameProfile().name() + " (" + onlineTarget.getUuidAsString() + ")");
            this.guiService.moderationManager().recordStaffAction(this.viewer, "heal", onlineTarget.getGameProfile().name());
            FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.literal("Target healed."));
            this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_FEED) {
            if (onlineTarget == null) {
                FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.target_offline"));
                return;
            }
            onlineTarget.getHungerManager().setFoodLevel(20);
            onlineTarget.getHungerManager().setSaturationLevel(20.0F);
            AuditLogger.sensitive(this.guiService.configManager(),
                    AuditLogger.actor(this.viewer) + " fed " + onlineTarget.getGameProfile().name() + " (" + onlineTarget.getUuidAsString() + ")");
            this.guiService.moderationManager().recordStaffAction(this.viewer, "feed", onlineTarget.getGameProfile().name());
            FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.literal("Target fed."));
            this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_STARVE) {
            if (onlineTarget == null) {
                FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.target_offline"));
                return;
            }
            onlineTarget.getHungerManager().setFoodLevel(0);
            onlineTarget.getHungerManager().setSaturationLevel(0.0F);
            AuditLogger.sensitive(this.guiService.configManager(),
                    AuditLogger.actor(this.viewer) + " starved " + onlineTarget.getGameProfile().name() + " (" + onlineTarget.getUuidAsString() + ")");
            this.guiService.moderationManager().recordStaffAction(this.viewer, "starve", onlineTarget.getGameProfile().name());
            FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.literal("Target starved."));
            this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_KILL) {
            if (onlineTarget == null) {
                FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.target_offline"));
                return;
            }
            onlineTarget.damage((net.minecraft.server.world.ServerWorld) onlineTarget.getEntityWorld(), onlineTarget.getDamageSources().outOfWorld(), Float.MAX_VALUE);
            AuditLogger.sensitive(this.guiService.configManager(),
                    AuditLogger.actor(this.viewer) + " killed " + onlineTarget.getGameProfile().name() + " (" + onlineTarget.getUuidAsString() + ")");
            this.guiService.moderationManager().recordStaffAction(this.viewer, "kill", onlineTarget.getGameProfile().name());
            FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.literal("Target killed."));
            this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_ALT_INFO) {
            List<com.b1progame.adminmod.moderation.ModerationManager.AltMatch> altMatches =
                    this.guiService.moderationManager().listPossibleAltMatches(this.targetUuid, 10);
            if (altMatches.isEmpty()) {
                FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.literal("No possible alts found for this player."));
            } else {
                this.viewer.sendMessage(Text.literal("Possible alts (" + altMatches.size() + "):").formatted(Formatting.AQUA), false);
                for (com.b1progame.adminmod.moderation.ModerationManager.AltMatch match : altMatches) {
                    this.viewer.sendMessage(Text.literal("- Name: " + match.name()).formatted(Formatting.GRAY), false);
                    this.viewer.sendMessage(Text.literal("  UUID: " + match.uuid()).formatted(Formatting.DARK_GRAY), false);
                    this.viewer.sendMessage(Text.literal("  Last Seen: " + java.time.Instant.ofEpochMilli(match.lastSeenEpochMillis())).formatted(Formatting.DARK_GRAY), false);
                }
            }
            return;
        }
        if (slot == SLOT_WHITELIST) {
            String name = targetName();
            boolean currently = this.guiService.moderationManager().isWhitelisted(ServerAccess.server(this.viewer), this.targetUuid, name);
            boolean changed = this.guiService.moderationManager().setWhitelisted(this.viewer, this.targetUuid, name, !currently);
            if (!changed) {
                FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.literal("Whitelist state unchanged."));
            } else {
                FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.literal(!currently ? "Added to whitelist." : "Removed from whitelist."));
            }
            this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_REFRESH) {
            this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
        }
    }

    private String targetName() {
        PlayerBrowserEntry entry = resolveEntry();
        if (entry != null && entry.name != null && !entry.name.isBlank()) {
            return entry.name;
        }
        var history = this.guiService.moderationManager().getPlayerHistory(this.targetUuid);
        if (history != null && history.lastKnownName != null && !history.lastKnownName.isBlank()) {
            return history.lastKnownName;
        }
        return this.targetUuid.toString();
    }

    private PlayerBrowserEntry resolveEntry() {
        for (PlayerBrowserEntry entry : this.guiService.playerDataRepository().loadEntries(ServerAccess.server(this.viewer))) {
            if (entry.uuid.equals(this.targetUuid)) {
                return entry;
            }
        }
        return null;
    }

    private ServerPlayerEntity resolveOnlineTarget() {
        return ServerAccess.server(this.viewer).getPlayerManager().getPlayer(this.targetUuid);
    }
}
