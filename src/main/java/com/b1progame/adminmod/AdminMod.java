package com.b1progame.adminmod;

import com.b1progame.adminmod.command.AdminCommands;
import com.b1progame.adminmod.config.ConfigManager;
import com.b1progame.adminmod.gui.AdminGuiService;
import com.b1progame.adminmod.heatmap.HeatmapManager;
import com.b1progame.adminmod.lagidentify.LagIdentifyManager;
import com.b1progame.adminmod.maintenance.MaintenanceManager;
import com.b1progame.adminmod.maintenance.ScheduledStopManager;
import com.b1progame.adminmod.mixin.ServerLoginNetworkHandlerAccessor;
import com.b1progame.adminmod.moderation.ModerationManager;
import com.b1progame.adminmod.state.StateManager;
import com.b1progame.adminmod.vanish.VanishManager;
import com.b1progame.adminmod.xrayreplay.XrayReplayManager;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AdminMod implements ModInitializer {
    public static final String MOD_ID = "adminmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static AdminMod instance;
    private ConfigManager configManager;
    private StateManager stateManager;
    private MaintenanceManager maintenanceManager;
    private ScheduledStopManager scheduledStopManager;
    private VanishManager vanishManager;
    private ModerationManager moderationManager;
    private HeatmapManager heatmapManager;
    private LagIdentifyManager lagIdentifyManager;
    private XrayReplayManager xrayReplayManager;
    private AdminGuiService guiService;
    private MinecraftServer server;

    @Override
    public void onInitialize() {
        instance = this;

        this.configManager = new ConfigManager();
        this.configManager.load();
        this.stateManager = new StateManager();
        this.maintenanceManager = new MaintenanceManager(this.configManager, this.stateManager);
        this.scheduledStopManager = new ScheduledStopManager(this.configManager);
        this.vanishManager = new VanishManager(this.configManager, this.stateManager);
        this.moderationManager = new ModerationManager(this.configManager, this.stateManager);
        this.heatmapManager = new HeatmapManager(this.configManager, this.stateManager);
        this.lagIdentifyManager = new LagIdentifyManager(this.configManager, this.stateManager);
        this.xrayReplayManager = new XrayReplayManager(this.configManager, this.stateManager);
        this.guiService = new AdminGuiService(this.configManager, this.maintenanceManager, this.vanishManager, this.moderationManager);

        AdminCommands.register(this);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            this.server = server;
            this.stateManager.load(server);
            this.maintenanceManager.onServerStarted(server);
            this.vanishManager.onServerStarted(server);
            this.moderationManager.onServerStarted(server);
            this.heatmapManager.onServerStarted(server);
            this.lagIdentifyManager.onServerStarted(server);
            this.xrayReplayManager.onServerStarted(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            this.maintenanceManager.onServerStopping(server);
            this.vanishManager.onServerStopping(server);
            this.heatmapManager.onServerStopping(server);
            this.lagIdentifyManager.onServerStopping(server);
            this.xrayReplayManager.onServerStopping(server);
            this.stateManager.save(server);
            this.server = null;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            this.maintenanceManager.handleJoin(handler.player);
            this.vanishManager.handleJoin(handler.player);
            this.moderationManager.handleJoin(handler.player);
            this.xrayReplayManager.onPlayerJoin(handler.player);
        });

        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            if (!server.getPlayerManager().isWhitelistEnabled()) {
                return;
            }
            GameProfile profile = ((ServerLoginNetworkHandlerAccessor) handler).adminmod$getProfile();
            if (profile == null) {
                return;
            }
            boolean isOperator = server.getPlayerManager().isOperator(new PlayerConfigEntry(profile));
            boolean whitelisted = server.getPlayerManager().getWhitelist().isAllowed(new PlayerConfigEntry(profile));
            if (!isOperator && !whitelisted) {
                this.moderationManager.notifyWhitelistDeniedAttempt(
                        server,
                        profile.name(),
                        profile.id() == null ? "" : profile.id().toString()
                );
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            this.vanishManager.handleDisconnect(handler.player);
            this.moderationManager.handleDisconnect(handler.player);
            this.heatmapManager.onPlayerDisconnect(handler.player);
            this.lagIdentifyManager.onPlayerDisconnect(handler.player);
            this.xrayReplayManager.onPlayerDisconnect(handler.player);
            this.guiService.playerSearchInputManager().cleanup(handler.player);
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> this.vanishManager.handleJoin(player));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            this.vanishManager.tick(server);
            this.moderationManager.tick(server);
            this.scheduledStopManager.tick(server);
            this.heatmapManager.tick(server);
            this.lagIdentifyManager.tick(server);
            this.xrayReplayManager.tick(server);
        });
    }

    public static AdminMod get() {
        return instance;
    }

    public ConfigManager configManager() {
        return this.configManager;
    }

    public StateManager stateManager() {
        return this.stateManager;
    }

    public MaintenanceManager maintenanceManager() {
        return this.maintenanceManager;
    }

    public VanishManager vanishManager() {
        return this.vanishManager;
    }

    public ScheduledStopManager scheduledStopManager() {
        return this.scheduledStopManager;
    }

    public AdminGuiService guiService() {
        return this.guiService;
    }

    public ModerationManager moderationManager() {
        return this.moderationManager;
    }

    public HeatmapManager heatmapManager() {
        return this.heatmapManager;
    }

    public LagIdentifyManager lagIdentifyManager() {
        return this.lagIdentifyManager;
    }

    public XrayReplayManager xrayReplayManager() {
        return this.xrayReplayManager;
    }

    public void reload() {
        this.configManager.load();
    }

    public MinecraftServer server() {
        return this.server;
    }
}
