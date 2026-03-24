package com.b1progame.adminmod.moderation;

import com.b1progame.adminmod.AdminMod;
import com.b1progame.adminmod.config.ConfigManager;
import com.b1progame.adminmod.state.BanRecordData;
import com.b1progame.adminmod.state.InventorySnapshotData;
import com.b1progame.adminmod.state.ModerationActionData;
import com.b1progame.adminmod.state.ModerationNoteData;
import com.b1progame.adminmod.state.PersistentStateData;
import com.b1progame.adminmod.state.PlayerHistoryData;
import com.b1progame.adminmod.state.RollbackEntryData;
import com.b1progame.adminmod.state.SnapshotStackData;
import com.b1progame.adminmod.state.StaffMailEntryData;
import com.b1progame.adminmod.state.StaffSessionActionData;
import com.b1progame.adminmod.state.StaffSessionData;
import com.b1progame.adminmod.state.StateManager;
import com.b1progame.adminmod.state.TempOpEntryData;
import com.b1progame.adminmod.state.WatchlistEntryData;
import com.b1progame.adminmod.state.XrayRecordData;
import com.b1progame.adminmod.util.AuditLogger;
import com.b1progame.adminmod.util.PermissionUtil;
import com.b1progame.adminmod.util.ServerAccess;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.WhitelistEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ModerationManager {
    private static final int FREEZE_ACTIONBAR_TICKS = 20;
    private static final int HISTORY_UPDATE_TICKS = 100;

    private final ConfigManager configManager;
    private final StateManager stateManager;
    private final Map<UUID, FrozenState> freezeAnchors = new HashMap<>();
    private int freezeActionbarTicker = 0;
    private int historyTicker = 0;

    public ModerationManager(ConfigManager configManager, StateManager stateManager) {
        this.configManager = configManager;
        this.stateManager = stateManager;
        registerInteractionGuards();
        registerChatGuards();
        registerXrayTracker();
    }

    public synchronized void onServerStarted(MinecraftServer server) {
        cleanupExpiredTempOps(server);
        cleanupExpiredBans(server);
        cleanupExpiredXrayRecords(server);
    }

    public synchronized void handleJoin(ServerPlayerEntity player) {
        if (checkBanOnJoin(player)) {
            return;
        }
        restoreSilentSettingsAfterVanishRejoin(player);
        updateHistory(player, true, false);
        openSessionIfStaff(player, "join");
        deliverPendingStaffMail(player);
        if (isFrozen(player.getUuid())) {
            if (!this.configManager.get().freeze.persist_through_relog) {
                this.stateManager.state().frozenPlayers.remove(player.getUuidAsString());
                this.stateManager.markDirty(ServerAccess.server(player));
            } else {
                this.freezeAnchors.put(player.getUuid(), FrozenState.fromPlayer(player));
            }
        }
        emitJoinNotifications(player);
    }

    public synchronized void handleDisconnect(ServerPlayerEntity player) {
        updateHistory(player, false, true);
        this.freezeAnchors.remove(player.getUuid());
        closeSessionIfOpen(player, "disconnect");
    }

    public synchronized void handleWorldChange(ServerPlayerEntity player) {
        if (isFrozen(player.getUuid())) {
            this.freezeAnchors.put(player.getUuid(), FrozenState.fromPlayer(player));
        }
        updateHistory(player, true, false);
    }

    public synchronized void tick(MinecraftServer server) {
        this.freezeActionbarTicker++;
        boolean showActionbar = this.freezeActionbarTicker >= FREEZE_ACTIONBAR_TICKS;
        if (showActionbar) {
            this.freezeActionbarTicker = 0;
        }

        this.historyTicker++;
        boolean updateHistoryNow = this.historyTicker >= HISTORY_UPDATE_TICKS;
        if (updateHistoryNow) {
            this.historyTicker = 0;
        }

        enforceFrozenPlayers(server, showActionbar);
        cleanupExpiredTempOps(server);
        cleanupExpiredBans(server);
        cleanupExpiredXrayRecords(server);

        if (updateHistoryNow) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                updateHistory(player, true, false);
            }
        }
    }

    public synchronized boolean freeze(ServerPlayerEntity actor, ServerPlayerEntity target) {
        if (target == null) {
            return false;
        }
        String uuid = target.getUuidAsString();
        if (!this.stateManager.state().frozenPlayers.add(uuid)) {
            return false;
        }
        this.freezeAnchors.put(target.getUuid(), FrozenState.fromPlayer(target));
        this.stateManager.markDirty(ServerAccess.server(target));
        logAction(actor, target, "freeze", "");
        recordStaffAction(actor, "freeze", target.getGameProfile().name());
        return true;
    }

    public synchronized boolean unfreeze(ServerPlayerEntity actor, ServerPlayerEntity target) {
        if (target == null) {
            return false;
        }
        String uuid = target.getUuidAsString();
        if (!this.stateManager.state().frozenPlayers.remove(uuid)) {
            return false;
        }
        this.freezeAnchors.remove(target.getUuid());
        this.stateManager.markDirty(ServerAccess.server(target));
        logAction(actor, target, "unfreeze", "");
        recordStaffAction(actor, "unfreeze", target.getGameProfile().name());
        return true;
    }

    public synchronized boolean mute(ServerPlayerEntity actor, ServerPlayerEntity target) {
        if (target == null) {
            return false;
        }
        String uuid = target.getUuidAsString();
        if (!this.stateManager.state().mutedPlayers.add(uuid)) {
            return false;
        }
        this.stateManager.markDirty(ServerAccess.server(target));
        logAction(actor, target, "mute", "");
        recordStaffAction(actor, "mute", target.getGameProfile().name());
        return true;
    }

    public synchronized boolean unmute(ServerPlayerEntity actor, ServerPlayerEntity target) {
        if (target == null) {
            return false;
        }
        String uuid = target.getUuidAsString();
        if (!this.stateManager.state().mutedPlayers.remove(uuid)) {
            return false;
        }
        this.stateManager.markDirty(ServerAccess.server(target));
        logAction(actor, target, "unmute", "");
        recordStaffAction(actor, "unmute", target.getGameProfile().name());
        return true;
    }

    public synchronized boolean toggleStaffChat(ServerPlayerEntity actor) {
        Set<String> toggles = this.stateManager.state().staffChatToggles;
        boolean enabled;
        if (toggles.contains(actor.getUuidAsString())) {
            toggles.remove(actor.getUuidAsString());
            enabled = false;
        } else {
            toggles.add(actor.getUuidAsString());
            enabled = true;
        }
        this.stateManager.markDirty(ServerAccess.server(actor));
        if (this.configManager.get().logging.staff_chat_toggles) {
            AuditLogger.sensitive(this.configManager, AuditLogger.actor(actor) + " set staff chat mode to " + enabled);
        }
        recordStaffAction(actor, "staffchat_toggle", String.valueOf(enabled));
        return enabled;
    }

    public synchronized boolean isStaffChatEnabled(UUID uuid) {
        return this.stateManager.state().staffChatToggles.contains(uuid.toString());
    }

    public synchronized boolean isFrozen(UUID uuid) {
        return this.stateManager.state().frozenPlayers.contains(uuid.toString());
    }

    public synchronized boolean isMuted(UUID uuid) {
        return this.stateManager.state().mutedPlayers.contains(uuid.toString());
    }

    public synchronized int notesCount(UUID targetUuid) {
        return this.stateManager.state().moderationNotes.getOrDefault(targetUuid.toString(), List.of()).size();
    }

    public synchronized List<ModerationNoteData> listNotes(UUID targetUuid) {
        return new ArrayList<>(this.stateManager.state().moderationNotes.getOrDefault(targetUuid.toString(), List.of()));
    }

    public synchronized ModerationNoteData addNote(ServerPlayerEntity actor, UUID targetUuid, String text) {
        return addModerationEntry(actor, targetUuid, text, "note", "general");
    }

    public synchronized ModerationNoteData addWarning(ServerPlayerEntity actor, UUID targetUuid, String text, String category) {
        return addModerationEntry(actor, targetUuid, text, "warning", normalizeCategory(category));
    }

    public synchronized boolean removeNote(ServerPlayerEntity actor, UUID targetUuid, int noteId) {
        String key = targetUuid.toString();
        List<ModerationNoteData> notes = this.stateManager.state().moderationNotes.get(key);
        if (notes == null) {
            return false;
        }
        Iterator<ModerationNoteData> iterator = notes.iterator();
        while (iterator.hasNext()) {
            ModerationNoteData note = iterator.next();
            if (note.id == noteId) {
                iterator.remove();
                if (actor != null) {
                    this.stateManager.markDirty(ServerAccess.server(actor));
                }
                AuditLogger.sensitive(this.configManager, actorLabel(actor) + " removed note #" + noteId + " for " + key);
                return true;
            }
        }
        return false;
    }

    public synchronized boolean grantTempOp(ServerPlayerEntity actor, ServerPlayerEntity target, long durationSeconds) {
        if (target == null) {
            return false;
        }
        MinecraftServer server = ServerAccess.server(target);
        PlayerConfigEntry entry = new PlayerConfigEntry(target.getGameProfile());
        boolean hadOpBefore = server.getPlayerManager().isOperator(entry);
        server.getPlayerManager().addToOperators(entry);

        TempOpEntryData temp = new TempOpEntryData();
        temp.expiresAtEpochMillis = System.currentTimeMillis() + (durationSeconds * 1000L);
        temp.hadOpBeforeGrant = hadOpBefore;
        temp.grantedByName = actor == null ? "console" : actor.getGameProfile().name();
        temp.grantedByUuid = actor == null ? "" : actor.getUuidAsString();
        this.stateManager.state().tempOpEntries.put(target.getUuidAsString(), temp);
        this.stateManager.markDirty(server);

        logAction(actor, target, "temp_op_grant", "durationSeconds=" + durationSeconds, temp.expiresAtEpochMillis);
        recordStaffAction(actor, "temp_op_grant", target.getGameProfile().name() + " " + durationSeconds + "s");
        return true;
    }

    public synchronized boolean hasActiveTempOp(UUID playerUuid) {
        return this.stateManager.state().tempOpEntries.containsKey(playerUuid.toString());
    }

    public synchronized long tempOpRemainingMillis(UUID playerUuid) {
        TempOpEntryData entry = this.stateManager.state().tempOpEntries.get(playerUuid.toString());
        if (entry == null) {
            return 0L;
        }
        return Math.max(0L, entry.expiresAtEpochMillis - System.currentTimeMillis());
    }

    public synchronized boolean revokeTempOp(ServerPlayerEntity actor, UUID targetUuid, String targetName) {
        TempOpEntryData entry = this.stateManager.state().tempOpEntries.remove(targetUuid.toString());
        if (entry == null) {
            return false;
        }
        if (actor != null) {
            MinecraftServer server = ServerAccess.server(actor);
            if (!entry.hadOpBeforeGrant) {
                server.getPlayerManager().removeFromOperators(new PlayerConfigEntry(targetUuid, targetName));
            }
            this.stateManager.markDirty(server);
        }
        logAction(actor, targetUuid, targetName, "temp_op_revoke", "", 0L);
        recordStaffAction(actor, "temp_op_revoke", targetName);
        return true;
    }

    public synchronized boolean isPermanentOp(MinecraftServer server, UUID targetUuid, String targetName) {
        return server.getPlayerManager().isOperator(new PlayerConfigEntry(targetUuid, targetName == null ? "" : targetName));
    }

    public synchronized boolean setPermanentOp(ServerPlayerEntity actor, UUID targetUuid, String targetName, boolean enabled) {
        MinecraftServer server = actor == null ? AdminMod.get().server() : ServerAccess.server(actor);
        if (server == null) {
            return false;
        }
        PlayerConfigEntry entry = new PlayerConfigEntry(targetUuid, targetName == null ? "" : targetName);
        boolean currentlyOp = server.getPlayerManager().isOperator(entry);
        if (enabled == currentlyOp) {
            return false;
        }
        if (enabled) {
            server.getPlayerManager().addToOperators(entry);
            logAction(actor, targetUuid, targetName, "op_grant_permanent", "", 0L);
            recordStaffAction(actor, "op_grant_permanent", targetName);
        } else {
            server.getPlayerManager().removeFromOperators(entry);
            this.stateManager.state().tempOpEntries.remove(targetUuid.toString());
            logAction(actor, targetUuid, targetName, "op_remove_permanent", "", 0L);
            recordStaffAction(actor, "op_remove_permanent", targetName);
        }
        if (actor != null) {
            this.stateManager.markDirty(server);
        }
        return true;
    }

    public synchronized InventorySnapshotData createSnapshot(
            ServerPlayerEntity actor,
            UUID targetUuid,
            String targetName,
            boolean inventory,
            boolean ender
    ) {
        PersistentStateData state = this.stateManager.state();
        InventorySnapshotData snapshot = new InventorySnapshotData();
        snapshot.id = state.nextSnapshotId++;
        snapshot.targetUuid = targetUuid.toString();
        snapshot.targetName = targetName;
        snapshot.createdByUuid = actor == null ? "" : actor.getUuidAsString();
        snapshot.createdByName = actor == null ? "console" : actor.getGameProfile().name();
        snapshot.createdAtEpochMillis = System.currentTimeMillis();
        snapshot.includeInventory = inventory;
        snapshot.includeEnderChest = ender;

        ServerPlayerEntity online = actor == null ? null : ServerAccess.server(actor).getPlayerManager().getPlayer(targetUuid);
        if (online != null) {
            if (inventory) {
                snapshot.inventoryStacks = snapshotPlayerInventory(online.getInventory());
            }
            if (ender) {
                snapshot.enderStacks = snapshotSimpleInventory(online.getEnderChestInventory(), 27);
            }
        } else if (actor != null) {
            var repository = AdminMod.get().guiService().playerDataRepository();
            if (inventory) {
                repository.loadOfflineInventory(ServerAccess.server(actor), targetUuid)
                        .ifPresent(inv -> snapshot.inventoryStacks = snapshotSimpleInventory(inv, 41));
            }
            if (ender) {
                repository.loadOfflineEnderChest(ServerAccess.server(actor), targetUuid)
                        .ifPresent(inv -> snapshot.enderStacks = snapshotSimpleInventory(inv, 27));
            }
        }

        state.inventorySnapshots.add(snapshot);
        if (actor != null) {
            this.stateManager.markDirty(ServerAccess.server(actor));
            logAction(actor, targetUuid, targetName, "inventory_snapshot",
                    "inventory=" + inventory + ",ender=" + ender + ",snapshotId=" + snapshot.id, 0L);
            recordStaffAction(actor, "snapshot_create", targetName + " #" + snapshot.id);
        }
        return snapshot;
    }

    public synchronized List<InventorySnapshotData> listSnapshots(UUID targetUuid) {
        String key = targetUuid.toString();
        List<InventorySnapshotData> out = new ArrayList<>();
        for (InventorySnapshotData data : this.stateManager.state().inventorySnapshots) {
            if (data.targetUuid.equals(key)) {
                out.add(data);
            }
        }
        out.sort(Comparator.comparingLong((InventorySnapshotData s) -> s.createdAtEpochMillis).reversed());
        return out;
    }

    public synchronized InventorySnapshotData getSnapshot(long snapshotId) {
        for (InventorySnapshotData snapshot : this.stateManager.state().inventorySnapshots) {
            if (snapshot.id == snapshotId) {
                return snapshot;
            }
        }
        return null;
    }

    public synchronized List<String> compareSnapshots(
            ServerPlayerEntity actor,
            UUID targetUuid,
            long snapshotAId,
            long snapshotBId,
            boolean enderChest
    ) {
        InventorySnapshotData first = getSnapshot(snapshotAId);
        InventorySnapshotData second = getSnapshot(snapshotBId);
        if (first == null || second == null || !first.targetUuid.equals(targetUuid.toString()) || !second.targetUuid.equals(targetUuid.toString())) {
            return List.of("Snapshot pair is invalid for this player.");
        }
        List<SnapshotStackData> a = enderChest ? first.enderStacks : first.inventoryStacks;
        List<SnapshotStackData> b = enderChest ? second.enderStacks : second.inventoryStacks;
        List<String> output = buildStackDiffLines(a, b);
        String target = historyNameFor(targetUuid);
        String type = enderChest ? "ender" : "inventory";
        AuditLogger.sensitive(this.configManager, actorLabel(actor) + " compared snapshots #" + snapshotAId + " vs #" + snapshotBId + " for " + target + " (" + type + ")");
        recordStaffAction(actor, "snapshot_compare", target + " " + type + " #" + snapshotAId + " vs #" + snapshotBId);
        return output;
    }

    public synchronized List<String> compareLatestToCurrent(ServerPlayerEntity actor, UUID targetUuid, boolean enderChest) {
        List<InventorySnapshotData> snapshots = listSnapshots(targetUuid);
        if (snapshots.isEmpty()) {
            return List.of("No snapshots found for this player.");
        }
        InventorySnapshotData latest = snapshots.get(0);
        List<SnapshotStackData> current = currentStacksFromOnline(actor, targetUuid, enderChest);
        if (current == null) {
            return List.of("Target must be online for latest-vs-current compare.");
        }
        List<SnapshotStackData> base = enderChest ? latest.enderStacks : latest.inventoryStacks;
        List<String> output = buildStackDiffLines(base, current);
        AuditLogger.sensitive(this.configManager, actorLabel(actor) + " compared latest snapshot #" + latest.id + " vs current for " + historyNameFor(targetUuid));
        recordStaffAction(actor, "snapshot_compare_current", historyNameFor(targetUuid) + " #" + latest.id);
        return output;
    }

    public synchronized List<String> compareLatestToPrevious(ServerPlayerEntity actor, UUID targetUuid, boolean enderChest) {
        List<InventorySnapshotData> snapshots = listSnapshots(targetUuid);
        if (snapshots.size() < 2) {
            return List.of("Need at least two snapshots.");
        }
        InventorySnapshotData latest = snapshots.get(0);
        InventorySnapshotData previous = snapshots.get(1);
        List<SnapshotStackData> a = enderChest ? previous.enderStacks : previous.inventoryStacks;
        List<SnapshotStackData> b = enderChest ? latest.enderStacks : latest.inventoryStacks;
        List<String> output = buildStackDiffLines(a, b);
        AuditLogger.sensitive(this.configManager, actorLabel(actor) + " compared latest snapshot #" + latest.id + " vs previous #" + previous.id + " for " + historyNameFor(targetUuid));
        recordStaffAction(actor, "snapshot_compare_prev", historyNameFor(targetUuid) + " #" + previous.id + " vs #" + latest.id);
        return output;
    }

    public synchronized List<ModerationActionData> listActionHistory(UUID targetUuid) {
        String key = targetUuid.toString();
        List<ModerationActionData> out = new ArrayList<>();
        for (ModerationActionData action : this.stateManager.state().moderationActions) {
            if (action.targetUuid.equals(key)) {
                out.add(action);
            }
        }
        out.sort(Comparator.comparingLong((ModerationActionData a) -> a.createdAtEpochMillis).reversed());
        return out;
    }

    public synchronized PlayerHistoryData getPlayerHistory(UUID targetUuid) {
        return this.stateManager.state().playerHistory.get(targetUuid.toString());
    }

    public synchronized UUID findPlayerUuidByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (Map.Entry<String, PlayerHistoryData> entry : this.stateManager.state().playerHistory.entrySet()) {
            if (entry.getValue().lastKnownName != null && entry.getValue().lastKnownName.equalsIgnoreCase(name)) {
                try {
                    return UUID.fromString(entry.getKey());
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    public synchronized boolean isBanned(UUID targetUuid) {
        BanRecordData ban = this.stateManager.state().activeBans.get(targetUuid.toString());
        if (ban == null) {
            return false;
        }
        if (ban.permanent) {
            return true;
        }
        return ban.expiresAtEpochMillis > System.currentTimeMillis();
    }

    public synchronized BanRecordData getBan(UUID targetUuid) {
        return this.stateManager.state().activeBans.get(targetUuid.toString());
    }

    public synchronized boolean isWhitelisted(MinecraftServer server, UUID targetUuid, String targetName) {
        return server.getPlayerManager().getWhitelist().isAllowed(new PlayerConfigEntry(targetUuid, targetName == null ? "" : targetName));
    }

    public synchronized boolean setWhitelisted(ServerPlayerEntity actor, UUID targetUuid, String targetName, boolean enabled) {
        MinecraftServer server = actor == null ? AdminMod.get().server() : ServerAccess.server(actor);
        if (server == null) {
            return false;
        }
        PlayerConfigEntry entry = new PlayerConfigEntry(targetUuid, targetName == null ? "" : targetName);
        boolean currently = server.getPlayerManager().getWhitelist().isAllowed(entry);
        if (currently == enabled) {
            return false;
        }
        if (enabled) {
            server.getPlayerManager().getWhitelist().add(new WhitelistEntry(entry));
            logAction(actor, targetUuid, targetName, "whitelist_add", "", 0L);
            recordStaffAction(actor, "whitelist_add", targetName);
            AuditLogger.sensitive(this.configManager, actorLabel(actor) + " added " + targetName + " (" + targetUuid + ") to whitelist");
        } else {
            server.getPlayerManager().getWhitelist().remove(entry);
            logAction(actor, targetUuid, targetName, "whitelist_remove", "", 0L);
            recordStaffAction(actor, "whitelist_remove", targetName);
            AuditLogger.sensitive(this.configManager, actorLabel(actor) + " removed " + targetName + " (" + targetUuid + ") from whitelist");
        }
        if (actor != null) {
            this.stateManager.markDirty(server);
        }
        return true;
    }

    public synchronized List<String> listPossibleAlts(UUID targetUuid, int limit) {
        List<AltMatch> matches = listPossibleAltMatches(targetUuid, limit);
        List<String> out = new ArrayList<>();
        for (AltMatch match : matches) {
            out.add(match.name + " (" + match.uuid + ") lastSeen=" + Instant.ofEpochMilli(match.lastSeenEpochMillis));
        }
        return out;
    }

    public synchronized List<AltMatch> listPossibleAltMatches(UUID targetUuid, int limit) {
        int safeLimit = Math.max(1, limit);
        PlayerHistoryData target = this.stateManager.state().playerHistory.get(targetUuid.toString());
        if (target == null || target.lastKnownIp == null || target.lastKnownIp.isBlank()) {
            return List.of();
        }
        String ip = target.lastKnownIp;
        List<PlayerHistoryData> matches = new ArrayList<>();
        List<String> uuids = new ArrayList<>();
        for (Map.Entry<String, PlayerHistoryData> entry : this.stateManager.state().playerHistory.entrySet()) {
            if (entry.getKey().equals(targetUuid.toString())) {
                continue;
            }
            PlayerHistoryData history = entry.getValue();
            if (history == null || history.lastKnownIp == null || history.lastKnownIp.isBlank()) {
                continue;
            }
            if (!ip.equals(history.lastKnownIp)) {
                continue;
            }
            matches.add(history);
            uuids.add(entry.getKey());
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            order.add(i);
        }
        order.sort((a, b) -> Long.compare(matches.get(b).lastSeenEpochMillis, matches.get(a).lastSeenEpochMillis));
        List<AltMatch> out = new ArrayList<>();
        for (int i = 0; i < order.size() && out.size() < safeLimit; i++) {
            int index = order.get(i);
            PlayerHistoryData history = matches.get(index);
            String uuid = uuids.get(index);
            String name = history.lastKnownName == null || history.lastKnownName.isBlank() ? uuid : history.lastKnownName;
            out.add(new AltMatch(name, uuid, history.lastSeenEpochMillis));
        }
        return out;
    }

    public record AltMatch(String name, String uuid, long lastSeenEpochMillis) {
    }

    public synchronized boolean ban(
            ServerPlayerEntity actor,
            UUID targetUuid,
            String targetName,
            String reason,
            boolean permanent,
            long expiresAtEpochMillis
    ) {
        BanRecordData record = new BanRecordData();
        record.targetUuid = targetUuid.toString();
        record.targetName = targetName;
        record.actorUuid = actor == null ? "" : actor.getUuidAsString();
        record.actorName = actor == null ? "console" : actor.getGameProfile().name();
        record.reason = reason == null ? "" : reason;
        record.permanent = permanent;
        record.createdAtEpochMillis = System.currentTimeMillis();
        record.expiresAtEpochMillis = expiresAtEpochMillis;
        this.stateManager.state().activeBans.put(record.targetUuid, record);
        if (actor != null) {
            this.stateManager.markDirty(ServerAccess.server(actor));
            ServerPlayerEntity online = ServerAccess.server(actor).getPlayerManager().getPlayer(targetUuid);
            if (online != null) {
                String base = permanent ? this.configManager.get().bans.ban_join_denied_message : this.configManager.get().bans.temp_ban_join_denied_message;
                online.networkHandler.disconnect(Text.literal(base + (reason.isBlank() ? "" : " (" + reason + ")")));
            }
        }
        logAction(actor, targetUuid, targetName, permanent ? "ban" : "temp_ban", reason, permanent ? 0L : expiresAtEpochMillis);
        recordStaffAction(actor, permanent ? "ban" : "temp_ban", targetName);
        return true;
    }

    public synchronized boolean unban(ServerPlayerEntity actor, UUID targetUuid, String targetName) {
        BanRecordData removed = this.stateManager.state().activeBans.remove(targetUuid.toString());
        if (removed == null) {
            return false;
        }
        if (actor != null) {
            this.stateManager.markDirty(ServerAccess.server(actor));
        }
        logAction(actor, targetUuid, targetName, "unban", "", 0L);
        recordStaffAction(actor, "unban", targetName);
        return true;
    }

    public synchronized void setXrayTrackerEnabled(ServerPlayerEntity actor, boolean enabled) {
        this.stateManager.state().xrayTrackerEnabled = enabled;
        if (actor != null) {
            this.stateManager.markDirty(ServerAccess.server(actor));
            AuditLogger.sensitive(this.configManager, AuditLogger.actor(actor) + " set xray tracker to " + enabled);
            recordStaffAction(actor, "xray_tracker_toggle", String.valueOf(enabled));
        }
    }

    public synchronized boolean isXrayTrackerEnabled() {
        return this.stateManager.state().xrayTrackerEnabled && this.configManager.get().xray_tracker.enabled;
    }

    public synchronized boolean isXrayOreEnabled(String oreId) {
        Boolean override = this.stateManager.state().xrayOreEnabled.get(oreId);
        if (override != null) {
            return override;
        }
        return this.configManager.get().xray_tracker.tracked_ores.contains(oreId);
    }

    public synchronized void setXrayOreEnabled(ServerPlayerEntity actor, String oreId, boolean enabled) {
        this.stateManager.state().xrayOreEnabled.put(oreId, enabled);
        if (actor != null) {
            this.stateManager.markDirty(ServerAccess.server(actor));
            AuditLogger.sensitive(this.configManager, AuditLogger.actor(actor) + " set xray ore " + oreId + "=" + enabled);
            recordStaffAction(actor, "xray_ore_toggle", oreId + "=" + enabled);
        }
    }

    public synchronized int calculateXraySuspicionScore(UUID targetUuid) {
        if (!this.configManager.get().xray_tracker.suspicion_score_enabled) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int windowMinutes = Math.max(5, this.configManager.get().xray_tracker.suspicion_score_window_minutes);
        int halfLifeMinutes = Math.max(1, this.configManager.get().xray_tracker.suspicion_decay_half_life_minutes);
        long cutoff = now - (windowMinutes * 60_000L);
        List<XrayRecordData> records = new ArrayList<>();
        for (XrayRecordData record : this.stateManager.state().xrayRecords) {
            if (record.playerUuid.equals(targetUuid.toString()) && record.createdAtEpochMillis >= cutoff) {
                records.add(record);
            }
        }
        if (records.isEmpty()) {
            return 0;
        }
        records.sort(Comparator.comparingLong(r -> r.createdAtEpochMillis));

        double weighted = 0.0D;
        for (XrayRecordData record : records) {
            double ageMinutes = (now - record.createdAtEpochMillis) / 60_000.0D;
            double decay = Math.pow(0.5D, ageMinutes / halfLifeMinutes);
            weighted += oreWeight(record.oreBlockId) * decay;
        }

        int veins = estimateVeinCount(records, 12.0D, 120_000L);
        int shortGapHits = 0;
        long previousAt = -1L;
        for (XrayRecordData record : records) {
            if (previousAt > 0L && record.createdAtEpochMillis - previousAt <= 45_000L) {
                shortGapHits++;
            }
            previousAt = record.createdAtEpochMillis;
        }
        int score = 0;
        score += Math.min(65, (int) Math.round(weighted * 7.5D));
        score += Math.min(20, veins * 3);
        score += Math.min(15, shortGapHits * 3);
        return Math.max(0, Math.min(100, score));
    }

    public synchronized String suspicionLabel(int score) {
        if (score >= 80) {
            return "HIGH";
        }
        if (score >= 50) {
            return "MEDIUM";
        }
        if (score >= 25) {
            return "LOW";
        }
        return "NONE";
    }

    public synchronized List<XrayRecordData> listRecentXrayRecords(int limit) {
        List<XrayRecordData> out = new ArrayList<>(this.stateManager.state().xrayRecords);
        out.sort(Comparator.comparingLong((XrayRecordData x) -> x.createdAtEpochMillis).reversed());
        return out.subList(0, Math.min(limit, out.size()));
    }

    public synchronized List<XrayRecordData> listXrayRecordsFor(UUID playerUuid, int limit) {
        List<XrayRecordData> out = new ArrayList<>();
        for (XrayRecordData record : this.stateManager.state().xrayRecords) {
            if (record.playerUuid.equals(playerUuid.toString())) {
                out.add(record);
            }
        }
        out.sort(Comparator.comparingLong((XrayRecordData x) -> x.createdAtEpochMillis).reversed());
        return out.subList(0, Math.min(limit, out.size()));
    }

    public synchronized XrayRecordData findXrayRecordById(long id) {
        for (XrayRecordData record : this.stateManager.state().xrayRecords) {
            if (record.id == id) {
                return record;
            }
        }
        return null;
    }

    public synchronized boolean sendStaffChat(ServerPlayerEntity sender, String message) {
        if (sender == null) {
            return false;
        }
        MinecraftServer server = ServerAccess.server(sender);
        String prefix = this.configManager.get().staff_chat.prefix;
        Text text = Text.literal(prefix + " " + sender.getGameProfile().name() + ": " + message).formatted(Formatting.AQUA);
        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            if (PermissionUtil.canUseAdminGui(online, this.configManager)) {
                online.sendMessage(text, false);
            }
        }
        if (this.configManager.get().staff_chat.log_messages) {
            AuditLogger.sensitive(this.configManager, "[STAFFCHAT] " + sender.getGameProfile().name() + ": " + message);
        }
        return true;
    }

    public synchronized boolean isWatchlisted(UUID targetUuid) {
        return this.stateManager.state().watchlistEntries.containsKey(targetUuid.toString());
    }

    public synchronized boolean isSilentJoinEnabled(UUID staffUuid) {
        return this.stateManager.state().silentJoinToggles.getOrDefault(staffUuid.toString(), this.configManager.get().silent_connect_default);
    }

    public synchronized boolean isSilentDisconnectEnabled(UUID staffUuid) {
        return this.stateManager.state().silentDisconnectToggles.getOrDefault(staffUuid.toString(), this.configManager.get().silent_disconnect_default);
    }

    public synchronized boolean isConsoleChatEnabled(UUID staffUuid) {
        return this.stateManager.state().consoleChatToggles.getOrDefault(staffUuid.toString(), false);
    }

    public synchronized void setSilentJoin(ServerPlayerEntity actor, boolean enabled) {
        this.stateManager.state().silentJoinToggles.put(actor.getUuidAsString(), enabled);
        this.stateManager.markDirty(ServerAccess.server(actor));
        AuditLogger.sensitive(this.configManager, AuditLogger.actor(actor) + " set silent join to " + enabled);
        recordStaffAction(actor, "silent_join_toggle", String.valueOf(enabled));
    }

    public synchronized void setSilentDisconnect(ServerPlayerEntity actor, boolean enabled) {
        this.stateManager.state().silentDisconnectToggles.put(actor.getUuidAsString(), enabled);
        this.stateManager.markDirty(ServerAccess.server(actor));
        AuditLogger.sensitive(this.configManager, AuditLogger.actor(actor) + " set silent disconnect to " + enabled);
        recordStaffAction(actor, "silent_disconnect_toggle", String.valueOf(enabled));
    }

    public synchronized void setConsoleChat(ServerPlayerEntity actor, boolean enabled) {
        this.stateManager.state().consoleChatToggles.put(actor.getUuidAsString(), enabled);
        this.stateManager.markDirty(ServerAccess.server(actor));
        AuditLogger.sensitive(this.configManager, AuditLogger.actor(actor) + " set console chat mode to " + enabled);
        recordStaffAction(actor, "console_chat_toggle", String.valueOf(enabled));
    }

    public synchronized void beginVanishReconnectFlow(ServerPlayerEntity actor) {
        if (actor == null) {
            return;
        }
        String key = actor.getUuidAsString();
        this.stateManager.state().pendingSilentJoinRestore.put(key, isSilentJoinEnabled(actor.getUuid()));
        this.stateManager.state().pendingSilentDisconnectRestore.put(key, isSilentDisconnectEnabled(actor.getUuid()));
        this.stateManager.state().silentJoinToggles.put(key, true);
        this.stateManager.state().silentDisconnectToggles.put(key, true);
        this.stateManager.markDirty(ServerAccess.server(actor));
        AuditLogger.sensitive(this.configManager, AuditLogger.actor(actor) + " started vanish reconnect flow");
        recordStaffAction(actor, "vanish_reconnect_flow", "silentJoin=true,silentDisconnect=true");
        actor.networkHandler.disconnect(Text.literal("Reconnect now. Silent join/disconnect were enabled temporarily and will be restored on login."));
    }

    public synchronized List<com.b1progame.adminmod.state.CommandHistoryEntryData> listCommandHistory(UUID targetUuid, int limit) {
        List<com.b1progame.adminmod.state.CommandHistoryEntryData> out = new ArrayList<>();
        for (com.b1progame.adminmod.state.CommandHistoryEntryData entry : this.stateManager.state().commandHistory) {
            if (entry.playerUuid.equals(targetUuid.toString())) {
                out.add(entry);
            }
        }
        out.sort(Comparator.comparingLong((com.b1progame.adminmod.state.CommandHistoryEntryData e) -> e.createdAtEpochMillis).reversed());
        return out.subList(0, Math.min(limit, out.size()));
    }

    public synchronized WatchlistEntryData getWatchlistEntry(UUID targetUuid) {
        return this.stateManager.state().watchlistEntries.get(targetUuid.toString());
    }

    public synchronized WatchlistEntryData addWatchlist(ServerPlayerEntity actor, UUID targetUuid, String targetName, String reason) {
        WatchlistEntryData entry = new WatchlistEntryData();
        entry.targetUuid = targetUuid.toString();
        entry.lastKnownName = targetName;
        entry.reason = reason == null ? "" : reason;
        entry.actorUuid = actor == null ? "" : actor.getUuidAsString();
        entry.actorName = actor == null ? "console" : actor.getGameProfile().name();
        entry.addedAtEpochMillis = System.currentTimeMillis();
        int score = calculateXraySuspicionScore(targetUuid);
        entry.suspicionSummary = "score=" + score + " level=" + suspicionLabel(score);
        this.stateManager.state().watchlistEntries.put(entry.targetUuid, entry);
        if (actor != null) {
            this.stateManager.markDirty(ServerAccess.server(actor));
        }
        AuditLogger.sensitive(this.configManager, actorLabel(actor) + " added watchlist " + targetName + " (" + entry.targetUuid + ") reason=" + entry.reason);
        recordStaffAction(actor, "watchlist_add", targetName + " reason=" + entry.reason);
        return entry;
    }

    public synchronized boolean removeWatchlist(ServerPlayerEntity actor, UUID targetUuid) {
        WatchlistEntryData removed = this.stateManager.state().watchlistEntries.remove(targetUuid.toString());
        if (removed == null) {
            return false;
        }
        if (actor != null) {
            this.stateManager.markDirty(ServerAccess.server(actor));
        }
        AuditLogger.sensitive(this.configManager, actorLabel(actor) + " removed watchlist " + removed.lastKnownName + " (" + removed.targetUuid + ")");
        recordStaffAction(actor, "watchlist_remove", removed.lastKnownName);
        return true;
    }

    public synchronized List<WatchlistEntryData> listWatchlist() {
        List<WatchlistEntryData> out = new ArrayList<>(this.stateManager.state().watchlistEntries.values());
        out.sort(Comparator.comparingLong((WatchlistEntryData e) -> e.addedAtEpochMillis).reversed());
        return out;
    }

    public synchronized RollbackEntryData recordRollbackEntry(
            ServerPlayerEntity actor,
            UUID targetUuid,
            String targetName,
            String inventoryType,
            String actionType,
            List<SnapshotStackData> removed
    ) {
        if (removed == null || removed.isEmpty()) {
            return null;
        }
        RollbackEntryData entry = new RollbackEntryData();
        entry.id = this.stateManager.state().nextRollbackEntryId++;
        entry.targetUuid = targetUuid.toString();
        entry.targetName = targetName;
        entry.actorUuid = actor == null ? "" : actor.getUuidAsString();
        entry.actorName = actor == null ? "console" : actor.getGameProfile().name();
        entry.createdAtEpochMillis = System.currentTimeMillis();
        entry.inventoryType = inventoryType;
        entry.actionType = actionType;
        for (SnapshotStackData stack : removed) {
            SnapshotStackData copy = new SnapshotStackData();
            copy.slot = stack.slot;
            copy.itemId = stack.itemId;
            copy.count = stack.count;
            entry.removedStacks.add(copy);
        }
        this.stateManager.state().rollbackEntries.add(entry);
        trimRollbackEntries(targetUuid);
        if (actor != null) {
            this.stateManager.markDirty(ServerAccess.server(actor));
        }
        AuditLogger.inventory(this.configManager, actorLabel(actor) + " created rollback entry #" + entry.id + " for " + targetName + " type=" + actionType);
        recordStaffAction(actor, "rollback_record", targetName + " #" + entry.id + " " + actionType);
        return entry;
    }

    public synchronized void recordSingleRemoval(
            ServerPlayerEntity actor,
            UUID targetUuid,
            String targetName,
            String inventoryType,
            String actionType,
            int slot,
            ItemStack removed
    ) {
        if (removed == null || removed.isEmpty()) {
            return;
        }
        SnapshotStackData stack = new SnapshotStackData();
        stack.slot = slot;
        stack.itemId = Registries.ITEM.getId(removed.getItem()).toString();
        stack.count = removed.getCount();
        recordRollbackEntry(actor, targetUuid, targetName, inventoryType, actionType, List.of(stack));
    }

    public synchronized void recordClearRollback(
            ServerPlayerEntity actor,
            UUID targetUuid,
            String targetName,
            String inventoryType,
            Inventory inventory,
            int slots
    ) {
        List<SnapshotStackData> removed = snapshotSimpleInventory(inventory, slots);
        recordRollbackEntry(actor, targetUuid, targetName, inventoryType, "clear", removed);
    }

    public synchronized List<RollbackEntryData> listRollbackEntries(UUID targetUuid, int limit) {
        List<RollbackEntryData> out = new ArrayList<>();
        for (RollbackEntryData entry : this.stateManager.state().rollbackEntries) {
            if (entry.targetUuid.equals(targetUuid.toString())) {
                out.add(entry);
            }
        }
        out.sort(Comparator.comparingLong((RollbackEntryData e) -> e.createdAtEpochMillis).reversed());
        if (limit <= 0) {
            return out;
        }
        return out.subList(0, Math.min(limit, out.size()));
    }

    public synchronized RollbackResultData applyRollback(ServerPlayerEntity actor, long entryId) {
        RollbackEntryData entry = null;
        for (RollbackEntryData current : this.stateManager.state().rollbackEntries) {
            if (current.id == entryId) {
                entry = current;
                break;
            }
        }
        if (entry == null) {
            return new RollbackResultData(false, "Rollback entry not found.", 0, 0);
        }
        if (entry.rolledBack) {
            return new RollbackResultData(false, "Rollback already applied.", 0, 0);
        }
        ServerPlayerEntity target = actor == null ? null : ServerAccess.server(actor).getPlayerManager().getPlayer(UUID.fromString(entry.targetUuid));
        if (target == null) {
            return new RollbackResultData(false, "Target must be online for rollback.", 0, 0);
        }

        Inventory inventory = "ender".equals(entry.inventoryType) ? target.getEnderChestInventory() : target.getInventory();
        int restored = 0;
        int dropped = 0;
        for (SnapshotStackData stackData : entry.removedStacks) {
            ItemStack stack = stackFromSnapshot(stackData);
            if (stack.isEmpty()) {
                continue;
            }
            restored += addStackSafely(inventory, stackData.slot, stack);
            if (!stack.isEmpty()) {
                dropped += stack.getCount();
                target.dropItem(stack.copy(), false, false);
                stack.setCount(0);
            }
        }
        target.getInventory().markDirty();
        target.getEnderChestInventory().markDirty();

        entry.rolledBack = true;
        entry.rolledBackByUuid = actor == null ? "" : actor.getUuidAsString();
        entry.rolledBackByName = actor == null ? "console" : actor.getGameProfile().name();
        entry.rolledBackAtEpochMillis = System.currentTimeMillis();
        if (actor != null) {
            this.stateManager.markDirty(ServerAccess.server(actor));
        }
        AuditLogger.inventory(this.configManager, actorLabel(actor) + " applied rollback #" + entry.id + " restored=" + restored + " dropped=" + dropped);
        recordStaffAction(actor, "rollback_apply", "#" + entry.id + " restored=" + restored + " dropped=" + dropped);
        return new RollbackResultData(true, "Rollback applied.", restored, dropped);
    }

    public synchronized void recordStaffAction(ServerPlayerEntity actor, String actionType, String details) {
        if (actor == null || !this.configManager.get().staff_sessions.enabled) {
            return;
        }
        String key = actor.getUuidAsString();
        Long sessionId = this.stateManager.state().activeStaffSessionByUuid.get(key);
        if (sessionId == null) {
            openSessionIfStaff(actor, "implicit");
            sessionId = this.stateManager.state().activeStaffSessionByUuid.get(key);
            if (sessionId == null) {
                return;
            }
        }
        StaffSessionData session = findSessionById(sessionId);
        if (session == null) {
            return;
        }
        StaffSessionActionData action = new StaffSessionActionData();
        action.atEpochMillis = System.currentTimeMillis();
        action.actionType = actionType;
        action.details = details == null ? "" : details;
        session.actions.add(action);
        this.stateManager.markDirty(ServerAccess.server(actor));
        AuditLogger.sensitive(this.configManager, "session#" + session.id + " " + actor.getGameProfile().name() + " action=" + actionType + " details=" + action.details);
    }

    public synchronized List<StaffSessionData> listStaffSessions(int limit, UUID staffUuid) {
        List<StaffSessionData> out = new ArrayList<>();
        for (StaffSessionData session : this.stateManager.state().staffSessions) {
            if (staffUuid != null && !session.staffUuid.equals(staffUuid.toString())) {
                continue;
            }
            out.add(session);
        }
        out.sort(Comparator.comparingLong((StaffSessionData s) -> s.startedAtEpochMillis).reversed());
        if (limit <= 0) {
            return out;
        }
        return out.subList(0, Math.min(limit, out.size()));
    }

    private void cleanupExpiredTempOps(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, TempOpEntryData>> iterator = this.stateManager.state().tempOpEntries.entrySet().iterator();
        boolean changed = false;
        while (iterator.hasNext()) {
            Map.Entry<String, TempOpEntryData> entry = iterator.next();
            String rawUuid = entry.getKey();
            TempOpEntryData data = entry.getValue();
            if (data.expiresAtEpochMillis > now) {
                ensureTempOpStillGranted(server, rawUuid);
                continue;
            }

            try {
                UUID uuid = UUID.fromString(rawUuid);
                if (!data.hadOpBeforeGrant) {
                    server.getPlayerManager().removeFromOperators(new PlayerConfigEntry(uuid, ""));
                }
                logAction(null, uuid, historyNameFor(uuid), "temp_op_expiry", "", 0L);
            } catch (IllegalArgumentException ignored) {
            }
            iterator.remove();
            changed = true;
        }
        if (changed) {
            this.stateManager.markDirty(server);
        }
    }

    private void cleanupExpiredBans(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, BanRecordData>> iterator = this.stateManager.state().activeBans.entrySet().iterator();
        boolean changed = false;
        while (iterator.hasNext()) {
            Map.Entry<String, BanRecordData> entry = iterator.next();
            BanRecordData ban = entry.getValue();
            if (ban.permanent || ban.expiresAtEpochMillis > now) {
                continue;
            }
            iterator.remove();
            changed = true;
            try {
                logAction(null, UUID.fromString(ban.targetUuid), ban.targetName, "temp_ban_expiry", ban.reason, 0L);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (changed) {
            this.stateManager.markDirty(server);
        }
    }

    private void cleanupExpiredXrayRecords(MinecraftServer server) {
        int retentionDays = Math.max(1, this.configManager.get().xray_tracker.retention_days);
        long cutoff = System.currentTimeMillis() - (retentionDays * 86_400_000L);
        boolean changed = this.stateManager.state().xrayRecords.removeIf(record -> record.createdAtEpochMillis < cutoff);
        if (changed) {
            this.stateManager.markDirty(server);
        }
    }

    private boolean checkBanOnJoin(ServerPlayerEntity player) {
        BanRecordData ban = this.stateManager.state().activeBans.get(player.getUuidAsString());
        if (ban == null) {
            return false;
        }
        if (!ban.permanent && ban.expiresAtEpochMillis <= System.currentTimeMillis()) {
            this.stateManager.state().activeBans.remove(player.getUuidAsString());
            this.stateManager.markDirty(ServerAccess.server(player));
            logAction(null, player.getUuid(), player.getGameProfile().name(), "temp_ban_expiry", ban.reason, 0L);
            return false;
        }
        sendBannedJoinAttempt(player.getGameProfile().name());
        StringBuilder message = new StringBuilder();
        message.append("=== AdminMod Ban Notice ===\n");
        message.append("Player: ").append(player.getGameProfile().name()).append("\n");
        message.append("Type: ").append(ban.permanent ? "Permanent Ban" : "Temporary Ban").append("\n");
        if (ban.reason != null && !ban.reason.isBlank()) {
            message.append("Reason: ").append(ban.reason).append("\n");
        }
        if (ban.actorName != null && !ban.actorName.isBlank()) {
            message.append("Issued by: ").append(ban.actorName).append("\n");
        }
        if (!ban.permanent) {
            long remaining = Math.max(0L, ban.expiresAtEpochMillis - System.currentTimeMillis());
            message.append("Remaining: ").append(com.b1progame.adminmod.util.DurationParser.formatMillis(remaining)).append("\n");
        }
        message.append(ban.permanent ? this.configManager.get().bans.ban_join_denied_message : this.configManager.get().bans.temp_ban_join_denied_message);
        player.networkHandler.disconnect(Text.literal(message.toString()));
        return true;
    }

    private void ensureTempOpStillGranted(MinecraftServer server, String rawUuid) {
        try {
            UUID uuid = UUID.fromString(rawUuid);
            PlayerConfigEntry entry = new PlayerConfigEntry(uuid, "");
            if (!server.getPlayerManager().isOperator(entry)) {
                server.getPlayerManager().addToOperators(entry);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void enforceFrozenPlayers(MinecraftServer server, boolean showActionbar) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!isFrozen(player.getUuid())) {
                this.freezeAnchors.remove(player.getUuid());
                continue;
            }
            FrozenState lock = this.freezeAnchors.computeIfAbsent(player.getUuid(), ignored -> FrozenState.fromPlayer(player));
            Vec3d current = new Vec3d(player.getX(), player.getY(), player.getZ());
            boolean moved = current.squaredDistanceTo(lock.anchor) > 0.003D;
            boolean rotated = Math.abs(player.getYaw() - lock.yaw) > 0.05F || Math.abs(player.getPitch() - lock.pitch) > 0.05F;
            if (moved || rotated) {
                player.teleport(player.getEntityWorld(), lock.anchor.x, lock.anchor.y, lock.anchor.z, Set.of(), lock.yaw, lock.pitch, false);
            }
            player.setVelocity(Vec3d.ZERO);
            if (showActionbar && this.configManager.get().freeze.freeze_actionbar_enabled) {
                player.sendMessage(Text.literal(this.configManager.get().freeze.freeze_message).formatted(Formatting.RED), true);
            }
        }
    }

    private void registerInteractionGuards() {
        UseItemCallback.EVENT.register((player, world, hand) -> isFrozen(player.getUuid()) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> isFrozen(player.getUuid()) ? ActionResult.FAIL : ActionResult.PASS);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> isFrozen(player.getUuid()) ? ActionResult.FAIL : ActionResult.PASS);
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> isFrozen(player.getUuid()) ? ActionResult.FAIL : ActionResult.PASS);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> isFrozen(player.getUuid()) ? ActionResult.FAIL : ActionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> !isFrozen(player.getUuid()));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> handleWorldChange(player));
    }

    private void registerChatGuards() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((signedMessage, sender, parameters) -> {
            if (isMuted(sender.getUuid())) {
                sender.sendMessage(Text.literal(this.configManager.get().mute.mute_message).formatted(Formatting.RED), false);
                return false;
            }
            if (isConsoleChatEnabled(sender.getUuid())) {
                sendConsoleChat(sender, signedMessage.getSignedContent());
                return false;
            }
            if (isStaffChatEnabled(sender.getUuid())) {
                sendStaffChat(sender, signedMessage.getSignedContent());
                return false;
            }
            return true;
        });
        ServerMessageEvents.ALLOW_GAME_MESSAGE.register((server, message, overlay) -> allowGameMessage(message));
        ServerMessageEvents.COMMAND_MESSAGE.register((signedMessage, source, parameters) -> {
            ServerPlayerEntity player = source.getPlayer();
            if (player == null) {
                return;
            }
            recordCommandHistory(player, signedMessage.getSignedContent());
        });
    }

    private void registerXrayTracker() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            if (AdminMod.get() != null && AdminMod.get().heatmapManager() != null) {
                AdminMod.get().heatmapManager().recordMiningBreak(serverPlayer, pos);
            }
            if (!isXrayTrackerEnabled()) {
                return;
            }
            String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
            if (!isXrayOreEnabled(blockId)) {
                return;
            }
            recordXray(serverPlayer, state.getBlock(), pos);
        });
    }

    private void recordXray(ServerPlayerEntity player, Block block, BlockPos pos) {
        PersistentStateData state = this.stateManager.state();
        XrayRecordData record = new XrayRecordData();
        record.id = state.nextXrayRecordId++;
        record.playerUuid = player.getUuidAsString();
        record.playerName = player.getGameProfile().name();
        record.oreBlockId = Registries.BLOCK.getId(block).toString();
        record.world = player.getEntityWorld().getRegistryKey().getValue().toString();
        record.dimension = player.getEntityWorld().getRegistryKey().getValue().toString();
        record.x = pos.getX();
        record.y = pos.getY();
        record.z = pos.getZ();
        record.createdAtEpochMillis = System.currentTimeMillis();
        state.xrayRecords.add(record);
        this.stateManager.markDirty(ServerAccess.server(player));
        if (AdminMod.get() != null && AdminMod.get().xrayReplayManager() != null) {
            AdminMod.get().xrayReplayManager().onTrackedOreBreak(player, record.oreBlockId, pos);
        }

        int threshold = Math.max(1, this.configManager.get().xray_tracker.suspicious_ore_count);
        int windowMinutes = Math.max(1, this.configManager.get().xray_tracker.suspicious_window_minutes);
        long cutoff = System.currentTimeMillis() - (windowMinutes * 60_000L);
        List<XrayRecordData> recent = new ArrayList<>();
        for (XrayRecordData existing : state.xrayRecords) {
            if (existing.playerUuid.equals(record.playerUuid) && existing.createdAtEpochMillis >= cutoff) {
                recent.add(existing);
            }
        }
        int score = calculateXraySuspicionScore(player.getUuid());
        boolean scoreSuspicious = score >= Math.max(1, this.configManager.get().xray_tracker.suspicion_alert_threshold);
        boolean valuableSuspicious = this.configManager.get().xray_tracker.valuable_ore_immediate_alerts
                && isValuableOre(record.oreBlockId)
                && countValuableFindsWithin(player.getUuid(), this.configManager.get().xray_tracker.valuable_repeat_window_minutes) >= Math.max(1, this.configManager.get().xray_tracker.valuable_repeat_count);
        if (AdminMod.get() != null && AdminMod.get().heatmapManager() != null) {
            AdminMod.get().heatmapManager().recordTrackedOre(player, pos, scoreSuspicious || valuableSuspicious);
        }
        alertStaffForXray(player, record, recent, windowMinutes, recent.size() >= threshold || scoreSuspicious || valuableSuspicious);
    }

    private void alertStaffForXray(ServerPlayerEntity player, XrayRecordData record, List<XrayRecordData> recent, int windowMinutes, boolean suspicious) {
        if (!this.configManager.get().xray_tracker.staff_alerts_enabled) {
            return;
        }
        VeinTimeline timeline = computeVeinTimeline(recent, 12.0D, 120_000L);
        int veinCount = timeline.veinCount;
        String oreName = record.oreBlockId.substring(record.oreBlockId.indexOf(':') + 1).toUpperCase(Locale.ROOT);
        Text base = Text.literal("Notice: ").formatted(Formatting.GOLD)
                .append(Text.literal(player.getGameProfile().name()).formatted(Formatting.AQUA))
                .append(Text.literal(" has mined a ").formatted(Formatting.YELLOW))
                .append(Text.literal(oreName).formatted(Formatting.AQUA))
                .append(Text.literal(" block.").formatted(Formatting.YELLOW));
        Text clickToTeleport = this.configManager.get().xray_tracker.click_to_teleport_enabled
                ? Text.literal(" Click to teleport").setStyle(
                Style.EMPTY.withColor(Formatting.GREEN).withClickEvent(new ClickEvent.RunCommand("/xraytracker tp " + record.id)))
                : Text.empty();
        Text summary = Text.literal("Notice: ").formatted(Formatting.GOLD)
                .append(Text.literal(player.getGameProfile().name()).formatted(Formatting.AQUA))
                .append(Text.literal(" has mined ").formatted(Formatting.YELLOW))
                .append(Text.literal(Integer.toString(veinCount)).formatted(Formatting.AQUA))
                .append(Text.literal(" veins over ").formatted(Formatting.YELLOW))
                .append(Text.literal(com.b1progame.adminmod.util.DurationParser.formatMillis(Math.max(0L, timeline.durationMillis))).formatted(Formatting.AQUA));
        if (timeline.lastGapMillis > 0L) {
            summary = summary.copy()
                    .append(Text.literal(" | last gap ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(com.b1progame.adminmod.util.DurationParser.formatMillis(timeline.lastGapMillis)).formatted(Formatting.GRAY));
        }
        if (timeline.averageGapMillis > 0L) {
            summary = summary.copy()
                    .append(Text.literal(" | avg gap ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(com.b1progame.adminmod.util.DurationParser.formatMillis(timeline.averageGapMillis)).formatted(Formatting.GRAY));
        }
        for (ServerPlayerEntity online : ServerAccess.server(player).getPlayerManager().getPlayerList()) {
            if (!PermissionUtil.canUseAdminGui(online, this.configManager)) {
                continue;
            }
            online.sendMessage(base.copy().append(clickToTeleport), false);
            if (suspicious) {
                online.sendMessage(summary, false);
            }
            if (this.configManager.get().xray_tracker.alert_sounds) {
                online.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.7F, 1.0F);
            }
        }
        AuditLogger.sensitive(this.configManager,
                "Xray alert player=" + player.getGameProfile().name() + " ore=" + oreName
                        + " recent=" + recent.size() + " veins=" + veinCount
                        + " duration=" + timeline.durationMillis + "ms"
                        + " lastGap=" + timeline.lastGapMillis + "ms");
    }

    private VeinTimeline computeVeinTimeline(List<XrayRecordData> records, double range, long gapMillis) {
        if (records.isEmpty()) {
            return new VeinTimeline(0, 0L, 0L, 0L);
        }
        long now = System.currentTimeMillis();
        List<XrayRecordData> sorted = new ArrayList<>(records);
        sorted.sort(Comparator.comparingLong(r -> r.createdAtEpochMillis));
        List<Long> veinTimes = new ArrayList<>();
        XrayRecordData last = null;
        for (XrayRecordData current : sorted) {
            if (last == null) {
                veinTimes.add(current.createdAtEpochMillis);
                last = current;
                continue;
            }
            double dx = current.x - last.x;
            double dy = current.y - last.y;
            double dz = current.z - last.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            long dt = current.createdAtEpochMillis - last.createdAtEpochMillis;
            if (distSq > (range * range) || dt > gapMillis) {
                veinTimes.add(current.createdAtEpochMillis);
            }
            last = current;
        }
        if (veinTimes.size() <= 1) {
            long first = veinTimes.get(0);
            long elapsed = Math.max(0L, now - first);
            return new VeinTimeline(veinTimes.size(), elapsed, elapsed, 0L);
        }
        long first = veinTimes.get(0);
        long lastTime = veinTimes.get(veinTimes.size() - 1);
        long duration = Math.max(0L, now - first);
        long lastGap = Math.max(0L, now - lastTime);
        long avgGap = duration / (veinTimes.size() - 1L);
        return new VeinTimeline(veinTimes.size(), duration, lastGap, avgGap);
    }

    private record VeinTimeline(int veinCount, long durationMillis, long lastGapMillis, long averageGapMillis) {
    }

    private void updateHistory(ServerPlayerEntity player, boolean online, boolean quitting) {
        PersistentStateData state = this.stateManager.state();
        String key = player.getUuidAsString();
        PlayerHistoryData history = state.playerHistory.computeIfAbsent(key, ignored -> new PlayerHistoryData());
        long now = System.currentTimeMillis();
        history.lastKnownName = player.getGameProfile().name();
        history.lastWorld = player.getEntityWorld().getRegistryKey().getValue().toString();
        history.lastDimension = player.getEntityWorld().getRegistryKey().getValue().toString();
        history.lastX = player.getX();
        history.lastY = player.getY();
        history.lastZ = player.getZ();
        history.lastPing = player.networkHandler == null ? history.lastPing : player.networkHandler.getLatency();
        history.lastHealth = player.getHealth();
        history.lastHunger = player.getHungerManager().getFoodLevel();
        history.lastXpLevel = player.experienceLevel;
        history.lastKnownIp = player.getIp();
        history.online = online;
        history.lastSeenEpochMillis = now;
        if (online && !quitting) {
            history.lastJoinEpochMillis = now;
        }
        if (quitting) {
            history.lastQuitEpochMillis = now;
        }
    }

    private ModerationNoteData addModerationEntry(
            ServerPlayerEntity actor,
            UUID targetUuid,
            String text,
            String type,
            String category
    ) {
        PersistentStateData state = this.stateManager.state();
        String key = targetUuid.toString();
        int nextId = state.moderationNoteCounters.getOrDefault(key, 0) + 1;
        state.moderationNoteCounters.put(key, nextId);

        ModerationNoteData note = new ModerationNoteData();
        note.id = nextId;
        note.text = text;
        note.createdAtIso = Instant.now().toString();
        note.authorName = actor == null ? "console" : actor.getGameProfile().name();
        note.authorUuid = actor == null ? "" : actor.getUuidAsString();
        note.type = type;
        note.category = normalizeCategory(category);

        state.moderationNotes.computeIfAbsent(key, ignored -> new ArrayList<>()).add(note);
        if (actor != null) {
            this.stateManager.markDirty(ServerAccess.server(actor));
        }
        AuditLogger.sensitive(this.configManager, actorLabel(actor) + " added " + type + " #" + note.id + " for " + key + ": " + text);
        return note;
    }

    private List<SnapshotStackData> snapshotPlayerInventory(PlayerInventory inventory) {
        List<SnapshotStackData> out = new ArrayList<>();
        for (int i = 0; i <= 40; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            SnapshotStackData line = new SnapshotStackData();
            line.slot = i;
            line.itemId = Registries.ITEM.getId(stack.getItem()).toString();
            line.count = stack.getCount();
            out.add(line);
        }
        return out;
    }

    private List<SnapshotStackData> snapshotSimpleInventory(Inventory inventory, int slots) {
        List<SnapshotStackData> out = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            SnapshotStackData line = new SnapshotStackData();
            line.slot = i;
            line.itemId = Registries.ITEM.getId(stack.getItem()).toString();
            line.count = stack.getCount();
            out.add(line);
        }
        return out;
    }

    private void logAction(ServerPlayerEntity actor, ServerPlayerEntity target, String action, String details) {
        logAction(actor, target, action, details, 0L);
    }

    private void logAction(ServerPlayerEntity actor, ServerPlayerEntity target, String action, String details, long expiresAt) {
        if (target == null) {
            return;
        }
        logAction(actor, target.getUuid(), target.getGameProfile().name(), action, details, expiresAt);
    }

    private void logAction(ServerPlayerEntity actor, UUID targetUuid, String targetName, String action, String details, long expiresAt) {
        PersistentStateData state = this.stateManager.state();
        ModerationActionData entry = new ModerationActionData();
        entry.id = state.nextActionId++;
        entry.targetUuid = targetUuid.toString();
        entry.targetName = targetName == null ? "" : targetName;
        entry.actionType = action;
        entry.actorUuid = actor == null ? "" : actor.getUuidAsString();
        entry.actorName = actor == null ? "console" : actor.getGameProfile().name();
        entry.createdAtEpochMillis = System.currentTimeMillis();
        entry.details = details == null ? "" : details;
        entry.expiresAtEpochMillis = expiresAt;
        state.moderationActions.add(entry);

        AuditLogger.sensitive(this.configManager,
                "[" + action.toUpperCase(Locale.ROOT) + "] actor=" + entry.actorName
                        + " target=" + entry.targetName + " (" + entry.targetUuid + ")"
                        + (entry.details.isBlank() ? "" : " details=" + entry.details)
                        + (expiresAt > 0L ? " expires=" + expiresAt : ""));
    }

    private String historyNameFor(UUID uuid) {
        PlayerHistoryData history = this.stateManager.state().playerHistory.get(uuid.toString());
        if (history == null || history.lastKnownName == null || history.lastKnownName.isBlank()) {
            return uuid.toString();
        }
        return history.lastKnownName;
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "general";
        }
        String normalized = category.toLowerCase(Locale.ROOT).trim();
        return switch (normalized) {
            case "general", "cheating", "xray", "chat", "griefing", "behavior" -> normalized;
            default -> "general";
        };
    }

    private String actorLabel(ServerPlayerEntity actor) {
        return actor == null ? "console" : AuditLogger.actor(actor);
    }

    private boolean allowGameMessage(Text message) {
        if (!(message.getContent() instanceof TranslatableTextContent content)) {
            return true;
        }
        String key = content.getKey();
        boolean isJoin = "multiplayer.player.joined".equals(key) || "multiplayer.player.joined.renamed".equals(key);
        boolean isLeave = "multiplayer.player.left".equals(key);
        boolean isDeath = key.startsWith("death.");
        boolean isAdvancement = key.startsWith("chat.type.advancement.");
        if (!isJoin && !isLeave && !isDeath && !isAdvancement) {
            return true;
        }
        Object[] args = content.getArgs();
        if (args.length == 0) {
            return true;
        }
        String name = extractGameMessagePlayerName(args[0]);
        if (name.isBlank()) {
            return true;
        }
        UUID uuid = resolveGameMessagePlayerUuid(name);
        if (uuid == null) {
            return true;
        }
        if (isVanished(uuid)) {
            return false;
        }
        if (isJoin && isSilentJoinEnabled(uuid)) {
            AdminMod.LOGGER.info("[AdminMod] silent join: {}", name);
            return false;
        }
        if (isLeave && isSilentDisconnectEnabled(uuid)) {
            AdminMod.LOGGER.info("[AdminMod] silent disconnect: {}", name);
            return false;
        }
        return true;
    }

    private void sendConsoleChat(ServerPlayerEntity sender, String message) {
        if (sender == null) {
            return;
        }
        String resolved = message == null ? "" : message.trim();
        if (resolved.isBlank()) {
            return;
        }
        MinecraftServer server = ServerAccess.server(sender);
        Text line = Text.literal(resolved);
        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            online.sendMessage(line, false);
        }
        AuditLogger.sensitive(this.configManager, "[CONSOLECHAT] " + sender.getGameProfile().name() + ": " + resolved);
    }

    private void restoreSilentSettingsAfterVanishRejoin(ServerPlayerEntity player) {
        String key = player.getUuidAsString();
        Boolean restoreJoin = this.stateManager.state().pendingSilentJoinRestore.remove(key);
        Boolean restoreDisconnect = this.stateManager.state().pendingSilentDisconnectRestore.remove(key);
        if (restoreJoin == null && restoreDisconnect == null) {
            return;
        }
        if (restoreJoin != null) {
            this.stateManager.state().silentJoinToggles.put(key, restoreJoin);
        }
        if (restoreDisconnect != null) {
            this.stateManager.state().silentDisconnectToggles.put(key, restoreDisconnect);
        }
        this.stateManager.markDirty(ServerAccess.server(player));
        player.sendMessage(Text.literal("vanish mode applied completly").formatted(Formatting.GRAY), false);
        AuditLogger.sensitive(this.configManager, AuditLogger.actor(player) + " had silent settings restored after vanish reconnect");
    }

    private UUID resolveGameMessagePlayerUuid(String name) {
        UUID uuid = findPlayerUuidByName(name);
        if (uuid != null) {
            return uuid;
        }
        // Fallback for messages where name formatting differs from stored plain name.
        for (Map.Entry<String, PlayerHistoryData> entry : this.stateManager.state().playerHistory.entrySet()) {
            String known = entry.getValue().lastKnownName;
            if (known == null || known.isBlank()) {
                continue;
            }
            if (!name.toLowerCase(Locale.ROOT).contains(known.toLowerCase(Locale.ROOT))) {
                continue;
            }
            try {
                return UUID.fromString(entry.getKey());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean isVanished(UUID uuid) {
        return AdminMod.get() != null
                && AdminMod.get().vanishManager() != null
                && AdminMod.get().vanishManager().isVanished(uuid);
    }

    private String extractGameMessagePlayerName(Object rawArg) {
        if (rawArg == null) {
            return "";
        }
        if (rawArg instanceof Text text) {
            return text.getString().trim();
        }
        return String.valueOf(rawArg).trim();
    }

    private void recordCommandHistory(ServerPlayerEntity player, String rawCommand) {
        if (!this.configManager.get().command_history.enabled) {
            return;
        }
        String command = rawCommand == null ? "" : rawCommand.trim();
        if (command.isBlank()) {
            return;
        }
        if (!command.startsWith("/")) {
            command = "/" + command;
        }
        com.b1progame.adminmod.state.CommandHistoryEntryData entry = new com.b1progame.adminmod.state.CommandHistoryEntryData();
        entry.id = this.stateManager.state().nextCommandHistoryId++;
        entry.playerUuid = player.getUuidAsString();
        entry.playerName = player.getGameProfile().name();
        entry.command = redactCommand(command);
        entry.createdAtEpochMillis = System.currentTimeMillis();
        this.stateManager.state().commandHistory.add(entry);
        trimCommandHistory();
        this.stateManager.markDirty(ServerAccess.server(player));
    }

    private String redactCommand(String command) {
        for (String prefix : this.configManager.get().command_history.redaction_rules) {
            if (command.equalsIgnoreCase(prefix) || command.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT) + " ")) {
                return prefix + " <redacted>";
            }
        }
        return command;
    }

    private void trimCommandHistory() {
        int max = Math.max(100, this.configManager.get().command_history.max_entries);
        List<com.b1progame.adminmod.state.CommandHistoryEntryData> entries = this.stateManager.state().commandHistory;
        if (entries.size() <= max) {
            return;
        }
        entries.sort(Comparator.comparingLong(e -> e.createdAtEpochMillis));
        while (entries.size() > max) {
            entries.remove(0);
        }
    }

    private void emitJoinNotifications(ServerPlayerEntity joined) {
        var settings = this.configManager.get().join_notifications;
        if (!settings.enabled) {
            return;
        }
        WatchlistEntryData watch = this.stateManager.state().watchlistEntries.get(joined.getUuidAsString());
        if (settings.watchlist_join_notifications && watch != null) {
            String message = settings.watchlist_join_template
                    .replace("%player%", joined.getGameProfile().name())
                    .replace("%reason%", watch.reason == null || watch.reason.isBlank() ? "-" : watch.reason);
            sendStaffNotice(Text.literal(message).formatted(Formatting.GOLD), joined.getUuid());
        }
        if (settings.suspicious_player_join_notifications) {
            int score = calculateXraySuspicionScore(joined.getUuid());
            if (score >= settings.suspicious_join_score_threshold) {
                String message = settings.suspicious_join_template
                        .replace("%player%", joined.getGameProfile().name())
                        .replace("%score%", Integer.toString(score));
                sendStaffNotice(Text.literal(message).formatted(Formatting.RED), joined.getUuid());
            }
        }
    }

    private void sendBannedJoinAttempt(String playerName) {
        var settings = this.configManager.get().join_notifications;
        if (!settings.enabled) {
            return;
        }
        String message = settings.banned_attempt_template.replace("%player%", playerName);
        sendStaffNotice(Text.literal(message).formatted(Formatting.DARK_RED), null);
    }

    public synchronized void notifyWhitelistDeniedAttempt(MinecraftServer server, String playerName, String playerUuid) {
        var settings = this.configManager.get().join_notifications;
        if (!settings.enabled || !settings.whitelist_denied_notifications) {
            return;
        }
        String resolvedName = playerName == null || playerName.isBlank() ? "unknown" : playerName;
        String resolvedUuid = playerUuid == null || playerUuid.isBlank() ? "-" : playerUuid;
        String message = settings.whitelist_denied_template
                .replace("%player%", resolvedName)
                .replace("%uuid%", resolvedUuid);
        Text line = Text.literal(message).formatted(Formatting.GOLD);
        try {
            UUID uuid = UUID.fromString(resolvedUuid);
            Text whitelistButton = Text.literal(" [Put on whitelist]")
                    .setStyle(Style.EMPTY
                            .withColor(Formatting.GREEN)
                            .withBold(true)
                            .withClickEvent(new ClickEvent.RunCommand("/admin whitelist confirm " + uuid + " " + resolvedName))
                            .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to confirm whitelist for " + resolvedName))));
            line = line.copy().append(whitelistButton);
        } catch (IllegalArgumentException ignored) {
            // Keep plain notice if UUID is missing/invalid.
        }
        int delivered = sendStaffNotice(line, null);
        if (delivered == 0 && settings.queue_offline_staff_mail) {
            StaffMailEntryData mail = new StaffMailEntryData();
            mail.id = this.stateManager.state().nextStaffMailId++;
            mail.createdAtEpochMillis = System.currentTimeMillis();
            mail.category = "whitelist_denied";
            mail.message = message;
            this.stateManager.state().pendingStaffMail.add(mail);
            this.stateManager.markDirty(server);
            AuditLogger.sensitive(this.configManager, "Queued staff mail for whitelist denied login: " + resolvedName + " (" + resolvedUuid + ")");
        } else {
            AuditLogger.sensitive(this.configManager, "Sent whitelist denied notice: " + resolvedName + " (" + resolvedUuid + "), recipients=" + delivered);
        }
    }

    private void deliverPendingStaffMail(ServerPlayerEntity player) {
        if (!PermissionUtil.canUseAdminGui(player, this.configManager)) {
            return;
        }
        if (this.stateManager.state().pendingStaffMail.isEmpty()) {
            return;
        }
        int count = this.stateManager.state().pendingStaffMail.size();
        player.sendMessage(Text.literal("[AdminMail] You have " + count + " pending notification(s). Use /admin mail or open it from Player tab.").formatted(Formatting.GOLD), false);
        AuditLogger.sensitive(this.configManager, AuditLogger.actor(player) + " was notified about " + count + " pending staff mail notification(s)");
    }

    public synchronized List<StaffMailEntryData> listPendingStaffMail(int limit) {
        int safeLimit = Math.max(1, limit);
        List<StaffMailEntryData> out = new ArrayList<>(this.stateManager.state().pendingStaffMail);
        out.sort(Comparator.comparingLong((StaffMailEntryData entry) -> entry.createdAtEpochMillis).reversed());
        if (out.size() > safeLimit) {
            return new ArrayList<>(out.subList(0, safeLimit));
        }
        return out;
    }

    public synchronized int pendingStaffMailCount() {
        return this.stateManager.state().pendingStaffMail.size();
    }

    public synchronized int clearPendingStaffMail(ServerPlayerEntity actor) {
        int count = this.stateManager.state().pendingStaffMail.size();
        if (count == 0) {
            return 0;
        }
        this.stateManager.state().pendingStaffMail.clear();
        if (actor != null) {
            this.stateManager.markDirty(ServerAccess.server(actor));
            recordStaffAction(actor, "staff_mail_clear", Integer.toString(count));
            AuditLogger.sensitive(this.configManager, AuditLogger.actor(actor) + " cleared " + count + " staff mail notification(s)");
        } else if (AdminMod.get() != null && AdminMod.get().server() != null) {
            this.stateManager.markDirty(AdminMod.get().server());
        }
        return count;
    }

    private int sendStaffNotice(Text message, UUID exclude) {
        MinecraftServer server = AdminMod.get() == null ? null : AdminMod.get().server();
        if (server == null) {
            return 0;
        }
        int recipients = 0;
        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            if (exclude != null && online.getUuid().equals(exclude)) {
                continue;
            }
            if (PermissionUtil.canUseAdminGui(online, this.configManager)) {
                online.sendMessage(message, false);
                recipients++;
            }
        }
        return recipients;
    }

    private List<String> buildStackDiffLines(List<SnapshotStackData> a, List<SnapshotStackData> b) {
        Map<String, Integer> countsA = new LinkedHashMap<>();
        Map<String, Integer> countsB = new LinkedHashMap<>();
        Map<String, Set<Integer>> slotsA = new LinkedHashMap<>();
        Map<String, Set<Integer>> slotsB = new LinkedHashMap<>();
        collectStackStats(a, countsA, slotsA);
        collectStackStats(b, countsB, slotsB);

        Set<String> keys = new HashSet<>();
        keys.addAll(countsA.keySet());
        keys.addAll(countsB.keySet());
        List<String> sortedKeys = new ArrayList<>(keys);
        sortedKeys.sort(String::compareTo);

        List<String> lines = new ArrayList<>();
        lines.add("Diff Summary:");
        for (String itemId : sortedKeys) {
            int before = countsA.getOrDefault(itemId, 0);
            int after = countsB.getOrDefault(itemId, 0);
            if (before == after) {
                continue;
            }
            int delta = after - before;
            String deltaLabel = delta > 0 ? "+" + delta : Integer.toString(delta);
            String slotHint = "";
            if (!slotsA.getOrDefault(itemId, Set.of()).equals(slotsB.getOrDefault(itemId, Set.of()))) {
                slotHint = " slots " + slotsA.getOrDefault(itemId, Set.of()) + " -> " + slotsB.getOrDefault(itemId, Set.of());
            }
            lines.add(itemId + " " + before + " -> " + after + " (" + deltaLabel + ")" + slotHint);
        }
        if (lines.size() == 1) {
            lines.add("No item/count differences found.");
        }
        return lines;
    }

    private void collectStackStats(List<SnapshotStackData> source, Map<String, Integer> counts, Map<String, Set<Integer>> slots) {
        for (SnapshotStackData stack : source) {
            counts.put(stack.itemId, counts.getOrDefault(stack.itemId, 0) + stack.count);
            slots.computeIfAbsent(stack.itemId, ignored -> new HashSet<>()).add(stack.slot);
        }
    }

    private List<SnapshotStackData> currentStacksFromOnline(ServerPlayerEntity actor, UUID targetUuid, boolean enderChest) {
        if (actor == null) {
            return null;
        }
        ServerPlayerEntity target = ServerAccess.server(actor).getPlayerManager().getPlayer(targetUuid);
        if (target == null) {
            return null;
        }
        return enderChest ? snapshotSimpleInventory(target.getEnderChestInventory(), 27) : snapshotPlayerInventory(target.getInventory());
    }

    private int estimateVeinCount(List<XrayRecordData> records, double range, long gapMillis) {
        if (records.isEmpty()) {
            return 0;
        }
        List<XrayRecordData> sorted = new ArrayList<>(records);
        sorted.sort(Comparator.comparingLong(r -> r.createdAtEpochMillis));
        int veins = 0;
        XrayRecordData last = null;
        for (XrayRecordData current : sorted) {
            if (last == null) {
                veins++;
                last = current;
                continue;
            }
            double dx = current.x - last.x;
            double dy = current.y - last.y;
            double dz = current.z - last.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            long dt = current.createdAtEpochMillis - last.createdAtEpochMillis;
            if (distSq > (range * range) || dt > gapMillis) {
                veins++;
            }
            last = current;
        }
        return veins;
    }

    private int oreWeight(String oreId) {
        if (oreId == null) {
            return 1;
        }
        if (oreId.endsWith("ancient_debris")) return 9;
        if (oreId.endsWith("diamond_ore") || oreId.endsWith("deepslate_diamond_ore")) return 8;
        if (oreId.endsWith("emerald_ore") || oreId.endsWith("deepslate_emerald_ore")) return 7;
        if (oreId.endsWith("gold_ore") || oreId.endsWith("deepslate_gold_ore")) return 4;
        if (oreId.endsWith("lapis_ore") || oreId.endsWith("deepslate_lapis_ore")) return 3;
        if (oreId.endsWith("redstone_ore") || oreId.endsWith("deepslate_redstone_ore")) return 3;
        if (oreId.endsWith("iron_ore") || oreId.endsWith("deepslate_iron_ore")) return 2;
        if (oreId.endsWith("coal_ore") || oreId.endsWith("deepslate_coal_ore")) return 1;
        return 1;
    }

    private boolean isValuableOre(String oreId) {
        if (oreId == null) {
            return false;
        }
        return oreId.endsWith("diamond_ore")
                || oreId.endsWith("deepslate_diamond_ore")
                || oreId.endsWith("emerald_ore")
                || oreId.endsWith("deepslate_emerald_ore")
                || oreId.endsWith("ancient_debris");
    }

    private int countValuableFindsWithin(UUID playerUuid, int minutes) {
        long cutoff = System.currentTimeMillis() - (Math.max(1, minutes) * 60_000L);
        int count = 0;
        for (XrayRecordData record : this.stateManager.state().xrayRecords) {
            if (!record.playerUuid.equals(playerUuid.toString())) {
                continue;
            }
            if (record.createdAtEpochMillis < cutoff) {
                continue;
            }
            if (isValuableOre(record.oreBlockId)) {
                count++;
            }
        }
        return count;
    }

    private int addStackSafely(Inventory inventory, int preferredSlot, ItemStack stack) {
        int before = stack.getCount();
        if (preferredSlot >= 0 && preferredSlot < inventory.size()) {
            ItemStack existing = inventory.getStack(preferredSlot);
            if (existing.isEmpty()) {
                inventory.setStack(preferredSlot, stack.copy());
                stack.setCount(0);
                return before;
            }
            if (ItemStack.areItemsAndComponentsEqual(existing, stack) && existing.getCount() < existing.getMaxCount()) {
                int moved = Math.min(stack.getCount(), existing.getMaxCount() - existing.getCount());
                existing.increment(moved);
                stack.decrement(moved);
            }
        }
        for (int i = 0; i < inventory.size() && !stack.isEmpty(); i++) {
            ItemStack existing = inventory.getStack(i);
            if (!existing.isEmpty() && ItemStack.areItemsAndComponentsEqual(existing, stack) && existing.getCount() < existing.getMaxCount()) {
                int moved = Math.min(stack.getCount(), existing.getMaxCount() - existing.getCount());
                existing.increment(moved);
                stack.decrement(moved);
            }
        }
        for (int i = 0; i < inventory.size() && !stack.isEmpty(); i++) {
            ItemStack existing = inventory.getStack(i);
            if (existing.isEmpty()) {
                inventory.setStack(i, stack.copy());
                stack.setCount(0);
            }
        }
        return before - stack.getCount();
    }

    private ItemStack stackFromSnapshot(SnapshotStackData stackData) {
        Identifier id = Identifier.tryParse(stackData.itemId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = Registries.ITEM.get(id);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, Math.max(1, stackData.count));
    }

    private void trimRollbackEntries(UUID targetUuid) {
        int max = Math.max(1, this.configManager.get().rollback_lite.max_entries_per_player);
        List<RollbackEntryData> targetEntries = listRollbackEntries(targetUuid, 0);
        if (targetEntries.size() <= max) {
            return;
        }
        int removeCount = targetEntries.size() - max;
        targetEntries.sort(Comparator.comparingLong(e -> e.createdAtEpochMillis));
        Set<Long> removeIds = new HashSet<>();
        for (int i = 0; i < removeCount; i++) {
            removeIds.add(targetEntries.get(i).id);
        }
        this.stateManager.state().rollbackEntries.removeIf(e -> removeIds.contains(e.id));
    }

    private void openSessionIfStaff(ServerPlayerEntity player, String reason) {
        if (!this.configManager.get().staff_sessions.enabled) {
            return;
        }
        if (!PermissionUtil.canUseAdminGui(player, this.configManager)) {
            return;
        }
        String key = player.getUuidAsString();
        if (this.stateManager.state().activeStaffSessionByUuid.containsKey(key)) {
            return;
        }
        StaffSessionData session = new StaffSessionData();
        session.id = this.stateManager.state().nextStaffSessionId++;
        session.staffUuid = key;
        session.staffName = player.getGameProfile().name();
        session.startedAtEpochMillis = System.currentTimeMillis();
        session.startReason = reason;
        this.stateManager.state().staffSessions.add(session);
        this.stateManager.state().activeStaffSessionByUuid.put(key, session.id);
        trimSessions();
        this.stateManager.markDirty(ServerAccess.server(player));
        AuditLogger.sensitive(this.configManager, "staff session opened #" + session.id + " staff=" + session.staffName + " reason=" + reason);
    }

    private void closeSessionIfOpen(ServerPlayerEntity player, String reason) {
        if (!this.configManager.get().staff_sessions.enabled) {
            return;
        }
        String key = player.getUuidAsString();
        Long id = this.stateManager.state().activeStaffSessionByUuid.remove(key);
        if (id == null) {
            return;
        }
        StaffSessionData session = findSessionById(id);
        if (session != null) {
            session.endedAtEpochMillis = System.currentTimeMillis();
            session.endReason = reason;
        }
        this.stateManager.markDirty(ServerAccess.server(player));
        AuditLogger.sensitive(this.configManager, "staff session closed #" + id + " staff=" + player.getGameProfile().name() + " reason=" + reason);
    }

    private StaffSessionData findSessionById(long id) {
        for (StaffSessionData session : this.stateManager.state().staffSessions) {
            if (session.id == id) {
                return session;
            }
        }
        return null;
    }

    private void trimSessions() {
        int max = Math.max(10, this.configManager.get().staff_sessions.max_saved_sessions);
        List<StaffSessionData> sessions = this.stateManager.state().staffSessions;
        if (sessions.size() <= max) {
            return;
        }
        sessions.sort(Comparator.comparingLong(s -> s.startedAtEpochMillis));
        while (sessions.size() > max) {
            sessions.remove(0);
        }
    }

    private static final class FrozenState {
        private final Vec3d anchor;
        private final float yaw;
        private final float pitch;

        private FrozenState(Vec3d anchor, float yaw, float pitch) {
            this.anchor = anchor;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        private static FrozenState fromPlayer(ServerPlayerEntity player) {
            return new FrozenState(new Vec3d(player.getX(), player.getY(), player.getZ()), player.getYaw(), player.getPitch());
        }
    }

    public record RollbackResultData(boolean success, String message, int restoredCount, int droppedCount) {
    }
}
