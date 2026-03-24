package com.b1progame.adminmod.maintenance;

import com.b1progame.adminmod.config.ConfigManager;
import com.b1progame.adminmod.util.AuditLogger;
import com.b1progame.adminmod.util.DurationParser;
import com.b1progame.adminmod.util.PermissionUtil;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public final class ScheduledStopManager {
    public enum BossBarMode {
        OFF("off"),
        SELF("self"),
        ALL("all");

        public final String id;

        BossBarMode(String id) {
            this.id = id;
        }
    }

    public enum KickPolicy {
        ALL("all"),
        NON_ADMIN("non_admin"),
        NOBODY("nobody");

        public final String id;

        KickPolicy(String id) {
            this.id = id;
        }
    }

    private final ConfigManager configManager;
    private boolean active;
    private long scheduledDurationMillis;
    private long stopAtEpochMillis;
    private long lastFiveMinuteAnnouncementSecond = -1L;
    private boolean announcedOneMinute;
    private boolean announcedThirtySeconds;
    private boolean announcedFifteenSeconds;
    private long lastSecondCountdownAnnouncement = -1L;
    private boolean forcedDisconnectAtFiveSeconds;
    private boolean forcedDisconnectAtOneSecond;
    private UUID actorUuid;
    private String actorName = "console";
    private BossBarMode bossBarMode = BossBarMode.SELF;
    private KickPolicy kickPolicy = KickPolicy.NON_ADMIN;
    private UUID bossBarSelfViewerUuid;
    private final ServerBossBar bossBar = new ServerBossBar(
            Text.literal("Server stop countdown"),
            BossBar.Color.RED,
            BossBar.Style.PROGRESS
    );

    public ScheduledStopManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public synchronized boolean schedule(ServerPlayerEntity actor, MinecraftServer server, long durationMillis) {
        if (server == null || durationMillis <= 0L || this.active) {
            return false;
        }
        this.active = true;
        this.scheduledDurationMillis = durationMillis;
        this.stopAtEpochMillis = System.currentTimeMillis() + durationMillis;
        long initialSeconds = (durationMillis + 999L) / 1000L;
        this.lastFiveMinuteAnnouncementSecond = initialSeconds % 300L == 0L ? initialSeconds : -1L;
        this.announcedOneMinute = false;
        this.announcedThirtySeconds = false;
        this.announcedFifteenSeconds = false;
        this.lastSecondCountdownAnnouncement = -1L;
        this.forcedDisconnectAtFiveSeconds = false;
        this.forcedDisconnectAtOneSecond = false;
        this.actorUuid = actor == null ? null : actor.getUuid();
        this.actorName = actor == null ? "console" : actor.getGameProfile().name();
        if (this.bossBarMode == BossBarMode.SELF && actor != null) {
            this.bossBarSelfViewerUuid = actor.getUuid();
        }

        broadcast(server, Text.literal("[ServerStop] ").formatted(Formatting.DARK_RED)
                .append(Text.literal("Server stop scheduled in ").formatted(Formatting.RED))
                .append(Text.literal(DurationParser.formatMillis(durationMillis)).formatted(Formatting.GOLD))
                .append(Text.literal(".").formatted(Formatting.RED)));
        updateBossBar(server, durationMillis);

        AuditLogger.sensitive(
                this.configManager,
                (actor == null ? "console" : AuditLogger.actor(actor)) + " scheduled server stop in " + DurationParser.formatMillis(durationMillis)
        );
        return true;
    }

    public synchronized boolean cancel(ServerPlayerEntity actor, MinecraftServer server) {
        if (!this.active) {
            return false;
        }
        this.active = false;
        this.scheduledDurationMillis = 0L;
        this.stopAtEpochMillis = 0L;
        this.lastFiveMinuteAnnouncementSecond = -1L;
        this.announcedOneMinute = false;
        this.announcedThirtySeconds = false;
        this.announcedFifteenSeconds = false;
        this.lastSecondCountdownAnnouncement = -1L;
        this.forcedDisconnectAtFiveSeconds = false;
        this.forcedDisconnectAtOneSecond = false;
        this.actorUuid = null;
        this.actorName = "console";
        clearBossBar(server);
        if (server != null) {
            broadcast(server, Text.literal("[ServerStop] ").formatted(Formatting.DARK_RED)
                    .append(Text.literal("Scheduled server stop canceled.").formatted(Formatting.GOLD)));
        }
        AuditLogger.sensitive(
                this.configManager,
                (actor == null ? "console" : AuditLogger.actor(actor)) + " canceled scheduled server stop"
        );
        return true;
    }

    public synchronized String statusText() {
        if (!this.active) {
            return "No scheduled server stop is active. Bossbar mode: " + this.bossBarMode.id + ". Kick policy: " + this.kickPolicy.id + ".";
        }
        long remaining = Math.max(0L, this.stopAtEpochMillis - System.currentTimeMillis());
        return "Scheduled stop in " + DurationParser.formatMillis(remaining) + " (by " + this.actorName + "). Bossbar mode: " + this.bossBarMode.id + ". Kick policy: " + this.kickPolicy.id + ".";
    }

    public synchronized boolean isActive() {
        return this.active;
    }

    public synchronized void setBossBarMode(ServerPlayerEntity actor, MinecraftServer server, BossBarMode mode) {
        if (mode == null) {
            return;
        }
        this.bossBarMode = mode;
        if (mode == BossBarMode.SELF && actor != null) {
            this.bossBarSelfViewerUuid = actor.getUuid();
        }
        if (mode == BossBarMode.OFF) {
            clearBossBar(server);
        } else if (this.active) {
            long remaining = Math.max(0L, this.stopAtEpochMillis - System.currentTimeMillis());
            updateBossBar(server, remaining);
        }
        AuditLogger.sensitive(
                this.configManager,
                (actor == null ? "console" : AuditLogger.actor(actor)) + " set sstop bossbar mode to " + mode.id
        );
    }

    public synchronized void setKickPolicy(ServerPlayerEntity actor, KickPolicy policy) {
        if (policy == null) {
            return;
        }
        this.kickPolicy = policy;
        AuditLogger.sensitive(
                this.configManager,
                (actor == null ? "console" : AuditLogger.actor(actor)) + " set sstop kick policy to " + policy.id
        );
    }

    public synchronized void tick(MinecraftServer server) {
        if (!this.active || server == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long remainingMillis = this.stopAtEpochMillis - now;
        if (remainingMillis <= 0L) {
            if (!this.forcedDisconnectAtOneSecond) {
                this.forcedDisconnectAtOneSecond = true;
                forceDisconnectAllAtFinalSecond(server);
            }
            this.active = false;
            this.scheduledDurationMillis = 0L;
            this.stopAtEpochMillis = 0L;
            this.lastFiveMinuteAnnouncementSecond = -1L;
            this.announcedOneMinute = false;
            this.announcedThirtySeconds = false;
            this.announcedFifteenSeconds = false;
            this.lastSecondCountdownAnnouncement = -1L;
            this.forcedDisconnectAtFiveSeconds = false;
            this.forcedDisconnectAtOneSecond = false;
            clearBossBar(server);
            broadcast(server, Text.literal("[ServerStop] ").formatted(Formatting.DARK_RED)
                    .append(Text.literal("Stopping server now.").formatted(Formatting.RED)));
            AuditLogger.sensitive(this.configManager, "scheduled server stop executed (by " + this.actorName + ")");
            server.stop(false);
            return;
        }
        updateBossBar(server, remainingMillis);

        long remainingSeconds = (remainingMillis + 999L) / 1000L;
        if (!this.announcedOneMinute && remainingSeconds <= 60L && remainingSeconds > 30L) {
            this.announcedOneMinute = true;
            broadcast(server, Text.literal("[ServerStop] ").formatted(Formatting.DARK_RED)
                    .append(Text.literal("Server will stop in ").formatted(Formatting.RED))
                    .append(Text.literal("1m").formatted(Formatting.GOLD))
                    .append(Text.literal(".").formatted(Formatting.RED)));
        }
        if (!this.announcedThirtySeconds && remainingSeconds <= 30L && remainingSeconds > 15L) {
            this.announcedThirtySeconds = true;
            broadcast(server, Text.literal("[ServerStop] ").formatted(Formatting.DARK_RED)
                    .append(Text.literal("Server will stop in ").formatted(Formatting.RED))
                    .append(Text.literal("30s").formatted(Formatting.GOLD))
                    .append(Text.literal(".").formatted(Formatting.RED)));
        }
        if (!this.announcedFifteenSeconds && remainingSeconds <= 15L && remainingSeconds > 10L) {
            this.announcedFifteenSeconds = true;
            broadcast(server, countdownText(15));
        }
        if (remainingSeconds <= 10L) {
            if (remainingSeconds != this.lastSecondCountdownAnnouncement) {
                this.lastSecondCountdownAnnouncement = remainingSeconds;
                broadcast(server, countdownText((int) remainingSeconds));
            }
            if (remainingSeconds <= 5L && !this.forcedDisconnectAtFiveSeconds) {
                this.forcedDisconnectAtFiveSeconds = true;
                forceCloseScreensAndDisconnectAll(server);
            }
            if (remainingSeconds <= 1L && !this.forcedDisconnectAtOneSecond) {
                this.forcedDisconnectAtOneSecond = true;
                forceDisconnectAllAtFinalSecond(server);
            }
            return;
        }

        if (remainingSeconds % 300L == 0L && remainingSeconds != this.lastFiveMinuteAnnouncementSecond) {
            this.lastFiveMinuteAnnouncementSecond = remainingSeconds;
            broadcast(server, fiveMinuteText(remainingMillis));
        }
    }

    private MutableText fiveMinuteText(long remainingMillis) {
        return Text.literal("[ServerStop] ").formatted(Formatting.DARK_RED)
                .append(Text.literal("Server will stop in ").formatted(Formatting.RED))
                .append(Text.literal(DurationParser.formatMillis(remainingMillis)).formatted(Formatting.GOLD))
                .append(Text.literal(".").formatted(Formatting.RED));
    }

    private MutableText countdownText(int seconds) {
        return Text.literal("[ServerStop] ").formatted(Formatting.DARK_RED)
                .append(Text.literal("Stopping in ").formatted(Formatting.RED))
                .append(Text.literal(seconds + "s").formatted(Formatting.GOLD))
                .append(Text.literal(".").formatted(Formatting.RED));
    }

    private void broadcast(MinecraftServer server, Text text) {
        server.getPlayerManager().getPlayerList().forEach(player -> player.sendMessage(text, false));
    }

    private void updateBossBar(MinecraftServer server, long remainingMillis) {
        if (server == null || this.bossBarMode == BossBarMode.OFF || !this.active) {
            return;
        }
        float percent;
        if (this.scheduledDurationMillis <= 0L) {
            percent = 0.0F;
        } else {
            percent = (float) Math.max(0.0D, Math.min(1.0D, (double) remainingMillis / (double) this.scheduledDurationMillis));
        }
        this.bossBar.setPercent(percent);
        this.bossBar.setName(
                Text.literal("Server stop in ").formatted(Formatting.RED)
                        .append(Text.literal(DurationParser.formatMillis(remainingMillis)).formatted(Formatting.GOLD))
        );
        syncBossBarAudience(server);
    }

    private void syncBossBarAudience(MinecraftServer server) {
        this.bossBar.clearPlayers();
        if (this.bossBarMode == BossBarMode.ALL) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                this.bossBar.addPlayer(player);
            }
            return;
        }
        if (this.bossBarMode == BossBarMode.SELF && this.bossBarSelfViewerUuid != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(this.bossBarSelfViewerUuid);
            if (player != null) {
                this.bossBar.addPlayer(player);
            }
        }
    }

    private void clearBossBar(MinecraftServer server) {
        this.bossBar.clearPlayers();
    }

    private void forceCloseScreensAndDisconnectAll(MinecraftServer server) {
        for (ServerPlayerEntity player : java.util.List.copyOf(server.getPlayerManager().getPlayerList())) {
            if (this.kickPolicy == KickPolicy.NOBODY) {
                continue;
            }
            if (this.kickPolicy == KickPolicy.NON_ADMIN && PermissionUtil.canUseAdminGui(player, this.configManager)) {
                continue;
            }
            if (player.currentScreenHandler != player.playerScreenHandler) {
                player.closeHandledScreen();
            }
            player.networkHandler.disconnect(
                    Text.literal("[ServerStop] ").formatted(Formatting.DARK_RED)
                            .append(Text.literal("Server restart in progress. You were disconnected to save inventory safely.").formatted(Formatting.RED))
            );
        }
        AuditLogger.sensitive(this.configManager, "scheduled server stop pre-shutdown disconnect executed at 5s (kick policy: " + this.kickPolicy.id + ")");
    }

    private void forceDisconnectAllAtFinalSecond(MinecraftServer server) {
        for (ServerPlayerEntity player : java.util.List.copyOf(server.getPlayerManager().getPlayerList())) {
            if (player.currentScreenHandler != player.playerScreenHandler) {
                player.closeHandledScreen();
            }
            player.networkHandler.disconnect(
                    Text.literal("[ServerStop] ").formatted(Formatting.DARK_RED)
                            .append(Text.literal("Server stopping now.").formatted(Formatting.RED))
            );
        }
        AuditLogger.sensitive(this.configManager, "scheduled server stop final disconnect executed at 1s (all players)");
    }
}
