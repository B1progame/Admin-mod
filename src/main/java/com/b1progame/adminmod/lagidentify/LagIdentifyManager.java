package com.b1progame.adminmod.lagidentify;

import com.b1progame.adminmod.AdminMod;
import com.b1progame.adminmod.config.ConfigManager;
import com.b1progame.adminmod.state.LagIdentifyResultData;
import com.b1progame.adminmod.state.PersistentStateData;
import com.b1progame.adminmod.state.StateManager;
import com.b1progame.adminmod.util.AuditLogger;
import com.b1progame.adminmod.util.PermissionUtil;
import com.b1progame.adminmod.util.ServerAccess;
import net.minecraft.block.AbstractRedstoneGateBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ComparatorBlock;
import net.minecraft.block.ObserverBlock;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.RedstoneLampBlock;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LagIdentifyManager {
    private final ConfigManager configManager;
    private final StateManager stateManager;
    private ActiveScan activeScan;
    private final Map<UUID, BackLocation> backLocations = new HashMap<>();
    private long cleanupTicker = 0L;

    public LagIdentifyManager(ConfigManager configManager, StateManager stateManager) {
        this.configManager = configManager;
        this.stateManager = stateManager;
    }

    public synchronized void onServerStarted(MinecraftServer server) {
        cleanupExpiredResults(server);
    }

    public synchronized void onServerStopping(MinecraftServer server) {
        this.activeScan = null;
    }

    public synchronized void onPlayerDisconnect(ServerPlayerEntity player) {
        this.backLocations.remove(player.getUuid());
    }

    public synchronized void tick(MinecraftServer server) {
        if (!this.configManager.get().lag_identify.lag_identify_enabled) {
            return;
        }
        tickScan(server);
        this.cleanupTicker++;
        if (this.cleanupTicker >= 1200L) {
            this.cleanupTicker = 0L;
            cleanupExpiredResults(server);
        }
    }

    public synchronized boolean isScanRunning() {
        return this.activeScan != null;
    }

    public synchronized String activeScanProgress() {
        if (this.activeScan == null) {
            return "No active scan.";
        }
        int total = Math.max(1, this.activeScan.targets.size());
        int done = Math.min(total, this.activeScan.index);
        int percent = (int) Math.round((done * 100.0D) / total);
        return "Lag scan " + done + "/" + total + " (" + percent + "%)";
    }

    public synchronized boolean startScan(ServerPlayerEntity actor, String worldIdOrMode) {
        if (!this.configManager.get().lag_identify.lag_identify_enabled || actor == null) {
            return false;
        }
        if (!PermissionUtil.canUseAdminGui(actor, this.configManager)) {
            return false;
        }
        if (this.activeScan != null) {
            return false;
        }
        ActiveScan scan = new ActiveScan();
        scan.startedAtEpochMillis = System.currentTimeMillis();
        scan.startedByUuid = actor.getUuid();
        scan.startedByName = actor.getGameProfile().name();
        scan.scanSessionId = this.stateManager.state().nextLagIdentifyScanSessionId++;
        scan.worldFilter = resolveWorldFilter(worldIdOrMode);
        scan.mode = resolveScanMode(worldIdOrMode);
        scan.targets = collectTargets(ServerAccess.server(actor), scan.worldFilter);
        scan.entityStatsByWorldChunk = precomputeEntityStats(ServerAccess.server(actor), scan.worldFilter);
        this.activeScan = scan;
        this.stateManager.markDirty(ServerAccess.server(actor));
        AuditLogger.sensitive(this.configManager, AuditLogger.actor(actor) + " lagidentify scan start mode=" + scan.mode + " world=" + scan.worldFilter + " chunks=" + scan.targets.size());
        return true;
    }

    public synchronized boolean stopScan(ServerPlayerEntity actor) {
        if (this.activeScan == null) {
            return false;
        }
        this.activeScan = null;
        if (actor != null) {
            AuditLogger.sensitive(this.configManager, AuditLogger.actor(actor) + " lagidentify scan stopped");
        }
        return true;
    }

    public synchronized List<LagIdentifyResultData> listResults() {
        List<LagIdentifyResultData> out = new ArrayList<>(this.stateManager.state().lagIdentifyResults);
        out.sort(Comparator.comparingDouble((LagIdentifyResultData r) -> r.score).reversed());
        return out;
    }

    public synchronized LagIdentifyResultData getResult(long id) {
        for (LagIdentifyResultData result : this.stateManager.state().lagIdentifyResults) {
            if (result.resultId == id) {
                return result;
            }
        }
        return null;
    }

    public synchronized boolean clearResults(ServerPlayerEntity actor) {
        this.stateManager.state().lagIdentifyResults.clear();
        if (actor != null) {
            this.stateManager.markDirty(ServerAccess.server(actor));
            AuditLogger.sensitive(this.configManager, AuditLogger.actor(actor) + " lagidentify clear results");
        }
        return true;
    }

    public synchronized boolean teleportToResult(ServerPlayerEntity actor, long resultId) {
        if (actor == null) {
            return false;
        }
        LagIdentifyResultData result = getResult(resultId);
        if (result == null) {
            return false;
        }
        ServerWorld world = resolveWorld(ServerAccess.server(actor), result.world);
        if (world == null) {
            return false;
        }
        BackLocation back = new BackLocation();
        back.world = ((ServerWorld) actor.getEntityWorld()).getRegistryKey().getValue().toString();
        back.x = actor.getX();
        back.y = actor.getY();
        back.z = actor.getZ();
        back.yaw = actor.getYaw();
        back.pitch = actor.getPitch();
        back.gameMode = actor.interactionManager.getGameMode();
        back.autoVanishApplied = false;
        if (this.configManager.get().lag_identify.lag_identify_auto_vanish_on_tp
                && AdminMod.get() != null
                && AdminMod.get().vanishManager() != null
                && !AdminMod.get().vanishManager().isVanished(actor.getUuid())) {
            AdminMod.get().vanishManager().toggleVanish(actor, actor);
            back.autoVanishApplied = true;
        }
        if (this.configManager.get().lag_identify.lag_identify_auto_staff_mode_on_tp) {
            actor.changeGameMode(GameMode.SPECTATOR);
        }
        this.backLocations.put(actor.getUuid(), back);
        actor.teleport(world, result.centerX + 0.5D, result.centerY + 1.5D, result.centerZ + 0.5D, Set.of(), actor.getYaw(), actor.getPitch(), false);
        world.spawnParticles(actor, ParticleTypes.END_ROD, true, true, result.centerX + 0.5D, result.centerY + 1.2D, result.centerZ + 0.5D, 18, 0.35D, 0.45D, 0.35D, 0.01D);
        actor.sendMessage(Text.literal("[LagIdentify] Teleported to hotspot #" + result.resultId + " score=" + String.format(Locale.ROOT, "%.1f", result.score)).formatted(Formatting.GOLD), false);
        AuditLogger.sensitive(this.configManager, AuditLogger.actor(actor) + " lagidentify tp #" + result.resultId + " world=" + result.world + " chunk=" + result.chunkX + "," + result.chunkZ);
        return true;
    }

    public synchronized boolean back(ServerPlayerEntity actor) {
        if (actor == null) {
            return false;
        }
        BackLocation back = this.backLocations.remove(actor.getUuid());
        if (back == null) {
            return false;
        }
        ServerWorld world = resolveWorld(ServerAccess.server(actor), back.world);
        if (world == null) {
            return false;
        }
        actor.teleport(world, back.x, back.y, back.z, Set.of(), back.yaw, back.pitch, false);
        if (actor.interactionManager.getGameMode() != back.gameMode) {
            actor.changeGameMode(back.gameMode);
        }
        if (back.autoVanishApplied && AdminMod.get() != null && AdminMod.get().vanishManager() != null && AdminMod.get().vanishManager().isVanished(actor.getUuid())) {
            AdminMod.get().vanishManager().toggleVanish(actor, actor);
        }
        return true;
    }

    public synchronized String settingsSummary() {
        var cfg = this.configManager.get().lag_identify;
        return "enabled=" + cfg.lag_identify_enabled
                + ", batch=" + cfg.lag_identify_scan_batch_size
                + ", maxResults=" + cfg.lag_identify_max_results
                + ", retentionMin=" + cfg.lag_identify_result_retention_minutes
                + ", autoVanishOnTp=" + cfg.lag_identify_auto_vanish_on_tp
                + ", autoStaffModeOnTp=" + cfg.lag_identify_auto_staff_mode_on_tp;
    }

    private void tickScan(MinecraftServer server) {
        if (this.activeScan == null) {
            return;
        }
        int batch = Math.max(1, this.configManager.get().lag_identify.lag_identify_scan_batch_size);
        int end = Math.min(this.activeScan.targets.size(), this.activeScan.index + batch);
        for (int i = this.activeScan.index; i < end; i++) {
            ChunkTarget target = this.activeScan.targets.get(i);
            analyzeChunk(server, target, this.activeScan);
        }
        this.activeScan.index = end;
        ServerPlayerEntity starter = server.getPlayerManager().getPlayer(this.activeScan.startedByUuid);
        if (starter != null) {
            starter.sendMessage(Text.literal("[LagIdentify] " + activeScanProgress()).formatted(Formatting.AQUA), true);
        }
        if (this.activeScan.index >= this.activeScan.targets.size()) {
            finalizeScan(server);
        }
    }

    private void analyzeChunk(MinecraftServer server, ChunkTarget target, ActiveScan scan) {
        ServerWorld world = resolveWorld(server, target.world);
        if (world == null) {
            return;
        }
        WorldChunk chunk;
        try {
            chunk = world.getChunk(target.chunkX, target.chunkZ);
        } catch (Exception ignored) {
            return;
        }
        if (chunk == null) {
            return;
        }
        Map<Long, ChunkEntityStats> worldStats = scan.entityStatsByWorldChunk.get(target.world);
        ChunkEntityStats entityStats = worldStats == null
                ? ChunkEntityStats.EMPTY
                : worldStats.getOrDefault(chunkKey(target.chunkX, target.chunkZ), ChunkEntityStats.EMPTY);
        if (entityStats.droppedItems <= 20 && entityStats.totalEntities <= 20) {
            return;
        }
        int blockEntities = 0;
        int tickingBlockEntities = 0;
        int hoppers = 0;
        int containers = 0;
        int redstone = 0;
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            blockEntities++;
            tickingBlockEntities++;
            if (blockEntity instanceof HopperBlockEntity) {
                hoppers++;
            }
            if (blockEntity instanceof Inventory && !(blockEntity instanceof HopperBlockEntity)) {
                containers++;
            }
            Block block = blockEntity.getCachedState().getBlock();
            if (isRedstoneComponent(block)) {
                redstone++;
            }
        }
        double score = score(entityStats, blockEntities, tickingBlockEntities, hoppers, redstone, containers);
        if (score <= 0.0D) {
            return;
        }
        LagIdentifyResultData result = new LagIdentifyResultData();
        result.scanSessionId = scan.scanSessionId;
        result.createdAtEpochMillis = System.currentTimeMillis();
        result.world = target.world;
        result.dimension = target.world;
        result.chunkX = target.chunkX;
        result.chunkZ = target.chunkZ;
        result.centerX = target.chunkX * 16 + 8;
        result.centerZ = target.chunkZ * 16 + 8;
        result.centerY = world.getTopYInclusive() - 8;
        result.score = score;
        result.droppedItems = entityStats.droppedItems;
        result.totalEntities = entityStats.totalEntities;
        result.mobs = entityStats.mobs;
        result.projectiles = entityStats.projectiles;
        result.villagers = entityStats.villagers;
        result.xpOrbs = entityStats.xpOrbs;
        result.blockEntities = blockEntities;
        result.tickingBlockEntities = tickingBlockEntities;
        result.hoppers = hoppers;
        result.containers = containers;
        result.redstoneComponents = redstone;
        result.category = category(result);
        result.summary = summary(result);
        scan.results.add(result);
    }

    private void finalizeScan(MinecraftServer server) {
        if (this.activeScan == null) {
            return;
        }
        ActiveScan finished = this.activeScan;
        this.activeScan = null;
        finished.results.sort(Comparator.comparingDouble((LagIdentifyResultData r) -> r.score).reversed());
        int max = Math.max(1, this.configManager.get().lag_identify.lag_identify_max_results);
        if (finished.results.size() > max) {
            finished.results = new ArrayList<>(finished.results.subList(0, max));
        }
        if (this.configManager.get().lag_identify.lag_identify_store_last_results) {
            PersistentStateData state = this.stateManager.state();
            state.lagIdentifyResults.clear();
            for (LagIdentifyResultData result : finished.results) {
                result.resultId = state.nextLagIdentifyResultId++;
                state.lagIdentifyResults.add(result);
            }
            this.stateManager.markDirty(server);
        }
        ServerPlayerEntity starter = server.getPlayerManager().getPlayer(finished.startedByUuid);
        if (starter != null) {
            starter.sendMessage(Text.literal("[LagIdentify] Scan completed. Hotspots found: " + finished.results.size()).formatted(Formatting.GREEN), false);
        }
        AuditLogger.sensitive(this.configManager, "lagidentify scan complete by " + finished.startedByName + " results=" + finished.results.size());
    }

    private List<ChunkTarget> collectTargets(MinecraftServer server, String worldFilter) {
        List<ChunkTarget> out = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        int radius = Math.max(2, server.getPlayerManager().getViewDistance() + 2);
        for (ServerWorld world : server.getWorlds()) {
            String worldId = world.getRegistryKey().getValue().toString();
            if (!worldFilter.isBlank() && !worldFilter.equals(worldId)) {
                continue;
            }
            for (ServerPlayerEntity player : world.getPlayers()) {
                int cx = player.getBlockX() >> 4;
                int cz = player.getBlockZ() >> 4;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int tx = cx + dx;
                        int tz = cz + dz;
                        String key = worldId + "|" + tx + "|" + tz;
                        if (dedupe.add(key)) {
                            out.add(new ChunkTarget(worldId, tx, tz));
                        }
                    }
                }
            }
        }
        return out;
    }

    private Map<String, Map<Long, ChunkEntityStats>> precomputeEntityStats(MinecraftServer server, String worldFilter) {
        Map<String, Map<Long, ChunkEntityStats>> byWorld = new HashMap<>();
        for (ServerWorld world : server.getWorlds()) {
            String worldId = world.getRegistryKey().getValue().toString();
            if (!worldFilter.isBlank() && !worldFilter.equals(worldId)) {
                continue;
            }
            Map<Long, ChunkEntityStats> stats = new HashMap<>();
            for (Entity entity : world.iterateEntities()) {
                int cx = entity.getBlockX() >> 4;
                int cz = entity.getBlockZ() >> 4;
                long key = chunkKey(cx, cz);
                ChunkEntityStats entry = stats.computeIfAbsent(key, ignored -> new ChunkEntityStats());
                entry.totalEntities++;
                if (entity instanceof ItemEntity) entry.droppedItems++;
                if (entity instanceof MobEntity) entry.mobs++;
                if (entity instanceof ProjectileEntity) entry.projectiles++;
                if (entity instanceof VillagerEntity) entry.villagers++;
                if (entity instanceof ExperienceOrbEntity) entry.xpOrbs++;
            }
            byWorld.put(worldId, stats);
        }
        return byWorld;
    }

    private boolean isRedstoneComponent(Block block) {
        return block instanceof RedstoneWireBlock
                || block instanceof RedstoneLampBlock
                || block instanceof AbstractRedstoneGateBlock
                || block instanceof ComparatorBlock
                || block instanceof ObserverBlock
                || block instanceof PistonBlock;
    }

    private double score(ChunkEntityStats entities, int blockEntities, int tickingBlockEntities, int hoppers, int redstone, int containers) {
        var w = this.configManager.get().lag_identify;
        return entities.droppedItems * w.lag_identify_weight_dropped_items
                + entities.totalEntities * w.lag_identify_weight_entities
                + entities.mobs * w.lag_identify_weight_mobs
                + entities.projectiles * w.lag_identify_weight_projectiles
                + blockEntities * w.lag_identify_weight_block_entities
                + tickingBlockEntities * w.lag_identify_weight_ticking_block_entities
                + hoppers * w.lag_identify_weight_hoppers
                + redstone * w.lag_identify_weight_redstone
                + entities.villagers * w.lag_identify_weight_villagers
                + entities.xpOrbs * w.lag_identify_weight_xp_orbs
                + containers * 0.20D;
    }

    private String category(LagIdentifyResultData result) {
        double item = result.droppedItems * this.configManager.get().lag_identify.lag_identify_weight_dropped_items;
        double entity = result.totalEntities * this.configManager.get().lag_identify.lag_identify_weight_entities;
        double mob = result.mobs * this.configManager.get().lag_identify.lag_identify_weight_mobs;
        double hopper = result.hoppers * this.configManager.get().lag_identify.lag_identify_weight_hoppers;
        double tile = result.blockEntities * this.configManager.get().lag_identify.lag_identify_weight_block_entities;
        double red = result.redstoneComponents * this.configManager.get().lag_identify.lag_identify_weight_redstone;
        double max = Math.max(item, Math.max(entity, Math.max(mob, Math.max(hopper, Math.max(tile, red)))));
        if (max == item) return "ITEM_OVERLOAD";
        if (max == mob) return "MOB_CLUSTER";
        if (max == hopper) return "HOPPER_CLUSTER";
        if (max == tile) return "TILE_ENTITY_CLUSTER";
        if (max == red) return "REDSTONE_CLUSTER";
        return "ENTITY_OVERLOAD";
    }

    private String summary(LagIdentifyResultData result) {
        List<String> parts = new ArrayList<>();
        if (result.droppedItems > 0) parts.add(result.droppedItems + " dropped_items");
        if (result.hoppers > 0) parts.add(result.hoppers + " hoppers");
        if (result.totalEntities > 0) parts.add(result.totalEntities + " entities");
        if (result.blockEntities > 0) parts.add(result.blockEntities + " block_entities");
        if (result.mobs > 0) parts.add(result.mobs + " mobs");
        if (result.projectiles > 0) parts.add(result.projectiles + " projectiles");
        if (parts.isEmpty()) parts.add("low signal");
        return String.join(", ", parts);
    }

    private String resolveWorldFilter(String worldIdOrMode) {
        if (worldIdOrMode == null || worldIdOrMode.isBlank()) {
            return "";
        }
        String raw = worldIdOrMode.toLowerCase(Locale.ROOT);
        if (raw.equals("loaded")) {
            return "";
        }
        return raw.contains(":") ? raw : "minecraft:" + raw;
    }

    private String resolveScanMode(String worldIdOrMode) {
        if (worldIdOrMode == null || worldIdOrMode.isBlank()) {
            return this.configManager.get().lag_identify.lag_identify_default_scan_mode;
        }
        String raw = worldIdOrMode.toLowerCase(Locale.ROOT);
        return raw.equals("loaded") ? "loaded" : "world";
    }

    private ServerWorld resolveWorld(MinecraftServer server, String worldId) {
        Identifier id = Identifier.tryParse(worldId);
        if (id == null) {
            return null;
        }
        RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
        return server.getWorld(key);
    }

    private long chunkKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private void cleanupExpiredResults(MinecraftServer server) {
        int retentionMinutes = Math.max(1, this.configManager.get().lag_identify.lag_identify_result_retention_minutes);
        long cutoff = System.currentTimeMillis() - retentionMinutes * 60_000L;
        if (this.stateManager.state().lagIdentifyResults.removeIf(result -> result.createdAtEpochMillis < cutoff)) {
            this.stateManager.markDirty(server);
        }
    }

    private static final class ChunkEntityStats {
        private static final ChunkEntityStats EMPTY = new ChunkEntityStats();
        int droppedItems;
        int totalEntities;
        int mobs;
        int projectiles;
        int villagers;
        int xpOrbs;
    }

    private static final class ActiveScan {
        long scanSessionId;
        long startedAtEpochMillis;
        UUID startedByUuid;
        String startedByName = "";
        String worldFilter = "";
        String mode = "loaded";
        List<ChunkTarget> targets = new ArrayList<>();
        int index = 0;
        List<LagIdentifyResultData> results = new ArrayList<>();
        Map<String, Map<Long, ChunkEntityStats>> entityStatsByWorldChunk = new HashMap<>();
    }

    private record ChunkTarget(String world, int chunkX, int chunkZ) {
    }

    private static final class BackLocation {
        String world = "";
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        GameMode gameMode = GameMode.SURVIVAL;
        boolean autoVanishApplied;
    }
}
