package com.b1progame.adminmod.heatmap;

import com.b1progame.adminmod.AdminMod;
import com.b1progame.adminmod.config.ConfigManager;
import com.b1progame.adminmod.state.HeatmapCellData;
import com.b1progame.adminmod.state.PersistentStateData;
import com.b1progame.adminmod.state.PlayerHistoryData;
import com.b1progame.adminmod.state.StateManager;
import com.b1progame.adminmod.util.AuditLogger;
import com.b1progame.adminmod.util.DurationParser;
import com.b1progame.adminmod.util.PermissionUtil;
import com.b1progame.adminmod.util.ServerAccess;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HeatmapManager {
    private static final boolean FEATURE_ENABLED = false;

    public enum HeatmapMode {
        MOVEMENT("movement"),
        MINING("mining"),
        ORE("ore"),
        SUSPICIOUS("suspicious");

        public final String id;

        HeatmapMode(String id) {
            this.id = id;
        }

        public static HeatmapMode parse(String raw) {
            if (raw == null) return null;
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "movement", "player" -> MOVEMENT;
                case "mining" -> MINING;
                case "ore" -> ORE;
                case "suspicious" -> SUSPICIOUS;
                default -> null;
            };
        }
    }

    private final ConfigManager configManager;
    private final StateManager stateManager;
    private final Map<CellKey, HeatmapCellData> cellIndex = new HashMap<>();
    private final Map<UUID, Long> lastMovementSampleTickByPlayer = new HashMap<>();
    private final Map<UUID, HeatmapRenderSession> renderSessions = new HashMap<>();
    private long cleanupTicker = 0L;
    private long dirtyEvents = 0L;

    public HeatmapManager(ConfigManager configManager, StateManager stateManager) {
        this.configManager = configManager;
        this.stateManager = stateManager;
    }

    public boolean isFeatureEnabled() {
        return FEATURE_ENABLED;
    }

    public synchronized void onServerStarted(MinecraftServer server) {
        if (!isFeatureEnabled()) {
            return;
        }
        rebuildIndexFromState();
        cleanupExpired(server);
    }

    public synchronized void onServerStopping(MinecraftServer server) {
        if (!isFeatureEnabled()) {
            return;
        }
        for (UUID admin : new ArrayList<>(this.renderSessions.keySet())) {
            stop(admin);
        }
    }

    public synchronized void onPlayerDisconnect(ServerPlayerEntity player) {
        if (!isFeatureEnabled()) {
            return;
        }
        this.renderSessions.remove(player.getUuid());
        this.lastMovementSampleTickByPlayer.remove(player.getUuid());
    }

    public synchronized void tick(MinecraftServer server) {
        if (!isFeatureEnabled() || !this.configManager.get().heatmap.heatmap_enabled) {
            return;
        }
        trackMovement(server);
        tickRenderSessions(server);
        if (this.dirtyEvents > 0 && server.getTicks() % 200L == 0L) {
            this.stateManager.markDirty(server);
            this.dirtyEvents = 0L;
        }
        this.cleanupTicker++;
        if (this.cleanupTicker >= 1200L) {
            this.cleanupTicker = 0L;
            cleanupExpired(server);
        }
    }

    public synchronized void recordMiningBreak(ServerPlayerEntity player, BlockPos pos) {
        if (!isFeatureEnabled() || !this.configManager.get().heatmap.heatmap_enabled || !this.configManager.get().heatmap.heatmap_track_mining) {
            return;
        }
        if (skipByWatchlistConfig(player.getUuid())) {
            return;
        }
        recordEvent(player, HeatmapMode.MINING, pos, 1.0D, System.currentTimeMillis());
    }

    public synchronized void recordTrackedOre(ServerPlayerEntity player, BlockPos pos, boolean suspicious) {
        if (!isFeatureEnabled() || !this.configManager.get().heatmap.heatmap_enabled || !this.configManager.get().heatmap.heatmap_track_ore) {
            return;
        }
        if (skipByWatchlistConfig(player.getUuid())) {
            return;
        }
        long now = System.currentTimeMillis();
        recordEvent(player, HeatmapMode.ORE, pos, suspicious ? 2.0D : 1.5D, now);
        if (suspicious) {
            recordEvent(player, HeatmapMode.SUSPICIOUS, pos, 3.0D, now);
        }
    }

    public synchronized boolean startPlayerView(ServerPlayerEntity admin, UUID targetUuid, HeatmapMode mode, long durationMillis) {
        if (!isFeatureEnabled()) {
            return false;
        }
        if (!validateAdmin(admin) || mode == null || targetUuid == null) {
            return false;
        }
        HeatmapRenderSession session = new HeatmapRenderSession();
        session.adminUuid = admin.getUuid();
        session.mode = mode;
        session.playerUuidFilter = targetUuid.toString();
        session.durationMillis = Math.max(30_000L, durationMillis);
        session.radius = Math.max(16, this.configManager.get().heatmap.heatmap_visualization_radius);
        session.worldFilter = resolvePlayerWorld(targetUuid);
        session.watchlistOnly = false;
        session.active = true;
        session.lastRenderTick = 0L;
        this.renderSessions.put(admin.getUuid(), session);
        AuditLogger.sensitive(this.configManager, AuditLogger.actor(admin) + " heatmap start mode=" + mode.id + " player=" + targetUuid + " duration=" + session.durationMillis);
        return true;
    }

    public synchronized boolean startGlobalView(ServerPlayerEntity admin, String worldId, HeatmapMode mode, long durationMillis) {
        if (!isFeatureEnabled()) {
            return false;
        }
        if (!validateAdmin(admin) || mode == null) {
            return false;
        }
        HeatmapRenderSession session = new HeatmapRenderSession();
        session.adminUuid = admin.getUuid();
        session.mode = mode;
        session.playerUuidFilter = "";
        session.durationMillis = Math.max(30_000L, durationMillis);
        session.radius = Math.max(16, this.configManager.get().heatmap.heatmap_visualization_radius);
        session.worldFilter = worldId == null ? "" : worldId;
        session.watchlistOnly = false;
        session.active = true;
        session.lastRenderTick = 0L;
        this.renderSessions.put(admin.getUuid(), session);
        AuditLogger.sensitive(this.configManager, AuditLogger.actor(admin) + " heatmap start mode=" + mode.id + " global world=" + session.worldFilter + " duration=" + session.durationMillis);
        return true;
    }

    public synchronized boolean startWatchlistView(ServerPlayerEntity admin, HeatmapMode mode, long durationMillis) {
        if (!isFeatureEnabled()) {
            return false;
        }
        if (!validateAdmin(admin) || mode == null) {
            return false;
        }
        HeatmapRenderSession session = new HeatmapRenderSession();
        session.adminUuid = admin.getUuid();
        session.mode = mode;
        session.playerUuidFilter = "";
        session.durationMillis = Math.max(30_000L, durationMillis);
        session.radius = Math.max(16, this.configManager.get().heatmap.heatmap_visualization_radius);
        session.worldFilter = "";
        session.watchlistOnly = true;
        session.active = true;
        session.lastRenderTick = 0L;
        this.renderSessions.put(admin.getUuid(), session);
        AuditLogger.sensitive(this.configManager, AuditLogger.actor(admin) + " heatmap start watchlist mode=" + mode.id + " duration=" + session.durationMillis);
        return true;
    }

    public synchronized boolean stop(ServerPlayerEntity admin) {
        if (!isFeatureEnabled()) {
            return false;
        }
        return admin != null && stop(admin.getUuid());
    }

    public synchronized boolean stop(UUID adminUuid) {
        if (!isFeatureEnabled()) {
            return false;
        }
        HeatmapRenderSession removed = this.renderSessions.remove(adminUuid);
        if (removed == null) {
            return false;
        }
        ServerPlayerEntity admin = AdminMod.get() == null || AdminMod.get().server() == null ? null : AdminMod.get().server().getPlayerManager().getPlayer(adminUuid);
        if (admin != null) {
            admin.sendMessage(Text.literal("[Heatmap] Review stopped.").formatted(Formatting.YELLOW), false);
            AuditLogger.sensitive(this.configManager, AuditLogger.actor(admin) + " heatmap stop");
        }
        return true;
    }

    public synchronized boolean setRadius(ServerPlayerEntity admin, int radius) {
        if (!isFeatureEnabled()) {
            return false;
        }
        if (admin == null || radius < 8) {
            return false;
        }
        HeatmapRenderSession session = this.renderSessions.get(admin.getUuid());
        if (session == null) {
            return false;
        }
        session.radius = Math.max(8, Math.min(512, radius));
        return true;
    }

    public synchronized boolean setMode(ServerPlayerEntity admin, HeatmapMode mode) {
        if (!isFeatureEnabled()) {
            return false;
        }
        if (admin == null || mode == null) {
            return false;
        }
        HeatmapRenderSession session = this.renderSessions.get(admin.getUuid());
        if (session == null) {
            return false;
        }
        session.mode = mode;
        return true;
    }

    public synchronized List<String> describeTopHotspots(HeatmapMode mode, UUID playerFilter, boolean watchlistOnly, String worldFilter, long durationMillis, int maxEntries) {
        if (!isFeatureEnabled()) {
            return List.of();
        }
        long cutoff = System.currentTimeMillis() - Math.max(30_000L, durationMillis);
        List<HeatmapCellData> filtered = queryCells(mode, playerFilter == null ? "" : playerFilter.toString(), watchlistOnly, worldFilter, cutoff);
        filtered.sort(Comparator.comparingDouble((HeatmapCellData c) -> c.score).reversed().thenComparingLong(c -> c.lastSeenEpochMillis).reversed());
        List<String> out = new ArrayList<>();
        int limit = Math.max(1, Math.min(50, maxEntries));
        for (int i = 0; i < filtered.size() && i < limit; i++) {
            HeatmapCellData c = filtered.get(i);
            int cellSize = Math.max(1, this.configManager.get().heatmap.heatmap_cell_size);
            int x = c.cellX * cellSize + (cellSize / 2);
            int y = c.cellY * cellSize + (cellSize / 2);
            int z = c.cellZ * cellSize + (cellSize / 2);
            out.add(String.format(Locale.ROOT,
                    "#%d %s [%s] (%d,%d,%d) count=%d score=%.2f last=%s",
                    i + 1, c.world, c.type, x, y, z, c.count, c.score, DurationParser.formatMillis(Math.max(0L, System.currentTimeMillis() - c.lastSeenEpochMillis)) + " ago"));
        }
        return out;
    }

    private boolean validateAdmin(ServerPlayerEntity admin) {
        return admin != null && PermissionUtil.canUseAdminGui(admin, this.configManager);
    }

    private boolean skipByWatchlistConfig(UUID uuid) {
        return this.configManager.get().heatmap.heatmap_watchlist_only_mode
                && !this.stateManager.state().watchlistEntries.containsKey(uuid.toString());
    }

    private void trackMovement(MinecraftServer server) {
        if (!this.configManager.get().heatmap.heatmap_track_movement) {
            return;
        }
        long tick = server.getTicks();
        int interval = Math.max(1, this.configManager.get().heatmap.heatmap_movement_sample_interval_ticks);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (skipByWatchlistConfig(player.getUuid())) {
                continue;
            }
            Long last = this.lastMovementSampleTickByPlayer.get(player.getUuid());
            if (last != null && tick - last < interval) {
                continue;
            }
            this.lastMovementSampleTickByPlayer.put(player.getUuid(), tick);
            BlockPos pos = player.getBlockPos();
            recordEvent(player, HeatmapMode.MOVEMENT, pos, 1.0D, System.currentTimeMillis());
        }
    }

    private void tickRenderSessions(MinecraftServer server) {
        int interval = Math.max(1, this.configManager.get().heatmap.heatmap_update_interval_ticks);
        long tick = server.getTicks();
        for (HeatmapRenderSession session : this.renderSessions.values()) {
            if (!session.active || tick - session.lastRenderTick < interval) {
                continue;
            }
            session.lastRenderTick = tick;
            ServerPlayerEntity admin = server.getPlayerManager().getPlayer(session.adminUuid);
            if (admin == null) {
                continue;
            }
            renderForAdmin(admin, session);
        }
    }

    private void renderForAdmin(ServerPlayerEntity admin, HeatmapRenderSession session) {
        long cutoff = System.currentTimeMillis() - Math.max(30_000L, session.durationMillis);
        List<HeatmapCellData> cells = queryCells(session.mode, session.playerUuidFilter, session.watchlistOnly, session.worldFilter, cutoff);
        if (cells.isEmpty()) {
            admin.sendMessage(Text.literal("[Heatmap] No data in selected window.").formatted(Formatting.GRAY), true);
            return;
        }
        ServerWorld world = resolveAdminRenderWorld(admin, session);
        if (world == null) {
            admin.sendMessage(Text.literal("[Heatmap] World not available.").formatted(Formatting.RED), true);
            return;
        }
        Vec3d center = resolveRenderCenter(admin, session);
        int cellSize = Math.max(1, this.configManager.get().heatmap.heatmap_cell_size);
        int maxCells = Math.max(8, this.configManager.get().heatmap.heatmap_max_rendered_cells);
        double radiusSq = (double) session.radius * (double) session.radius;
        List<HeatmapCellData> inRange = new ArrayList<>();
        for (HeatmapCellData cell : cells) {
            if (!world.getRegistryKey().getValue().toString().equals(cell.world)) {
                continue;
            }
            double x = cell.cellX * cellSize + (cellSize / 2.0D);
            double y = cell.cellY * cellSize + 0.2D;
            double z = cell.cellZ * cellSize + (cellSize / 2.0D);
            if (center.squaredDistanceTo(x, y, z) <= radiusSq) {
                inRange.add(cell);
            }
        }
        inRange.sort(Comparator.comparingDouble((HeatmapCellData c) -> c.score).reversed());
        if (inRange.size() > maxCells) {
            inRange = new ArrayList<>(inRange.subList(0, maxCells));
        }
        double maxScore = 1.0D;
        for (HeatmapCellData c : inRange) {
            maxScore = Math.max(maxScore, c.score);
        }
        for (HeatmapCellData c : inRange) {
            double x = c.cellX * cellSize + (cellSize / 2.0D);
            double y = c.cellY * cellSize + 0.2D;
            double z = c.cellZ * cellSize + (cellSize / 2.0D);
            double normalized = Math.max(0.0D, Math.min(1.0D, c.score / maxScore));
            DustParticleEffect effect = colorFor(normalized);
            int amount = 2 + (int) Math.round(normalized * 8.0D);
            world.spawnParticles(admin, effect, true, true, x, y, z, amount, 0.35D, 0.15D, 0.35D, 0.001D);
            if (normalized > 0.75D) {
                world.spawnParticles(admin, effect, true, true, x, y + 1.0D, z, 2, 0.08D, 0.08D, 0.08D, 0.001D);
            }
        }
        String playerLabel = session.playerUuidFilter.isBlank() ? "all" : session.playerUuidFilter;
        admin.sendMessage(Text.literal("[Heatmap] mode=" + session.mode.id
                + " duration=" + DurationParser.formatMillis(session.durationMillis)
                + " radius=" + session.radius
                + " player=" + playerLabel
                + " cells=" + inRange.size()).formatted(Formatting.AQUA), true);
    }

    private DustParticleEffect colorFor(double normalized) {
        int r;
        int g;
        int b = 20;
        if (normalized < 0.33D) {
            r = 56;
            g = 242;
        } else if (normalized < 0.66D) {
            r = 242;
            g = 235;
        } else if (normalized < 0.85D) {
            r = 250;
            g = 140;
        } else {
            r = 242;
            g = 46;
        }
        int color = (r << 16) | (g << 8) | b;
        return new DustParticleEffect(color, 1.25F);
    }

    private ServerWorld resolveAdminRenderWorld(ServerPlayerEntity admin, HeatmapRenderSession session) {
        if (session.worldFilter == null || session.worldFilter.isBlank()) {
            return (ServerWorld) admin.getEntityWorld();
        }
        Identifier id = Identifier.tryParse(session.worldFilter);
        if (id == null) {
            return (ServerWorld) admin.getEntityWorld();
        }
        RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
        return ServerAccess.server(admin).getWorld(key);
    }

    private Vec3d resolveRenderCenter(ServerPlayerEntity admin, HeatmapRenderSession session) {
        if (session.playerUuidFilter == null || session.playerUuidFilter.isBlank()) {
            return new Vec3d(admin.getX(), admin.getY(), admin.getZ());
        }
        try {
            UUID target = UUID.fromString(session.playerUuidFilter);
            ServerPlayerEntity online = ServerAccess.server(admin).getPlayerManager().getPlayer(target);
            if (online != null) {
                return new Vec3d(online.getX(), online.getY(), online.getZ());
            }
            PlayerHistoryData history = this.stateManager.state().playerHistory.get(target.toString());
            if (history != null) {
                return new Vec3d(history.lastX, history.lastY, history.lastZ);
            }
        } catch (IllegalArgumentException ignored) {
        }
        return new Vec3d(admin.getX(), admin.getY(), admin.getZ());
    }

    private List<HeatmapCellData> queryCells(HeatmapMode mode, String playerFilter, boolean watchlistOnly, String worldFilter, long cutoffEpochMillis) {
        Set<String> watchlist = watchlistOnly ? new HashSet<>(this.stateManager.state().watchlistEntries.keySet()) : Set.of();
        List<HeatmapCellData> out = new ArrayList<>();
        for (HeatmapCellData cell : this.cellIndex.values()) {
            if (!cell.type.equals(mode.id)) {
                continue;
            }
            if (cell.lastSeenEpochMillis < cutoffEpochMillis) {
                continue;
            }
            if (worldFilter != null && !worldFilter.isBlank() && !worldFilter.equals(cell.world)) {
                continue;
            }
            if (playerFilter != null && !playerFilter.isBlank()) {
                if (!playerFilter.equals(cell.playerUuid)) {
                    continue;
                }
            } else if (cell.playerUuid != null && !cell.playerUuid.isBlank()) {
                continue;
            }
            if (watchlistOnly) {
                if (cell.playerUuid == null || cell.playerUuid.isBlank() || !watchlist.contains(cell.playerUuid)) {
                    continue;
                }
            }
            out.add(cell);
        }
        return out;
    }

    private void recordEvent(ServerPlayerEntity player, HeatmapMode mode, BlockPos pos, double weight, long now) {
        String world = player.getEntityWorld().getRegistryKey().getValue().toString();
        String dimension = world;
        String playerUuid = player.getUuidAsString();
        int cellSize = Math.max(1, this.configManager.get().heatmap.heatmap_cell_size);
        int cx = floorDiv(pos.getX(), cellSize);
        int cy = floorDiv(pos.getY(), cellSize);
        int cz = floorDiv(pos.getZ(), cellSize);
        addToCell(world, dimension, playerUuid, mode.id, cx, cy, cz, weight, now);
        addToCell(world, dimension, "", mode.id, cx, cy, cz, weight, now);
    }

    private int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a)) {
            r--;
        }
        return r;
    }

    private void addToCell(String world, String dimension, String playerUuid, String type, int cx, int cy, int cz, double weight, long now) {
        CellKey key = new CellKey(world, playerUuid, type, cx, cy, cz);
        HeatmapCellData cell = this.cellIndex.get(key);
        if (cell == null) {
            cell = new HeatmapCellData();
            cell.world = world;
            cell.dimension = dimension;
            cell.playerUuid = playerUuid;
            cell.type = type;
            cell.cellX = cx;
            cell.cellY = cy;
            cell.cellZ = cz;
            cell.count = 0L;
            cell.score = 0.0D;
            cell.firstSeenEpochMillis = now;
            this.cellIndex.put(key, cell);
            this.stateManager.state().heatmapCells.add(cell);
        }
        cell.count++;
        cell.score += Math.max(0.1D, weight);
        cell.lastSeenEpochMillis = now;
        this.dirtyEvents++;
    }

    private String resolvePlayerWorld(UUID targetUuid) {
        PlayerHistoryData history = this.stateManager.state().playerHistory.get(targetUuid.toString());
        return history == null ? "" : history.lastWorld;
    }

    private void cleanupExpired(MinecraftServer server) {
        long retention = Math.max(1, this.configManager.get().heatmap.heatmap_retention_days) * 86_400_000L;
        long cutoff = System.currentTimeMillis() - retention;
        this.stateManager.state().heatmapCells.removeIf(cell -> cell == null || cell.lastSeenEpochMillis < cutoff);
        rebuildIndexFromState();
        this.stateManager.markDirty(server);
    }

    private void rebuildIndexFromState() {
        this.cellIndex.clear();
        List<HeatmapCellData> cells = this.stateManager.state().heatmapCells;
        List<HeatmapCellData> normalized = new ArrayList<>();
        for (HeatmapCellData cell : cells) {
            if (cell == null || cell.world == null || cell.type == null) {
                continue;
            }
            if (cell.dimension == null) {
                cell.dimension = cell.world;
            }
            if (cell.playerUuid == null) {
                cell.playerUuid = "";
            }
            CellKey key = new CellKey(cell.world, cell.playerUuid, cell.type, cell.cellX, cell.cellY, cell.cellZ);
            HeatmapCellData existing = this.cellIndex.get(key);
            if (existing == null) {
                this.cellIndex.put(key, cell);
                normalized.add(cell);
            } else {
                existing.count += Math.max(0L, cell.count);
                existing.score += Math.max(0.0D, cell.score);
                existing.firstSeenEpochMillis = existing.firstSeenEpochMillis == 0L ? cell.firstSeenEpochMillis
                        : Math.min(existing.firstSeenEpochMillis, cell.firstSeenEpochMillis);
                existing.lastSeenEpochMillis = Math.max(existing.lastSeenEpochMillis, cell.lastSeenEpochMillis);
            }
        }
        this.stateManager.state().heatmapCells = normalized;
    }

    private record CellKey(String world, String playerUuid, String type, int x, int y, int z) {
    }

    private static final class HeatmapRenderSession {
        public UUID adminUuid;
        public HeatmapMode mode = HeatmapMode.MOVEMENT;
        public String playerUuidFilter = "";
        public String worldFilter = "";
        public long durationMillis = 3_600_000L;
        public int radius = 96;
        public boolean watchlistOnly;
        public boolean active;
        public long lastRenderTick;
    }
}
