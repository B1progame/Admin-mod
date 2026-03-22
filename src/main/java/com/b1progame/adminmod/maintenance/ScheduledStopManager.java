package com.b1progame.adminmod.maintenance;

import com.b1progame.adminmod.config.ConfigManager;
import com.b1progame.adminmod.util.AuditLogger;
import com.b1progame.adminmod.util.DurationParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public final class ScheduledStopManager {
    private final ConfigManager configManager;
    private boolean active;
    private long stopAtEpochMillis;
    private long lastFiveMinuteAnnouncementSecond = -1L;
    private long lastSecondCountdownAnnouncement = -1L;
    private UUID actorUuid;
    private String actorName = "console";

    public ScheduledStopManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public synchronized boolean schedule(ServerPlayerEntity actor, MinecraftServer server, long durationMillis) {
        if (server == null || durationMillis <= 0L || this.active) {
            return false;
        }
        this.active = true;
        this.stopAtEpochMillis = System.currentTimeMillis() + durationMillis;
        this.lastFiveMinuteAnnouncementSecond = -1L;
        this.lastSecondCountdownAnnouncement = -1L;
        this.actorUuid = actor == null ? null : actor.getUuid();
        this.actorName = actor == null ? "console" : actor.getGameProfile().name();

        broadcast(server, Text.literal("[ServerStop] ").formatted(Formatting.DARK_RED)
                .append(Text.literal("Server stop scheduled in ").formatted(Formatting.RED))
                .append(Text.literal(DurationParser.formatMillis(durationMillis)).formatted(Formatting.GOLD))
                .append(Text.literal(".").formatted(Formatting.RED)));

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
        this.stopAtEpochMillis = 0L;
        this.lastFiveMinuteAnnouncementSecond = -1L;
        this.lastSecondCountdownAnnouncement = -1L;
        this.actorUuid = null;
        this.actorName = "console";
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
            return "No scheduled server stop is active.";
        }
        long remaining = Math.max(0L, this.stopAtEpochMillis - System.currentTimeMillis());
        return "Scheduled stop in " + DurationParser.formatMillis(remaining) + " (by " + this.actorName + ").";
    }

    public synchronized boolean isActive() {
        return this.active;
    }

    public synchronized void tick(MinecraftServer server) {
        if (!this.active || server == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long remainingMillis = this.stopAtEpochMillis - now;
        if (remainingMillis <= 0L) {
            this.active = false;
            this.stopAtEpochMillis = 0L;
            this.lastFiveMinuteAnnouncementSecond = -1L;
            this.lastSecondCountdownAnnouncement = -1L;
            broadcast(server, Text.literal("[ServerStop] ").formatted(Formatting.DARK_RED)
                    .append(Text.literal("Stopping server now.").formatted(Formatting.RED)));
            AuditLogger.sensitive(this.configManager, "scheduled server stop executed (by " + this.actorName + ")");
            server.stop(false);
            return;
        }

        long remainingSeconds = (remainingMillis + 999L) / 1000L;
        if (remainingSeconds <= 15L) {
            if (remainingSeconds != this.lastSecondCountdownAnnouncement) {
                this.lastSecondCountdownAnnouncement = remainingSeconds;
                broadcast(server, countdownText((int) remainingSeconds));
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
        server.sendMessage(text);
    }
}
