package com.b1progame.adminmod.xrayreplay;

import com.b1progame.adminmod.AdminMod;
import com.b1progame.adminmod.config.ConfigManager;
import com.b1progame.adminmod.state.PersistentStateData;
import com.b1progame.adminmod.state.StateManager;
import com.b1progame.adminmod.state.XrayReplayWatchEntryData;
import com.b1progame.adminmod.util.AuditLogger;
import com.b1progame.adminmod.util.PermissionUtil;
import com.b1progame.adminmod.util.ServerAccess;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class XrayReplayManager {
    private static final boolean FEATURE_ENABLED = false;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> CONTROL_NAMES = List.of(
            "Replay: Play/Resume", "Replay: Pause", "Replay: Restart", "Replay: Rewind 5s", "Replay: Rewind 30s",
            "Replay: Forward 5s", "Replay: Forward 30s", "Replay: Speed -", "Replay: Speed +", "Replay: Follow View",
            "Replay: Spectator View", "Replay: Exit"
    );

    private final ConfigManager configManager;
    private final StateManager stateManager;
    private final Map<UUID, ActiveRecording> activeRecordings = new HashMap<>();
    private final Map<UUID, ReplaySession> replaySessions = new HashMap<>();
    private final Map<UUID, Integer> replaySlots = new HashMap<>();
    private long cleanupTicker = 0L;

    public XrayReplayManager(ConfigManager configManager, StateManager stateManager) {
        this.configManager = configManager;
        this.stateManager = stateManager;
        if (FEATURE_ENABLED) {
            registerCallbacks();
        }
    }

    public boolean isFeatureEnabled() {
        return FEATURE_ENABLED;
    }

    public synchronized void onServerStarted(MinecraftServer server) {
        if (!FEATURE_ENABLED) {
            return;
        }
        cleanupExpired(server);
    }

    public synchronized void onServerStopping(MinecraftServer server) {
        if (!FEATURE_ENABLED) {
            return;
        }
        for (ActiveRecording recording : new ArrayList<>(this.activeRecordings.values())) {
            finalizeRecording(server, recording, "server_stop");
        }
        for (UUID admin : new ArrayList<>(this.replaySessions.keySet())) {
            stop(admin);
        }
    }

    public synchronized void onPlayerDisconnect(ServerPlayerEntity player) {
        if (!FEATURE_ENABLED) {
            return;
        }
        ActiveRecording recording = this.activeRecordings.remove(player.getUuid());
        if (recording != null) {
            finalizeRecording(ServerAccess.server(player), recording, "disconnect");
        }
        stop(player.getUuid());
    }

    public synchronized void onPlayerJoin(ServerPlayerEntity player) {
        if (!FEATURE_ENABLED) {
            return;
        }
        if (!PermissionUtil.canUseAdminGui(player, this.configManager)) {
            return;
        }
        if (!this.replaySessions.containsKey(player.getUuid())) {
            return;
        }
        stop(player.getUuid());
    }

    public synchronized void tick(MinecraftServer server) {
        if (!FEATURE_ENABLED || !this.configManager.get().xray_replay.enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        tickRecordings(server, now);
        tickReplaySessions(server, now);
        this.cleanupTicker++;
        if (this.cleanupTicker >= 6000) {
            this.cleanupTicker = 0;
            cleanupExpired(server);
        }
    }

    public synchronized boolean isReplayLocked(UUID uuid) {
        return FEATURE_ENABLED && this.replaySessions.containsKey(uuid);
    }

    public synchronized boolean handleInventoryControlClick(ServerPlayerEntity player, int slotId) {
        if (!FEATURE_ENABLED) {
            return false;
        }
        if (player == null || slotId < 0) {
            return false;
        }
        ReplaySession session = this.replaySessions.get(player.getUuid());
        if (session == null || player.currentScreenHandler == null || slotId >= player.currentScreenHandler.slots.size()) {
            return false;
        }
        Slot clickedSlot = player.currentScreenHandler.getSlot(slotId);
        if (clickedSlot.inventory == player.getInventory()) {
            int invIndex = clickedSlot.getIndex();
            if (invIndex >= 0 && invIndex < CONTROL_NAMES.size()) {
                handleControl(player, session, CONTROL_NAMES.get(invIndex));
                return true;
            }
        }

        ItemStack clicked = clickedSlot.getStack();
        ItemStack cursor = player.currentScreenHandler.getCursorStack();
        String clickedLabel = clicked.isEmpty() ? "" : clicked.getName().getString();
        String cursorLabel = cursor.isEmpty() ? "" : cursor.getName().getString();
        String selectedLabel = player.getMainHandStack().isEmpty() ? "" : player.getMainHandStack().getName().getString();
        String label = CONTROL_NAMES.contains(clickedLabel) ? clickedLabel
                : CONTROL_NAMES.contains(cursorLabel) ? cursorLabel
                : CONTROL_NAMES.contains(selectedLabel) ? selectedLabel
                : "";
        if (label.isEmpty() && ("Replay: Toggle View".equals(clickedLabel) || "Replay: Toggle View".equals(cursorLabel) || "Replay: Toggle View".equals(selectedLabel))) {
            label = "Replay: Toggle View";
        }
        if (label.isEmpty()) {
            return false;
        }
        handleControl(player, session, label);
        return true;
    }

    public synchronized boolean handleCreativeInventoryControl(ServerPlayerEntity player, ItemStack stack, int slotId) {
        if (!FEATURE_ENABLED) {
            return false;
        }
        if (player == null) {
            return false;
        }
        ReplaySession session = this.replaySessions.get(player.getUuid());
        if (session == null) {
            return false;
        }
        if (slotId >= 0 && slotId < CONTROL_NAMES.size()) {
            handleControl(player, session, CONTROL_NAMES.get(slotId));
            return true;
        }
        if (slotId >= 36 && slotId < 36 + CONTROL_NAMES.size()) {
            int mapped = slotId - 36;
            if (mapped >= 0 && mapped < CONTROL_NAMES.size()) {
                handleControl(player, session, CONTROL_NAMES.get(mapped));
                return true;
            }
        }
        String label = stack == null || stack.isEmpty() ? "" : stack.getName().getString();
        if ("Replay: Toggle View".equals(label) || CONTROL_NAMES.contains(label)) {
            handleControl(player, session, label);
            return true;
        }
        return false;
    }

    public synchronized void onTrackedOreBreak(ServerPlayerEntity player, String oreId, BlockPos pos) {
        if (!FEATURE_ENABLED || !this.configManager.get().xray_replay.enabled || !isWatched(player.getUuid())) {
            return;
        }
        ActiveRecording recording = this.activeRecordings.computeIfAbsent(player.getUuid(), ignored -> beginRecording(player));
        long offset = Math.max(0L, System.currentTimeMillis() - recording.startEpochMillis);
        recording.lastActivityEpochMillis = System.currentTimeMillis();
        recording.oreEvents.add(new OreEvent((int) offset, oreId, relX(recording, pos.getX()), relY(recording, pos.getY()), relZ(recording, pos.getZ())));
        recording.oreSummary.put(oreId, recording.oreSummary.getOrDefault(oreId, 0) + 1);
        captureAroundBreak(recording, (ServerWorld) player.getEntityWorld(), pos);
    }

    public synchronized boolean watch(ServerPlayerEntity actor, UUID targetUuid, String targetName, String reason) {
        if (!FEATURE_ENABLED) {
            return false;
        }
        if (targetUuid == null || this.stateManager.state().xrayReplayWatchedPlayers.containsKey(targetUuid.toString())) {
            return false;
        }
        XrayReplayWatchEntryData entry = new XrayReplayWatchEntryData();
        entry.targetUuid = targetUuid.toString();
        entry.lastKnownName = targetName == null ? targetUuid.toString() : targetName;
        entry.reason = reason == null ? "" : reason;
        entry.actorUuid = actor == null ? "" : actor.getUuidAsString();
        entry.actorName = actor == null ? "console" : actor.getGameProfile().name();
        entry.addedAtEpochMillis = System.currentTimeMillis();
        this.stateManager.state().xrayReplayWatchedPlayers.put(entry.targetUuid, entry);
        if (actor != null) {
            this.stateManager.markDirty(ServerAccess.server(actor));
            AuditLogger.sensitive(this.configManager, AuditLogger.actor(actor) + " xrayreplay watch " + entry.lastKnownName + " (" + entry.targetUuid + ")");
        }
        return true;
    }

    public synchronized boolean unwatch(ServerPlayerEntity actor, UUID targetUuid) {
        if (!FEATURE_ENABLED) {
            return false;
        }
        XrayReplayWatchEntryData removed = this.stateManager.state().xrayReplayWatchedPlayers.remove(targetUuid.toString());
        if (removed == null) {
            return false;
        }
        if (actor != null) {
            this.stateManager.markDirty(ServerAccess.server(actor));
            AuditLogger.sensitive(this.configManager, AuditLogger.actor(actor) + " xrayreplay unwatch " + removed.lastKnownName + " (" + removed.targetUuid + ")");
        }
        return true;
    }

    public synchronized boolean isWatched(UUID uuid) {
        return FEATURE_ENABLED && uuid != null && this.stateManager.state().xrayReplayWatchedPlayers.containsKey(uuid.toString());
    }

    public synchronized List<XrayReplayWatchEntryData> watched() {
        if (!FEATURE_ENABLED) {
            return List.of();
        }
        List<XrayReplayWatchEntryData> out = new ArrayList<>(this.stateManager.state().xrayReplayWatchedPlayers.values());
        out.sort(Comparator.comparingLong((XrayReplayWatchEntryData e) -> e.addedAtEpochMillis).reversed());
        return out;
    }

    public synchronized List<SegmentMeta> listSegments(UUID targetUuid) {
        if (!FEATURE_ENABLED) {
            return List.of();
        }
        SegmentIndex index = readIndex(targetUuid);
        List<SegmentMeta> out = new ArrayList<>(index.segments);
        out.sort(Comparator.comparingLong((SegmentMeta e) -> e.startEpochMillis).reversed());
        return out;
    }

    public synchronized SegmentMeta latestSegment(UUID targetUuid) {
        List<SegmentMeta> list = listSegments(targetUuid);
        return list.isEmpty() ? null : list.get(0);
    }

    public synchronized SegmentMeta segmentInfo(UUID targetUuid, long segmentId) {
        for (SegmentMeta meta : listSegments(targetUuid)) {
            if (meta.segmentId == segmentId) {
                return meta;
            }
        }
        return null;
    }

    public synchronized boolean play(ServerPlayerEntity admin, UUID targetUuid, long segmentId) {
        if (!FEATURE_ENABLED) {
            return false;
        }
        SegmentPayload payload = readSegment(targetUuid, segmentId);
        if (admin == null || payload == null || payload.samples.isEmpty()) {
            return false;
        }
        stop(admin.getUuid());
        ReplaySession session = new ReplaySession();
        session.adminUuid = admin.getUuid();
        session.targetUuid = targetUuid;
        session.segmentId = segmentId;
        session.payload = payload;
        session.world = resolveReplayWorld(ServerAccess.server(admin));
        if (session.world == null) {
            return false;
        }
        session.returnWorld = ((ServerWorld) admin.getEntityWorld()).getRegistryKey().getValue().toString();
        session.returnX = admin.getX();
        session.returnY = admin.getY();
        session.returnZ = admin.getZ();
        session.returnYaw = admin.getYaw();
        session.returnPitch = admin.getPitch();
        session.hasReturnPosition = true;
        session.originalGameMode = admin.interactionManager.getGameMode();
        int slot = this.replaySlots.getOrDefault(admin.getUuid(), 0);
        this.replaySlots.put(admin.getUuid(), slot + 1);
        session.baseX = 20_000_000 + (slot % 64) * 256;
        session.baseY = 120;
        session.baseZ = 20_000_000 + ((slot / 64) % 64) * 256;
        session.viewMode = ViewMode.FOLLOW;
        session.speed = Math.max(0.25D, this.configManager.get().xray_replay.replay_default_speed);
        session.paused = true;
        session.lastTickEpochMillis = System.currentTimeMillis();
        saveControlSlots(admin, session);
        debugReplay("play start admin=" + admin.getGameProfile().name()
                + " target=" + payload.playerName
                + " seg#" + segmentId
                + " world=" + session.world.getRegistryKey().getValue()
                + " samples=" + payload.samples.size()
                + " visibleBlocks=" + payload.visibleBlocks.size()
                + " oreEvents=" + payload.oreEvents.size()
                + " relativeMovement=" + payload.relativeMovementSamples
                + " origin=" + payload.originX + "," + payload.originY + "," + payload.originZ);
        ReplayBounds bounds = computeReplayBounds(session.payload);
        debugReplay("play bounds seg#" + segmentId
                + " relX=" + bounds.minX + ".." + bounds.maxX
                + " relY=" + bounds.minY + ".." + bounds.maxY
                + " relZ=" + bounds.minZ + ".." + bounds.maxZ);
        prepareVoidReplayArea(session, bounds);
        int builtCount = buildReplayWorld(session);
        debugReplay("build result seg#" + segmentId + " placedVisibleBlocks=" + builtCount);
        if (builtCount == 0) {
            int fallbackPlaced = buildFallbackRouteWorld(session);
            debugReplay("fallback result seg#" + segmentId + " placedFallbackBlocks=" + fallbackPlaced);
        }
        spawnActor(session);
        giveControlItems(admin);
        applyViewMode(admin, session, ViewMode.FOLLOW);
        this.replaySessions.put(admin.getUuid(), session);
        AuditLogger.sensitive(this.configManager, AuditLogger.actor(admin) + " xrayreplay play " + payload.playerName + " segment#" + segmentId);
        return true;
    }

    public synchronized boolean playLatest(ServerPlayerEntity admin, UUID targetUuid) {
        SegmentMeta latest = latestSegment(targetUuid);
        return latest != null && play(admin, targetUuid, latest.segmentId);
    }

    public synchronized boolean stop(ServerPlayerEntity admin) {
        return admin != null && stop(admin.getUuid());
    }

    public synchronized boolean deleteSegment(ServerPlayerEntity actor, UUID targetUuid, long segmentId) {
        if (!FEATURE_ENABLED) {
            return false;
        }
        SegmentIndex index = readIndex(targetUuid);
        boolean removed = index.segments.removeIf(meta -> meta.segmentId == segmentId);
        if (!removed) {
            return false;
        }
        writeIndex(targetUuid, index);
        try { Files.deleteIfExists(segmentPath(targetUuid, segmentId, true)); } catch (Exception ignored) {}
        try { Files.deleteIfExists(segmentPath(targetUuid, segmentId, false)); } catch (Exception ignored) {}
        if (actor != null) {
            AuditLogger.sensitive(this.configManager, AuditLogger.actor(actor) + " xrayreplay delete segment#" + segmentId + " for " + targetUuid);
        }
        return true;
    }

    public synchronized boolean stop(UUID adminUuid) {
        if (!FEATURE_ENABLED) {
            return false;
        }
        ReplaySession session = this.replaySessions.remove(adminUuid);
        if (session == null) {
            return false;
        }
        ServerPlayerEntity admin = AdminMod.get().server().getPlayerManager().getPlayer(adminUuid);
        if (session.actor != null && !session.actor.isRemoved()) {
            session.actor.remove(Entity.RemovalReason.DISCARDED);
        }
        for (long packed : session.builtBlocks) {
            session.world.setBlockState(BlockPos.fromLong(packed), net.minecraft.block.Blocks.AIR.getDefaultState());
        }
        if (admin != null) {
            restoreControlSlots(admin, session);
            if (session.originalGameMode != null && admin.interactionManager.getGameMode() != session.originalGameMode) {
                admin.changeGameMode(session.originalGameMode);
            }
            if (session.hasReturnPosition) {
                ServerWorld returnWorld = resolveWorldById(ServerAccess.server(admin), session.returnWorld);
                if (returnWorld != null) {
                    admin.teleport(returnWorld, session.returnX, session.returnY, session.returnZ, Set.of(), session.returnYaw, session.returnPitch, false);
                    debugReplay("stop teleport back ok admin=" + admin.getGameProfile().name()
                            + " world=" + session.returnWorld
                            + " pos=" + String.format(Locale.ROOT, "%.2f,%.2f,%.2f", session.returnX, session.returnY, session.returnZ));
                } else {
                    debugReplay("stop teleport back failed admin=" + admin.getGameProfile().name() + " worldNotFound=" + session.returnWorld);
                }
            }
            admin.sendMessage(Text.literal("[XrayReplay] Session ended").formatted(Formatting.YELLOW), false);
            AuditLogger.sensitive(this.configManager, AuditLogger.actor(admin) + " xrayreplay stop segment#" + session.segmentId);
        }
        return true;
    }

    private void registerCallbacks() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            ActiveRecording recording = this.activeRecordings.get(serverPlayer.getUuid());
            if (recording == null) {
                return;
            }
            long offset = Math.max(0L, System.currentTimeMillis() - recording.startEpochMillis);
            String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
            recording.blockEvents.add(new BlockEvent((int) offset, "break", blockId, relX(recording, pos.getX()), relY(recording, pos.getY()), relZ(recording, pos.getZ())));
            recording.lastActivityEpochMillis = System.currentTimeMillis();
            captureAroundBreak(recording, (ServerWorld) world, pos);
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            ReplaySession session = this.replaySessions.get(serverPlayer.getUuid());
            if (session == null) {
                return ActionResult.PASS;
            }
            handleControl(serverPlayer, session, serverPlayer.getStackInHand(hand).getName().getString());
            return ActionResult.FAIL;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> this.replaySessions.containsKey(player.getUuid()) ? ActionResult.FAIL : ActionResult.PASS);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> this.replaySessions.containsKey(player.getUuid()) ? ActionResult.FAIL : ActionResult.PASS);
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> this.replaySessions.containsKey(player.getUuid()) ? ActionResult.FAIL : ActionResult.PASS);
    }

    private void tickRecordings(MinecraftServer server, long now) {
        int moveIntervalMs = Math.max(1, this.configManager.get().xray_replay.movement_sample_interval_ticks) * 50;
        int visibilityIntervalMs = Math.max(1, this.configManager.get().xray_replay.visibility_refresh_interval_ticks) * 50;
        int inactivityMs = Math.max(10, this.configManager.get().xray_replay.inactivity_stop_seconds) * 1000;
        int maxDurationMs = Math.max(1, this.configManager.get().xray_replay.max_segment_duration_minutes) * 60_000;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ActiveRecording recording = this.activeRecordings.get(player.getUuid());
            if (recording == null) {
                continue;
            }
            if (now - recording.startEpochMillis >= maxDurationMs) {
                finalizeRecording(server, recording, "segment_rotate");
                this.activeRecordings.remove(player.getUuid());
                this.activeRecordings.put(player.getUuid(), beginRecording(player));
                continue;
            }
            if (now - recording.lastActivityEpochMillis >= inactivityMs) {
                finalizeRecording(server, recording, "inactive");
                this.activeRecordings.remove(player.getUuid());
                continue;
            }
            if (now - recording.lastMovementSampleEpochMillis >= moveIntervalMs) {
                int offset = (int) Math.max(0L, now - recording.startEpochMillis);
                recording.samples.add(new MovementSample(
                        offset,
                        relX(recording, (int) Math.floor(player.getX())) * 100,
                        relY(recording, (int) Math.floor(player.getY())) * 100,
                        relZ(recording, (int) Math.floor(player.getZ())) * 100,
                        (short) Math.round(player.getYaw() * 10.0F),
                        (short) Math.round(player.getPitch() * 10.0F)
                ));
                recording.lastMovementSampleEpochMillis = now;
            }
            if (now - recording.lastVisibilityCaptureEpochMillis >= visibilityIntervalMs) {
                captureVisibleShell(recording, player);
                recording.lastVisibilityCaptureEpochMillis = now;
            }
        }
    }

    private void tickReplaySessions(MinecraftServer server, long now) {
        for (ReplaySession session : this.replaySessions.values()) {
            ServerPlayerEntity admin = server.getPlayerManager().getPlayer(session.adminUuid);
            if (admin == null || session.actor == null || session.actor.isRemoved()) {
                continue;
            }
            long delta = Math.max(0L, now - session.lastTickEpochMillis);
            session.lastTickEpochMillis = now;
            if (!session.paused) {
                session.cursorMillis = Math.min(session.payload.durationMillis, session.cursorMillis + (long) (delta * session.speed));
            }
            applyReplayFrame(admin, session);
            ensureControlItems(admin);
        }
    }

    private ActiveRecording beginRecording(ServerPlayerEntity player) {
        ActiveRecording recording = new ActiveRecording();
        recording.playerUuid = player.getUuid();
        recording.playerName = player.getGameProfile().name();
        recording.worldKey = player.getEntityWorld().getRegistryKey().getValue().toString();
        recording.dimensionKey = recording.worldKey;
        recording.startEpochMillis = System.currentTimeMillis();
        recording.lastActivityEpochMillis = recording.startEpochMillis;
        recording.originX = (int) Math.floor(player.getX());
        recording.originY = (int) Math.floor(player.getY());
        recording.originZ = (int) Math.floor(player.getZ());
        AuditLogger.sensitive(this.configManager, "xrayreplay recording start for " + recording.playerName + " (" + recording.playerUuid + ")");
        return recording;
    }

    private void captureVisibleShell(ActiveRecording recording, ServerPlayerEntity player) {
        int maxDist = Math.max(2, this.configManager.get().xray_replay.max_visible_block_capture_distance);
        int maxSq = maxDist * maxDist;
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        BlockPos center = player.getBlockPos();
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F).normalize();
        for (int dx = -maxDist; dx <= maxDist; dx++) {
            for (int dy = -maxDist; dy <= maxDist; dy++) {
                for (int dz = -maxDist; dz <= maxDist; dz++) {
                    int distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > maxSq) {
                        continue;
                    }
                    BlockPos pos = center.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir() || !isExposed(world, pos)) {
                        continue;
                    }
                    Vec3d dir = Vec3d.ofCenter(pos).subtract(eye).normalize();
                    double dot = dir.dotProduct(look);
                    if (dot < 0.30D && distSq > 9) {
                        continue;
                    }
                    captureVisibleBlock(recording, pos, state);
                }
            }
        }
    }

    private void captureAroundBreak(ActiveRecording recording, ServerWorld world, BlockPos pos) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos nearby = pos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(nearby);
                    if (!state.isAir() && isExposed(world, nearby)) {
                        captureVisibleBlock(recording, nearby, state);
                    }
                }
            }
        }
    }

    private boolean isExposed(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos.north()).isAir()
                || world.getBlockState(pos.south()).isAir()
                || world.getBlockState(pos.east()).isAir()
                || world.getBlockState(pos.west()).isAir()
                || world.getBlockState(pos.up()).isAir()
                || world.getBlockState(pos.down()).isAir();
    }

    private void captureVisibleBlock(ActiveRecording recording, BlockPos pos, BlockState state) {
        int rx = relX(recording, pos.getX());
        int ry = relY(recording, pos.getY());
        int rz = relZ(recording, pos.getZ());
        long packed = packRelative(rx, ry, rz);
        if (packed == Long.MIN_VALUE) {
            return;
        }
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        int paletteIndex = recording.blockPaletteIndex.computeIfAbsent(blockId, ignored -> {
            recording.blockPalette.add(blockId);
            return recording.blockPalette.size() - 1;
        });
        recording.visibleBlocks.put(packed, paletteIndex);
    }

    private void finalizeRecording(MinecraftServer server, ActiveRecording recording, String reason) {
        if (recording.samples.size() < 2 && recording.oreEvents.isEmpty()) {
            return;
        }
        SegmentPayload payload = new SegmentPayload();
        payload.playerUuid = recording.playerUuid.toString();
        payload.playerName = recording.playerName;
        payload.world = recording.worldKey;
        payload.dimension = recording.dimensionKey;
        payload.startEpochMillis = recording.startEpochMillis;
        payload.endEpochMillis = System.currentTimeMillis();
        payload.durationMillis = Math.max(0L, payload.endEpochMillis - payload.startEpochMillis);
        payload.originX = recording.originX;
        payload.originY = recording.originY;
        payload.originZ = recording.originZ;
        payload.relativeMovementSamples = true;
        payload.samples = recording.samples;
        payload.blockEvents = recording.blockEvents;
        payload.oreEvents = recording.oreEvents;
        payload.blockPalette = recording.blockPalette;
        payload.oreSummary = recording.oreSummary;
        payload.visibleBlocks = new ArrayList<>(recording.visibleBlocks.size());
        for (Map.Entry<Long, Integer> entry : recording.visibleBlocks.entrySet()) {
            int[] rel = unpackRelative(entry.getKey());
            if (rel != null) {
                payload.visibleBlocks.add(new VisibleBlock(rel[0], rel[1], rel[2], entry.getValue()));
            }
        }
        SegmentIndex index = readIndex(recording.playerUuid);
        long segmentId = index.nextSegmentId++;
        if (!writeSegment(recording.playerUuid, segmentId, payload)) {
            return;
        }
        SegmentMeta meta = new SegmentMeta();
        meta.segmentId = segmentId;
        meta.playerUuid = payload.playerUuid;
        meta.playerName = payload.playerName;
        meta.world = payload.world;
        meta.dimension = payload.dimension;
        meta.startEpochMillis = payload.startEpochMillis;
        meta.endEpochMillis = payload.endEpochMillis;
        meta.durationMillis = payload.durationMillis;
        meta.oreSummary = new LinkedHashMap<>(payload.oreSummary);
        meta.movementSampleCount = payload.samples.size();
        meta.blockEventCount = payload.blockEvents.size();
        meta.visibleBlockCount = payload.visibleBlocks.size();
        meta.suspicionSummary = "reason=" + reason + ", ores=" + payload.oreEvents.size();
        index.segments.add(meta);
        writeIndex(recording.playerUuid, index);
        this.stateManager.markDirty(server);
        AuditLogger.sensitive(this.configManager, "xrayreplay segment created #" + segmentId + " for " + recording.playerName + " (" + recording.playerUuid + ")");
        debugReplay("segment meta #" + segmentId
                + " samples=" + payload.samples.size()
                + " visibleBlocks=" + payload.visibleBlocks.size()
                + " oreEvents=" + payload.oreEvents.size()
                + " relativeMovement=" + payload.relativeMovementSamples
                + " origin=" + payload.originX + "," + payload.originY + "," + payload.originZ);
    }

    private int buildReplayWorld(ReplaySession session) {
        int built = 0;
        int invalidId = 0;
        int missingBlock = 0;
        int yOut = 0;
        for (VisibleBlock block : session.payload.visibleBlocks) {
            String blockId = block.paletteIndex >= 0 && block.paletteIndex < session.payload.blockPalette.size()
                    ? session.payload.blockPalette.get(block.paletteIndex)
                    : "minecraft:stone";
            var id = net.minecraft.util.Identifier.tryParse(blockId);
            if (id == null) {
                invalidId++;
                continue;
            }
            var blockObj = Registries.BLOCK.get(id);
            if (blockObj == null) {
                missingBlock++;
                continue;
            }
            int y = session.baseY + block.y;
            if (y < session.world.getBottomY() || y > session.world.getTopYInclusive()) {
                yOut++;
                continue;
            }
            BlockPos pos = new BlockPos(session.baseX + block.x, y, session.baseZ + block.z);
            session.world.setBlockState(pos, blockObj.getDefaultState());
            session.builtBlocks.add(pos.asLong());
            built++;
        }
        debugReplay("build detail seg#" + session.segmentId
                + " built=" + built
                + " invalidId=" + invalidId
                + " missingBlock=" + missingBlock
                + " yOut=" + yOut);
        return built;
    }

    private ServerWorld resolveReplayWorld(MinecraftServer server) {
        String configured = this.configManager.get().xray_replay.replay_world;
        Identifier id = Identifier.tryParse(configured);
        if (id != null) {
            RegistryKey<net.minecraft.world.World> key = RegistryKey.of(RegistryKeys.WORLD, id);
            for (int i = 0; i < 8; i++) {
                ServerWorld world = server.getWorld(key);
                if (world != null) {
                    debugReplay("resolved replay world=" + configured + " attempt=" + (i + 1));
                    return world;
                }
            }
            AuditLogger.sensitive(this.configManager, "xrayreplay world '" + configured + "' not found, fallback to minecraft:the_end");
        }
        ServerWorld fallback = server.getWorld(net.minecraft.world.World.END);
        if (fallback != null) {
            debugReplay("using fallback replay world=minecraft:the_end");
        }
        return fallback;
    }

    private void prepareVoidReplayArea(ReplaySession session, ReplayBounds bounds) {
        int configuredPadding = Math.max(8, this.configManager.get().xray_replay.replay_void_clear_radius / 4);
        int minX = session.baseX + bounds.minX - configuredPadding;
        int maxX = session.baseX + bounds.maxX + configuredPadding;
        int minZ = session.baseZ + bounds.minZ - configuredPadding;
        int maxZ = session.baseZ + bounds.maxZ + configuredPadding;
        int minY = Math.max(session.world.getBottomY(), session.baseY + bounds.minY - 12);
        int maxY = Math.min(session.world.getTopYInclusive(), session.baseY + bounds.maxY + 12);
        int loadedChunks = loadChunksForArea(session.world, minX, maxX, minZ, maxZ);
        debugReplay("prepare area seg#" + session.segmentId
                + " x=" + minX + ".." + maxX
                + " y=" + minY + ".." + maxY
                + " z=" + minZ + ".." + maxZ
                + " loadedChunks=" + loadedChunks);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    session.world.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState());
                }
            }
        }
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                session.world.setBlockState(new BlockPos(session.baseX + x, session.baseY - 1, session.baseZ + z), net.minecraft.block.Blocks.BEDROCK.getDefaultState());
                session.builtBlocks.add(new BlockPos(session.baseX + x, session.baseY - 1, session.baseZ + z).asLong());
            }
        }
    }

    private int loadChunksForArea(ServerWorld world, int minX, int maxX, int minZ, int maxZ) {
        int minChunkX = Math.floorDiv(minX, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);
        int loaded = 0;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                world.getChunk(chunkX, chunkZ);
                loaded++;
            }
        }
        return loaded;
    }

    private int buildFallbackRouteWorld(ReplaySession session) {
        int bottomY = session.world.getBottomY();
        int topY = session.world.getTopYInclusive();
        int placed = 0;
        for (int i = 0; i < session.payload.samples.size(); i += 2) {
            MovementSample sample = session.payload.samples.get(i);
            int x = session.baseX + sampleRelX(session.payload, sample);
            int y = session.baseY + sampleRelY(session.payload, sample);
            int z = session.baseZ + sampleRelZ(session.payload, sample);
            if (y - 1 < bottomY || y + 2 > topY) {
                continue;
            }
            BlockPos floor = new BlockPos(x, y - 1, z);
            BlockPos head = new BlockPos(x, y, z);
            session.world.setBlockState(floor, net.minecraft.block.Blocks.STONE.getDefaultState());
            session.world.setBlockState(head, net.minecraft.block.Blocks.AIR.getDefaultState());
            session.builtBlocks.add(floor.asLong());
            session.builtBlocks.add(head.asLong());
            placed += 2;
        }
        for (OreEvent ore : session.payload.oreEvents) {
            int x = session.baseX + ore.x;
            int y = session.baseY + ore.y;
            int z = session.baseZ + ore.z;
            if (y < bottomY || y > topY) {
                continue;
            }
            BlockPos marker = new BlockPos(x, y, z);
            session.world.setBlockState(marker, net.minecraft.block.Blocks.GOLD_BLOCK.getDefaultState());
            session.builtBlocks.add(marker.asLong());
            placed++;
        }
        return placed;
    }

    private ReplayBounds computeReplayBounds(SegmentPayload payload) {
        int fallback = Math.max(16, this.configManager.get().xray_replay.max_reconstructed_replay_radius_per_step);
        int minX = 0;
        int maxX = 0;
        int minY = -8;
        int maxY = 24;
        int minZ = 0;
        int maxZ = 0;
        boolean hasData = false;

        for (VisibleBlock block : payload.visibleBlocks) {
            minX = hasData ? Math.min(minX, block.x) : block.x;
            maxX = hasData ? Math.max(maxX, block.x) : block.x;
            minY = hasData ? Math.min(minY, block.y) : block.y;
            maxY = hasData ? Math.max(maxY, block.y) : block.y;
            minZ = hasData ? Math.min(minZ, block.z) : block.z;
            maxZ = hasData ? Math.max(maxZ, block.z) : block.z;
            hasData = true;
        }
        for (MovementSample sample : payload.samples) {
            int sx = sampleRelX(payload, sample);
            int sy = sampleRelY(payload, sample);
            int sz = sampleRelZ(payload, sample);
            minX = hasData ? Math.min(minX, sx) : sx;
            maxX = hasData ? Math.max(maxX, sx) : sx;
            minY = hasData ? Math.min(minY, sy) : sy;
            maxY = hasData ? Math.max(maxY, sy) : sy;
            minZ = hasData ? Math.min(minZ, sz) : sz;
            maxZ = hasData ? Math.max(maxZ, sz) : sz;
            hasData = true;
        }
        if (!hasData) {
            return new ReplayBounds(-fallback, fallback, -12, 32, -fallback, fallback);
        }
        return new ReplayBounds(minX, maxX, minY, maxY, minZ, maxZ);
    }

    private ServerWorld resolveWorldById(MinecraftServer server, String worldId) {
        Identifier id = Identifier.tryParse(worldId);
        if (id == null) {
            return null;
        }
        RegistryKey<net.minecraft.world.World> key = RegistryKey.of(RegistryKeys.WORLD, id);
        return server.getWorld(key);
    }

    private void spawnActor(ReplaySession session) {
        MovementSample first = session.payload.samples.get(0);
        double x = session.baseX + (first.posX / 100.0D);
        double y = session.baseY + (first.posY / 100.0D);
        double z = session.baseZ + (first.posZ / 100.0D);
        ArmorStandEntity actor = new ArmorStandEntity(session.world, x, y, z);
        actor.setNoGravity(true);
        actor.setInvulnerable(true);
        actor.setSilent(true);
        session.world.spawnEntity(actor);
        session.actor = actor;
    }

    private void applyReplayFrame(ServerPlayerEntity admin, ReplaySession session) {
        MovementFrame frame = movementAt(session.payload.samples, session.payload, session.cursorMillis);
        if (frame == null) {
            return;
        }
        double x = session.baseX + frame.x;
        double y = session.baseY + frame.y;
        double z = session.baseZ + frame.z;
        session.actor.refreshPositionAndAngles(x, y, z, frame.yaw, frame.pitch);
        if (session.viewMode == ViewMode.FOLLOW) {
            admin.teleport(session.world, x, y + 0.05D, z, Set.of(), frame.yaw, frame.pitch, false);
        }
        while (session.nextOreIndex < session.payload.oreEvents.size()
                && session.payload.oreEvents.get(session.nextOreIndex).offsetMillis <= session.cursorMillis) {
            OreEvent ore = session.payload.oreEvents.get(session.nextOreIndex);
            session.world.spawnParticles(ParticleTypes.CRIT, session.baseX + ore.x + 0.5D, session.baseY + ore.y + 0.5D, session.baseZ + ore.z + 0.5D, 18, 0.2D, 0.2D, 0.2D, 0.01D);
            session.nextOreIndex++;
        }
        String hud = "[Replay] " + session.payload.playerName + " seg#" + session.segmentId + " "
                + formatDuration(session.cursorMillis) + "/" + formatDuration(session.payload.durationMillis)
                + " " + String.format(Locale.ROOT, "%.2fx", session.speed) + " " + session.viewMode
                + (session.paused ? " PAUSED" : "");
        admin.sendMessage(Text.literal(hud).formatted(Formatting.AQUA), true);
    }

    private void teleportForView(ServerPlayerEntity admin, ReplaySession session) {
        MovementFrame frame = movementAt(session.payload.samples, session.payload, session.cursorMillis);
        if (frame == null) {
            return;
        }
        if (session.viewMode == ViewMode.FOLLOW) {
            admin.teleport(session.world, session.baseX + frame.x, session.baseY + frame.y + 0.05D, session.baseZ + frame.z, Set.of(), frame.yaw, frame.pitch, false);
        } else {
            admin.teleport(session.world, session.baseX + frame.x + 1.0D, session.baseY + frame.y + 1.0D, session.baseZ + frame.z + 1.0D, Set.of(), frame.yaw, frame.pitch, false);
        }
    }

    private void handleControl(ServerPlayerEntity admin, ReplaySession session, String label) {
        switch (label) {
            case "Replay: Play/Resume" -> session.paused = false;
            case "Replay: Pause" -> session.paused = true;
            case "Replay: Restart" -> {
                session.cursorMillis = 0L;
                session.nextOreIndex = 0;
                session.paused = true;
            }
            case "Replay: Rewind 5s" -> seek(session, session.cursorMillis - 5000L);
            case "Replay: Rewind 30s" -> seek(session, session.cursorMillis - 30000L);
            case "Replay: Forward 5s" -> seek(session, session.cursorMillis + 5000L);
            case "Replay: Forward 30s" -> seek(session, session.cursorMillis + 30000L);
            case "Replay: Speed -" -> session.speed = Math.max(0.25D, session.speed - 0.25D);
            case "Replay: Speed +" -> session.speed = Math.min(8.0D, session.speed + 0.25D);
            case "Replay: Follow View" -> applyViewMode(admin, session, ViewMode.FOLLOW);
            case "Replay: Spectator View" -> applyViewMode(admin, session, ViewMode.SPECTATOR);
            case "Replay: Toggle View" -> applyViewMode(admin, session, session.viewMode == ViewMode.FOLLOW ? ViewMode.SPECTATOR : ViewMode.FOLLOW);
            case "Replay: Exit" -> stop(admin);
            default -> {
            }
        }
    }

    private void applyViewMode(ServerPlayerEntity admin, ReplaySession session, ViewMode mode) {
        session.viewMode = mode;
        if (mode == ViewMode.SPECTATOR) {
            if (admin.interactionManager.getGameMode() != net.minecraft.world.GameMode.SPECTATOR) {
                admin.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            }
        } else {
            if (admin.interactionManager.getGameMode() != net.minecraft.world.GameMode.ADVENTURE) {
                admin.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
            }
        }
        teleportForView(admin, session);
    }

    private void seek(ReplaySession session, long targetMillis) {
        session.cursorMillis = Math.max(0L, Math.min(session.payload.durationMillis, targetMillis));
        session.nextOreIndex = 0;
        while (session.nextOreIndex < session.payload.oreEvents.size()
                && session.payload.oreEvents.get(session.nextOreIndex).offsetMillis <= session.cursorMillis) {
            session.nextOreIndex++;
        }
    }

    private void saveControlSlots(ServerPlayerEntity admin, ReplaySession session) {
        for (int i = 0; i < session.savedSlots.length; i++) {
            session.savedSlots[i] = admin.getInventory().getStack(i).copy();
        }
    }

    private void restoreControlSlots(ServerPlayerEntity admin, ReplaySession session) {
        for (int i = 0; i < session.savedSlots.length; i++) {
            admin.getInventory().setStack(i, session.savedSlots[i] == null ? ItemStack.EMPTY : session.savedSlots[i]);
        }
        admin.getInventory().markDirty();
    }

    private void giveControlItems(ServerPlayerEntity admin) {
        for (int i = 0; i < CONTROL_NAMES.size(); i++) {
            ItemStack stack = switch (i) {
                case 0 -> new ItemStack(Items.LIME_DYE);
                case 1 -> new ItemStack(Items.RED_DYE);
                case 2 -> new ItemStack(Items.COMPASS);
                case 3, 4 -> new ItemStack(Items.ARROW);
                case 5, 6 -> new ItemStack(Items.SPECTRAL_ARROW);
                case 7, 8 -> new ItemStack(Items.CLOCK);
                case 9 -> new ItemStack(Items.RECOVERY_COMPASS);
                case 10 -> new ItemStack(Items.ENDER_EYE);
                default -> new ItemStack(Items.BARRIER);
            };
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(CONTROL_NAMES.get(i)).formatted(Formatting.GOLD));
            admin.getInventory().setStack(i, stack);
        }
        admin.getInventory().markDirty();
    }

    private void ensureControlItems(ServerPlayerEntity admin) {
        for (int i = 0; i < CONTROL_NAMES.size(); i++) {
            ItemStack stack = admin.getInventory().getStack(i);
            if (stack.isEmpty() || !CONTROL_NAMES.get(i).equals(stack.getName().getString())) {
                giveControlItems(admin);
                return;
            }
        }
    }

    private int relX(ActiveRecording recording, int x) { return x - recording.originX; }
    private int relY(ActiveRecording recording, int y) { return y - recording.originY; }
    private int relZ(ActiveRecording recording, int z) { return z - recording.originZ; }

    private long packRelative(int x, int y, int z) {
        int bias = 1_048_576;
        int bx = x + bias, by = y + bias, bz = z + bias;
        if ((bx & ~0x1FFFFF) != 0 || (by & ~0x1FFFFF) != 0 || (bz & ~0x1FFFFF) != 0) return Long.MIN_VALUE;
        return ((long) bx << 42) | ((long) by << 21) | bz;
    }

    private int[] unpackRelative(long packed) {
        int bias = 1_048_576;
        int bx = (int) ((packed >>> 42) & 0x1FFFFF);
        int by = (int) ((packed >>> 21) & 0x1FFFFF);
        int bz = (int) (packed & 0x1FFFFF);
        return new int[]{bx - bias, by - bias, bz - bias};
    }

    private MovementFrame movementAt(List<MovementSample> samples, SegmentPayload payload, long offset) {
        if (samples.isEmpty()) return null;
        if (offset <= samples.get(0).offsetMillis) {
            MovementSample s = samples.get(0);
            return new MovementFrame(s.offsetMillis, sampleRelX(payload, s), sampleRelY(payload, s), sampleRelZ(payload, s), s.yaw10 / 10.0F, s.pitch10 / 10.0F);
        }
        MovementSample prev = samples.get(0);
        for (int i = 1; i < samples.size(); i++) {
            MovementSample cur = samples.get(i);
            if (cur.offsetMillis >= offset) {
                double t = (offset - prev.offsetMillis) / (double) Math.max(1, cur.offsetMillis - prev.offsetMillis);
                return new MovementFrame((int) offset,
                        lerp(sampleRelX(payload, prev), sampleRelX(payload, cur), t),
                        lerp(sampleRelY(payload, prev), sampleRelY(payload, cur), t),
                        lerp(sampleRelZ(payload, prev), sampleRelZ(payload, cur), t),
                        (float) lerp(prev.yaw10 / 10.0D, cur.yaw10 / 10.0D, t),
                        (float) lerp(prev.pitch10 / 10.0D, cur.pitch10 / 10.0D, t));
            }
            prev = cur;
        }
        MovementSample last = samples.get(samples.size() - 1);
        return new MovementFrame(last.offsetMillis, sampleRelX(payload, last), sampleRelY(payload, last), sampleRelZ(payload, last), last.yaw10 / 10.0F, last.pitch10 / 10.0F);
    }

    private double lerp(double a, double b, double t) { return a + (b - a) * Math.max(0.0D, Math.min(1.0D, t)); }

    private int sampleRelX(SegmentPayload payload, MovementSample sample) {
        return normalizeSampleComponent(payload, sample.posX / 100, payload == null ? 0 : payload.originX);
    }

    private int sampleRelY(SegmentPayload payload, MovementSample sample) {
        return normalizeSampleComponent(payload, sample.posY / 100, payload == null ? 0 : payload.originY);
    }

    private int sampleRelZ(SegmentPayload payload, MovementSample sample) {
        return normalizeSampleComponent(payload, sample.posZ / 100, payload == null ? 0 : payload.originZ);
    }

    private int normalizeSampleComponent(SegmentPayload payload, int blockCoord, int originCoord) {
        if (payload == null || payload.relativeMovementSamples) {
            return blockCoord;
        }
        return blockCoord - originCoord;
    }

    private void debugReplay(String message) {
        AuditLogger.sensitive(this.configManager, "xrayreplay debug " + message);
    }

    private String formatDuration(long millis) {
        long sec = Math.max(0, millis / 1000L);
        return String.format(Locale.ROOT, "%02d:%02d", sec / 60L, sec % 60L);
    }

    private void cleanupExpired(MinecraftServer server) {
        int retentionDays = Math.max(1, this.configManager.get().xray_replay.replay_retention_days);
        long cutoff = System.currentTimeMillis() - retentionDays * 86_400_000L;
        for (XrayReplayWatchEntryData entry : watched()) {
            UUID uuid;
            try { uuid = UUID.fromString(entry.targetUuid); } catch (IllegalArgumentException ex) { continue; }
            SegmentIndex index = readIndex(uuid);
            List<Long> removeIds = new ArrayList<>();
            index.segments.removeIf(meta -> {
                boolean remove = meta.endEpochMillis > 0 && meta.endEpochMillis < cutoff;
                if (remove) removeIds.add(meta.segmentId);
                return remove;
            });
            if (!removeIds.isEmpty()) {
                for (Long id : removeIds) {
                    try { Files.deleteIfExists(segmentPath(uuid, id, true)); } catch (Exception ignored) {}
                    try { Files.deleteIfExists(segmentPath(uuid, id, false)); } catch (Exception ignored) {}
                }
                writeIndex(uuid, index);
                this.stateManager.markDirty(server);
            }
        }
    }

    private SegmentIndex readIndex(UUID playerUuid) {
        SegmentIndex fallback = new SegmentIndex();
        Path path = indexPath(playerUuid);
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) return fallback;
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                SegmentIndex read = GSON.fromJson(reader, SegmentIndex.class);
                if (read == null) return fallback;
                if (read.segments == null) read.segments = new ArrayList<>();
                if (read.nextSegmentId <= 0) {
                    long max = 0;
                    for (SegmentMeta meta : read.segments) max = Math.max(max, meta.segmentId);
                    read.nextSegmentId = max + 1;
                }
                return read;
            }
        } catch (Exception exception) {
            AuditLogger.sensitive(this.configManager, "xrayreplay index read failed for " + playerUuid + ": " + exception.getMessage());
            return fallback;
        }
    }

    private void writeIndex(UUID playerUuid, SegmentIndex index) {
        Path path = indexPath(playerUuid);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(index, writer);
            }
        } catch (Exception exception) {
            AuditLogger.sensitive(this.configManager, "xrayreplay index write failed for " + playerUuid + ": " + exception.getMessage());
        }
    }

    private boolean writeSegment(UUID playerUuid, long segmentId, SegmentPayload payload) {
        boolean compressed = this.configManager.get().xray_replay.compression_enabled;
        Path path = segmentPath(playerUuid, segmentId, compressed);
        try {
            Files.createDirectories(path.getParent());
            if (compressed) {
                try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(path));
                     Writer writer = new java.io.OutputStreamWriter(gzip, StandardCharsets.UTF_8)) {
                    GSON.toJson(payload, writer);
                }
            } else {
                try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    GSON.toJson(payload, writer);
                }
            }
            return true;
        } catch (Exception exception) {
            AuditLogger.sensitive(this.configManager, "xrayreplay segment write failed for " + playerUuid + "#" + segmentId + ": " + exception.getMessage());
            return false;
        }
    }

    private SegmentPayload readSegment(UUID playerUuid, long segmentId) {
        Path gz = segmentPath(playerUuid, segmentId, true);
        Path plain = segmentPath(playerUuid, segmentId, false);
        if (Files.exists(gz)) {
            try (GZIPInputStream gzip = new GZIPInputStream(Files.newInputStream(gz));
                 Reader reader = new java.io.InputStreamReader(gzip, StandardCharsets.UTF_8)) {
                SegmentPayload payload = GSON.fromJson(reader, SegmentPayload.class);
                debugReplay("read segment file=" + gz + " compressed=true");
                return normalize(payload);
            } catch (Exception ignored) {
                debugReplay("read segment failed file=" + gz + " error=" + ignored.getMessage());
            }
        }
        if (Files.exists(plain)) {
            try (Reader reader = Files.newBufferedReader(plain, StandardCharsets.UTF_8)) {
                SegmentPayload payload = GSON.fromJson(reader, SegmentPayload.class);
                debugReplay("read segment file=" + plain + " compressed=false");
                return normalize(payload);
            } catch (Exception ignored) {
                debugReplay("read segment failed file=" + plain + " error=" + ignored.getMessage());
            }
        }
        debugReplay("read segment missing player=" + playerUuid + " seg#" + segmentId);
        return null;
    }

    private SegmentPayload normalize(SegmentPayload payload) {
        if (payload == null) return null;
        if (payload.samples == null) payload.samples = new ArrayList<>();
        if (payload.blockEvents == null) payload.blockEvents = new ArrayList<>();
        if (payload.oreEvents == null) payload.oreEvents = new ArrayList<>();
        if (payload.blockPalette == null) payload.blockPalette = new ArrayList<>();
        if (payload.visibleBlocks == null) payload.visibleBlocks = new ArrayList<>();
        if (payload.oreSummary == null) payload.oreSummary = new LinkedHashMap<>();
        return payload;
    }

    private Path basePath() {
        MinecraftServer server = AdminMod.get() == null ? null : AdminMod.get().server();
        return server == null ? Path.of("adminmod-xrayreplay") : server.getPath("adminmod-xrayreplay");
    }

    private Path playerPath(UUID playerUuid) { return basePath().resolve("players").resolve(playerUuid.toString()); }
    private Path indexPath(UUID playerUuid) { return playerPath(playerUuid).resolve("index.json"); }
    private Path segmentPath(UUID playerUuid, long id, boolean compressed) {
        return playerPath(playerUuid).resolve("segments").resolve(id + (compressed ? ".json.gz" : ".json"));
    }

    private static final class ActiveRecording {
        public UUID playerUuid;
        public String playerName = "";
        public String worldKey = "";
        public String dimensionKey = "";
        public long startEpochMillis;
        public long lastActivityEpochMillis;
        public long lastMovementSampleEpochMillis;
        public long lastVisibilityCaptureEpochMillis;
        public int originX;
        public int originY;
        public int originZ;
        public List<MovementSample> samples = new ArrayList<>();
        public List<BlockEvent> blockEvents = new ArrayList<>();
        public List<OreEvent> oreEvents = new ArrayList<>();
        public List<String> blockPalette = new ArrayList<>();
        public Map<String, Integer> blockPaletteIndex = new HashMap<>();
        public Map<Long, Integer> visibleBlocks = new HashMap<>();
        public Map<String, Integer> oreSummary = new LinkedHashMap<>();
    }

    private static final class ReplaySession {
        public UUID adminUuid;
        public UUID targetUuid;
        public long segmentId;
        public SegmentPayload payload;
        public ServerWorld world;
        public ArmorStandEntity actor;
        public int baseX;
        public int baseY;
        public int baseZ;
        public long cursorMillis;
        public long lastTickEpochMillis;
        public double speed = 1.0D;
        public boolean paused = true;
        public ViewMode viewMode = ViewMode.FOLLOW;
        public int nextOreIndex = 0;
        public Set<Long> builtBlocks = new HashSet<>();
        public ItemStack[] savedSlots = new ItemStack[12];
        public String returnWorld = "";
        public double returnX;
        public double returnY;
        public double returnZ;
        public float returnYaw;
        public float returnPitch;
        public boolean hasReturnPosition;
        public net.minecraft.world.GameMode originalGameMode;
    }

    private enum ViewMode { FOLLOW, SPECTATOR }

    public static final class SegmentMeta {
        public long segmentId;
        public String playerUuid = "";
        public String playerName = "";
        public String world = "";
        public String dimension = "";
        public long startEpochMillis;
        public long endEpochMillis;
        public long durationMillis;
        public Map<String, Integer> oreSummary = new LinkedHashMap<>();
        public int movementSampleCount;
        public int blockEventCount;
        public int visibleBlockCount;
        public String suspicionSummary = "";
    }

    private static final class SegmentIndex {
        public long nextSegmentId = 1L;
        public List<SegmentMeta> segments = new ArrayList<>();
    }

    private static final class SegmentPayload {
        public String playerUuid = "";
        public String playerName = "";
        public String world = "";
        public String dimension = "";
        public long startEpochMillis;
        public long endEpochMillis;
        public long durationMillis;
        public int originX;
        public int originY;
        public int originZ;
        public boolean relativeMovementSamples;
        public List<MovementSample> samples = new ArrayList<>();
        public List<BlockEvent> blockEvents = new ArrayList<>();
        public List<OreEvent> oreEvents = new ArrayList<>();
        public List<String> blockPalette = new ArrayList<>();
        public List<VisibleBlock> visibleBlocks = new ArrayList<>();
        public Map<String, Integer> oreSummary = new LinkedHashMap<>();
    }

    private record MovementSample(int offsetMillis, int posX, int posY, int posZ, short yaw10, short pitch10) {}
    private record BlockEvent(int offsetMillis, String type, String blockId, int x, int y, int z) {}
    private record OreEvent(int offsetMillis, String oreId, int x, int y, int z) {}
    private record VisibleBlock(int x, int y, int z, int paletteIndex) {}
    private record MovementFrame(int offsetMillis, double x, double y, double z, float yaw, float pitch) {}
    private record ReplayBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {}
}
