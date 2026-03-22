package com.b1progame.adminmod.maintenance;

import com.b1progame.adminmod.config.ConfigManager;
import com.b1progame.adminmod.state.PersistentStateData;
import com.b1progame.adminmod.state.StateManager;
import com.b1progame.adminmod.util.AuditLogger;
import com.b1progame.adminmod.util.PermissionUtil;
import com.b1progame.adminmod.util.ServerAccess;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.Whitelist;
import net.minecraft.server.WhitelistEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MaintenanceManager {
    private final ConfigManager configManager;
    private final StateManager stateManager;

    public MaintenanceManager(ConfigManager configManager, StateManager stateManager) {
        this.configManager = configManager;
        this.stateManager = stateManager;
    }

    public boolean isEnabled() {
        return this.stateManager.state().maintenanceEnabled;
    }

    public synchronized void onServerStarted(net.minecraft.server.MinecraftServer server) {
        if (isEnabled()) {
            server.setEnforceWhitelist(true);
            ensureMaintenanceAccessEntries(server);
            kickNonAuthorizedOnlinePlayers(server);
        }
    }

    public synchronized void onServerStopping(net.minecraft.server.MinecraftServer server) {
        this.stateManager.save(server);
    }

    public synchronized boolean enable(net.minecraft.server.MinecraftServer server, ServerPlayerEntity actor) {
        PersistentStateData data = this.stateManager.state();
        if (data.maintenanceEnabled) {
            return false;
        }

        data.preMaintenanceWhitelistEnabled = server.isEnforceWhitelist();
        data.originalWhitelistMembers.clear();
        data.maintenanceTemporaryMembers.clear();
        snapshotWhitelist(server, data.originalWhitelistMembers);

        data.maintenanceEnabled = true;
        server.setEnforceWhitelist(true);
        ensureMaintenanceAccessEntries(server);
        kickNonAuthorizedOnlinePlayers(server);
        this.stateManager.markDirty(server);

        AuditLogger.sensitive(
                this.configManager,
                "Maintenance enabled by " + (actor == null ? "console" : AuditLogger.actor(actor))
        );
        return true;
    }

    public synchronized boolean disable(net.minecraft.server.MinecraftServer server, ServerPlayerEntity actor) {
        PersistentStateData data = this.stateManager.state();
        if (!data.maintenanceEnabled) {
            return false;
        }

        Whitelist whitelist = server.getPlayerManager().getWhitelist();
        for (String raw : new HashSet<>(data.maintenanceTemporaryMembers)) {
            try {
                UUID uuid = UUID.fromString(raw);
                if (!data.originalWhitelistMembers.contains(raw)) {
                    whitelist.remove(new PlayerConfigEntry(uuid, ""));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        data.maintenanceTemporaryMembers.clear();
        data.maintenanceEnabled = false;
        server.setEnforceWhitelist(data.preMaintenanceWhitelistEnabled);
        this.stateManager.markDirty(server);

        AuditLogger.sensitive(
                this.configManager,
                "Maintenance disabled by " + (actor == null ? "console" : AuditLogger.actor(actor))
        );
        return true;
    }

    public synchronized void handleJoin(ServerPlayerEntity player) {
        if (!isEnabled()) {
            return;
        }

        if (PermissionUtil.canUseAdminGui(player, this.configManager)) {
            ensureWhitelistedForMaintenance(player);
            return;
        }

        player.networkHandler.disconnect(Text.literal(this.configManager.get().maintenance_join_denied_message));
    }

    private void ensureMaintenanceAccessEntries(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            if (PermissionUtil.canUseAdminGui(online, this.configManager)) {
                ensureWhitelistedForMaintenance(online);
            }
        }
        for (UUID allowed : this.configManager.allowedAdminUuids()) {
            addTemporaryWhitelistEntry(server, new PlayerConfigEntry(allowed, ""));
        }
    }

    private void ensureWhitelistedForMaintenance(ServerPlayerEntity player) {
        if (!isEnabled()) {
            return;
        }
        addTemporaryWhitelistEntry(
                ServerAccess.server(player),
                new PlayerConfigEntry(new GameProfile(player.getUuid(), player.getGameProfile().name()))
        );
    }

    private void addTemporaryWhitelistEntry(net.minecraft.server.MinecraftServer server, PlayerConfigEntry entry) {
        if (server == null) {
            return;
        }
        Whitelist whitelist = server.getPlayerManager().getWhitelist();
        String uuid = entry.id().toString();
        if (this.stateManager.state().originalWhitelistMembers.contains(uuid)) {
            return;
        }

        whitelist.add(new WhitelistEntry(entry));
        this.stateManager.state().maintenanceTemporaryMembers.add(uuid);
        this.stateManager.markDirty(server);
    }

    private void snapshotWhitelist(net.minecraft.server.MinecraftServer server, Set<String> out) {
        out.clear();
        for (WhitelistEntry entry : server.getPlayerManager().getWhitelist().values()) {
            if (entry.getKey() != null && entry.getKey().id() != null) {
                out.add(entry.getKey().id().toString());
            }
        }
    }

    private void kickNonAuthorizedOnlinePlayers(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!PermissionUtil.canUseAdminGui(player, this.configManager)) {
                player.networkHandler.disconnect(Text.literal(this.configManager.get().maintenance_kick_message));
            }
        }
    }
}
