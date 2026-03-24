package com.b1progame.adminmod.vanish;

import com.b1progame.adminmod.AdminMod;
import com.b1progame.adminmod.config.ConfigManager;
import com.b1progame.adminmod.state.PersistentStateData;
import com.b1progame.adminmod.state.StateManager;
import com.b1progame.adminmod.util.AuditLogger;
import com.b1progame.adminmod.util.PermissionUtil;
import com.b1progame.adminmod.util.ServerAccess;
import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ChunkLoadDistanceS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SimulationDistanceS2CPacket;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.*;
import java.util.stream.Collectors;

public final class VanishManager {
    private static final int ACTIONBAR_PERIOD_TICKS = 40;
    private static final int RUNTIME_RECONCILE_PERIOD_TICKS = 20;
    private static final int JOIN_REFRESH_WINDOW_TICKS = 100;
    private static final int VANISH_LOW_DISTANCE = 2;
    private static final int MIN_VANISH_FLY_SPEED_LEVEL = 1;
    private static final int MAX_VANISH_FLY_SPEED_LEVEL = 10;
    private static final float BASE_FLY_SPEED = 0.05F;

    private final ConfigManager configManager;
    private final StateManager stateManager;
    private final Map<UUID, GameMode> noClipPreviousGameModes = new HashMap<>();
    private final Map<UUID, ViewDistanceState> lastAppliedViewDistances = new HashMap<>();
    private final Map<UUID, Integer> pendingJoinRefreshTicks = new HashMap<>();
    private int actionbarTicker = 0;
    private int runtimeReconcileTicker = 0;

    public VanishManager(ConfigManager configManager, StateManager stateManager) {
        this.configManager = configManager;
        this.stateManager = stateManager;
    }

    public synchronized void onServerStarted(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (isVanished(player.getUuid())) {
                applyVanish(player, false);
            }
        }
        refreshAllVisibility(server);
    }

    public synchronized void onServerStopping(MinecraftServer server) {
        this.stateManager.save(server);
    }

    public synchronized void refreshVanishRuntime(MinecraftServer server) {
        if (server == null) {
            return;
        }
        reconcileRuntime(server, true);
    }

    public synchronized boolean isVanished(UUID uuid) {
        return this.stateManager.state().vanishedPlayers.contains(uuid.toString());
    }

    public synchronized boolean toggleVanish(ServerPlayerEntity actor, ServerPlayerEntity target) {
        if (target == null) {
            return false;
        }
        boolean nowVanished;
        if (isVanished(target.getUuid())) {
            removeVanish(target, true);
            nowVanished = false;
        } else {
            applyVanish(target, true);
            nowVanished = true;
        }
        refreshAllVisibility(ServerAccess.server(target));
        this.stateManager.markDirty(ServerAccess.server(target));

        String actorText = actor == null ? "console" : AuditLogger.actor(actor);
        AuditLogger.sensitive(
                this.configManager,
                "Vanish " + (nowVanished ? "enabled" : "disabled") + " by " + actorText + " for " + target.getGameProfile().name()
        );
        if (actor != null && AdminMod.get() != null && AdminMod.get().moderationManager() != null) {
            AdminMod.get().moderationManager().recordStaffAction(actor, nowVanished ? "vanish_on" : "vanish_off", target.getGameProfile().name());
        }
        if (actor != null && isLeaveMessageEnabled(actor.getUuid())) {
            broadcastFakeVanishMessage(actor, nowVanished);
        }
        if (actor != null && nowVanished && actor.getUuid().equals(target.getUuid())) {
            sendReconnectRecommendation(actor);
        }
        return nowVanished;
    }

    public synchronized void handleJoin(ServerPlayerEntity joined) {
        MinecraftServer server = ServerAccess.server(joined);

        if (isVanished(joined.getUuid())) {
            applyVanish(joined, false);
        } else {
            applyVanishLoadPreferences(joined, true);
        }
        refreshVisibilityFor(joined, server);
        refreshVisibilityOf(joined, server);
        this.pendingJoinRefreshTicks.put(joined.getUuid(), JOIN_REFRESH_WINDOW_TICKS);
    }

    public synchronized void handleDisconnect(ServerPlayerEntity disconnected) {
        if (isVanished(disconnected.getUuid())) {
            this.stateManager.save(ServerAccess.server(disconnected));
        }
        this.pendingJoinRefreshTicks.remove(disconnected.getUuid());
        this.lastAppliedViewDistances.remove(disconnected.getUuid());
    }

    public synchronized void tick(MinecraftServer server) {
        tickJoinRefreshes(server);
        this.runtimeReconcileTicker++;
        if (this.runtimeReconcileTicker >= RUNTIME_RECONCILE_PERIOD_TICKS) {
            this.runtimeReconcileTicker = 0;
            reconcileRuntime(server, false);
        }
        this.actionbarTicker++;
        if (this.actionbarTicker < ACTIONBAR_PERIOD_TICKS) {
            scrubHiddenEquipment(server);
            return;
        }
        this.actionbarTicker = 0;
        String text = this.configManager.get().vanish_status_messages.actionbar;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (isVanished(player.getUuid())) {
                player.sendMessage(Text.literal(text).formatted(Formatting.AQUA), true);
            }
        }
        scrubHiddenEquipment(server);
    }

    private void applyVanish(ServerPlayerEntity player, boolean sendFeedback) {
        PersistentStateData data = this.stateManager.state();
        data.vanishedPlayers.add(player.getUuidAsString());

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
        player.getAbilities().allowFlying = true;
        player.getAbilities().flying = true;
        player.sendAbilitiesUpdate();
        applyNoClipForPlayer(player);
        applyVanishEnhancements(player);
        applyVanishLoadPreferences(player, true);
        if (sendFeedback) {
            player.sendMessage(Text.literal(this.configManager.get().vanish_status_messages.enabled).formatted(Formatting.GREEN), false);
        }
    }

    private void removeVanish(ServerPlayerEntity player, boolean sendFeedback) {
        PersistentStateData data = this.stateManager.state();
        data.vanishedPlayers.remove(player.getUuidAsString());

        player.removeStatusEffect(StatusEffects.INVISIBILITY);
        disableNoClipForPlayer(player);
        if (!player.isCreative() && !player.isSpectator()) {
            setFlyingState(player, false, false);
        }
        setFlySpeed(player, BASE_FLY_SPEED);
        if (isNightVisionEnabled(player.getUuid())) {
            player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
        applyVanishLoadPreferences(player, true);
        if (sendFeedback) {
            player.sendMessage(Text.literal(this.configManager.get().vanish_status_messages.disabled).formatted(Formatting.RED), false);
        }
    }

    private void reconcileRuntime(MinecraftServer server, boolean forceRefreshDistances) {
        Set<UUID> online = new HashSet<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            online.add(player.getUuid());
            if (isVanished(player.getUuid())) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
                applyNoClipForPlayer(player);
                applyVanishEnhancements(player);
            } else {
                disableNoClipForPlayer(player);
                setFlySpeed(player, BASE_FLY_SPEED);
            }
            applyVanishLoadPreferences(player, forceRefreshDistances);
        }

        this.pendingJoinRefreshTicks.keySet().removeIf(uuid -> !online.contains(uuid));
        this.lastAppliedViewDistances.keySet().removeIf(uuid -> !online.contains(uuid));
        this.noClipPreviousGameModes.keySet().removeIf(uuid -> !online.contains(uuid));
    }

    private void applyNoClipForPlayer(ServerPlayerEntity player) {
        if (!this.configManager.get().vanish_noclip_enabled) {
            disableNoClipForPlayer(player);
            if (isVanished(player.getUuid()) && player.interactionManager.getGameMode() != GameMode.CREATIVE) {
                player.changeGameMode(GameMode.CREATIVE);
            }
            return;
        }
        UUID uuid = player.getUuid();
        this.noClipPreviousGameModes.putIfAbsent(uuid, player.interactionManager.getGameMode());
        if (player.interactionManager.getGameMode() != GameMode.SPECTATOR) {
            player.changeGameMode(GameMode.SPECTATOR);
        }
    }

    private void disableNoClipForPlayer(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        GameMode previous = this.noClipPreviousGameModes.remove(uuid);
        if (previous != null && player.interactionManager.getGameMode() != previous) {
            player.changeGameMode(previous);
        }
    }

    private void applyVanishLoadPreferences(ServerPlayerEntity player, boolean force) {
        if (player.networkHandler == null) {
            return;
        }
        MinecraftServer server = ServerAccess.server(player);
        if (server == null) {
            return;
        }
        boolean vanished = isVanished(player.getUuid());
        int chunkDistance = vanished && !this.configManager.get().vanish_load_chunks_enabled
                ? VANISH_LOW_DISTANCE
                : server.getPlayerManager().getViewDistance();
        int simulationDistance = vanished && !this.configManager.get().vanish_load_entities_enabled
                ? VANISH_LOW_DISTANCE
                : server.getPlayerManager().getSimulationDistance();
        ViewDistanceState previous = this.lastAppliedViewDistances.get(player.getUuid());
        ViewDistanceState next = new ViewDistanceState(Math.max(2, chunkDistance), Math.max(2, simulationDistance));
        if (!force && previous != null && previous.equals(next)) {
            return;
        }
        this.lastAppliedViewDistances.put(player.getUuid(), next);
        player.networkHandler.sendPacket(new ChunkLoadDistanceS2CPacket(Math.max(2, chunkDistance)));
        player.networkHandler.sendPacket(new SimulationDistanceS2CPacket(Math.max(2, simulationDistance)));
    }

    private void applyVanishEnhancements(ServerPlayerEntity player) {
        boolean vanished = isVanished(player.getUuid());
        if (vanished) {
            int level = getVanishFlySpeedLevel(player.getUuid());
            setFlySpeed(player, BASE_FLY_SPEED * level);
            if (isNightVisionEnabled(player.getUuid())) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false));
            } else {
                player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }
        } else {
            setFlySpeed(player, BASE_FLY_SPEED);
        }
    }

    private void refreshAllVisibility(MinecraftServer server) {
        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
            refreshVisibilityFor(viewer, server);
        }
    }

    private void refreshVisibilityFor(ServerPlayerEntity viewer, MinecraftServer server) {
        for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
            if (viewer.getUuid().equals(target.getUuid())) {
                continue;
            }
            refreshOnePair(viewer, target);
        }
    }

    private void refreshVisibilityOf(ServerPlayerEntity target, MinecraftServer server) {
        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
            if (viewer.getUuid().equals(target.getUuid())) {
                continue;
            }
            refreshOnePair(viewer, target);
        }
    }

    private void refreshOnePair(ServerPlayerEntity viewer, ServerPlayerEntity target) {
        if (target.networkHandler == null || viewer.networkHandler == null) {
            return;
        }

        boolean targetVanished = isVanished(target.getUuid());
        if (!targetVanished) {
            viewer.networkHandler.sendPacket(PlayerListS2CPacket.entryFromPlayer(List.of(target)));
            return;
        }

        boolean viewerCanSeeVanished = canViewerSeeVanished(viewer);
        if (viewerCanSeeVanished) {
            viewer.networkHandler.sendPacket(PlayerListS2CPacket.entryFromPlayer(List.of(target)));
        } else {
            viewer.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(target.getUuid())));
            sendHiddenEquipment(viewer, target);
        }
    }

    private void tickJoinRefreshes(MinecraftServer server) {
        if (this.pendingJoinRefreshTicks.isEmpty()) {
            return;
        }
        List<UUID> viewers = List.copyOf(this.pendingJoinRefreshTicks.keySet());
        for (UUID viewerUuid : viewers) {
            ServerPlayerEntity viewer = server.getPlayerManager().getPlayer(viewerUuid);
            if (viewer == null) {
                this.pendingJoinRefreshTicks.remove(viewerUuid);
                continue;
            }
            refreshVisibilityFor(viewer, server);
            int remaining = this.pendingJoinRefreshTicks.getOrDefault(viewerUuid, 0) - 1;
            if (remaining <= 0) {
                this.pendingJoinRefreshTicks.remove(viewerUuid);
            } else {
                this.pendingJoinRefreshTicks.put(viewerUuid, remaining);
            }
        }
    }

    public synchronized boolean canViewerSeeVanished(ServerPlayerEntity viewer) {
        return PermissionUtil.canUseAdminGui(viewer, this.configManager)
                && ((this.configManager.get().staff_can_see_vanished_staff || this.configManager.get().vanished_admins_can_see_other_vanished)
                || !isVanished(viewer.getUuid()));
    }

    public synchronized Set<UUID> vanishedPlayers() {
        return this.stateManager.state().vanishedPlayers.stream().map(UUID::fromString).collect(Collectors.toSet());
    }

    public synchronized boolean isLeaveMessageEnabled(UUID uuid) {
        return this.stateManager.state().vanishLeaveMessageToggles.getOrDefault(uuid.toString(), this.configManager.get().vanish_leave_message_default);
    }

    public synchronized void setLeaveMessageEnabled(ServerPlayerEntity actor, boolean enabled) {
        this.stateManager.state().vanishLeaveMessageToggles.put(actor.getUuidAsString(), enabled);
        this.stateManager.markDirty(ServerAccess.server(actor));
        AuditLogger.sensitive(this.configManager, AuditLogger.actor(actor) + " set vanish leave-message to " + enabled);
    }

    public synchronized int getVanishFlySpeedLevel(UUID uuid) {
        int level = this.stateManager.state().vanishFlySpeedLevels.getOrDefault(uuid.toString(), MIN_VANISH_FLY_SPEED_LEVEL);
        return Math.max(MIN_VANISH_FLY_SPEED_LEVEL, Math.min(MAX_VANISH_FLY_SPEED_LEVEL, level));
    }

    public synchronized int cycleVanishFlySpeedLevel(ServerPlayerEntity actor) {
        int current = getVanishFlySpeedLevel(actor.getUuid());
        int next = current >= MAX_VANISH_FLY_SPEED_LEVEL ? MIN_VANISH_FLY_SPEED_LEVEL : current + 1;
        this.stateManager.state().vanishFlySpeedLevels.put(actor.getUuidAsString(), next);
        applyVanishEnhancements(actor);
        this.stateManager.markDirty(ServerAccess.server(actor));
        AuditLogger.sensitive(this.configManager, AuditLogger.actor(actor) + " set vanish fly speed level to " + next);
        return next;
    }

    public synchronized boolean isNightVisionEnabled(UUID uuid) {
        return this.stateManager.state().vanishNightVisionToggles.getOrDefault(uuid.toString(), false);
    }

    public synchronized void setNightVisionEnabled(ServerPlayerEntity actor, boolean enabled) {
        this.stateManager.state().vanishNightVisionToggles.put(actor.getUuidAsString(), enabled);
        applyVanishEnhancements(actor);
        this.stateManager.markDirty(ServerAccess.server(actor));
        AuditLogger.sensitive(this.configManager, AuditLogger.actor(actor) + " set vanish night vision to " + enabled);
    }

    public synchronized boolean interceptDirectMessageCommand(ServerPlayerEntity sender, String rawCommand) {
        if (sender == null || rawCommand == null || rawCommand.isBlank()) {
            return false;
        }
        String normalized = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        String[] parts = normalized.split("\\s+", 3);
        if (parts.length < 2) {
            return false;
        }
        String root = parts[0].toLowerCase(Locale.ROOT);
        if (!root.equals("msg") && !root.equals("tell") && !root.equals("w") && !root.equals("whisper")) {
            return false;
        }
        MinecraftServer server = ServerAccess.server(sender);
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(parts[1]);
        if (target == null || !isVanished(target.getUuid()) || canViewerSeeVanished(sender)) {
            return false;
        }
        String attempted = parts.length >= 3 ? parts[2] : "";
        Text notice = Text.literal("[Hidden DM Attempt] ")
                .formatted(Formatting.DARK_GRAY)
                .append(Text.literal(sender.getGameProfile().name()).formatted(Formatting.GRAY))
                .append(Text.literal(" -> you: ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(attempted).formatted(Formatting.GRAY));
        target.sendMessage(notice, false);
        sender.sendMessage(Text.literal("No player was found"), false);
        return true;
    }

    private void scrubHiddenEquipment(MinecraftServer server) {
        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
            if (canViewerSeeVanished(viewer)) {
                continue;
            }
            for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
                if (!isVanished(target.getUuid()) || viewer.getUuid().equals(target.getUuid())) {
                    continue;
                }
                sendHiddenEquipment(viewer, target);
            }
        }
    }

    private void sendHiddenEquipment(ServerPlayerEntity viewer, ServerPlayerEntity target) {
        List<Pair<EquipmentSlot, ItemStack>> equipment = List.of(
                Pair.of(EquipmentSlot.HEAD, ItemStack.EMPTY),
                Pair.of(EquipmentSlot.CHEST, ItemStack.EMPTY),
                Pair.of(EquipmentSlot.LEGS, ItemStack.EMPTY),
                Pair.of(EquipmentSlot.FEET, ItemStack.EMPTY),
                Pair.of(EquipmentSlot.MAINHAND, ItemStack.EMPTY),
                Pair.of(EquipmentSlot.OFFHAND, ItemStack.EMPTY)
        );
        viewer.networkHandler.sendPacket(new EntityEquipmentUpdateS2CPacket(target.getId(), equipment));
    }

    private void broadcastFakeVanishMessage(ServerPlayerEntity actor, boolean vanishedNow) {
        String template = vanishedNow
                ? this.configManager.get().vanish_fake_leave_message_format
                : this.configManager.get().vanish_fake_join_message_format;
        String rendered = template.replace("%player%", actor.getGameProfile().name());
        Text line = Text.literal(rendered).formatted(Formatting.YELLOW);
        for (ServerPlayerEntity online : ServerAccess.server(actor).getPlayerManager().getPlayerList()) {
            online.sendMessage(line, false);
        }
    }

    private void sendReconnectRecommendation(ServerPlayerEntity actor) {
        Text prompt = Text.literal("[AdminMod] For best vanish results, leave and rejoin now. ").formatted(Formatting.YELLOW);
        Text yes = Text.literal("[YES]")
                .setStyle(Style.EMPTY
                        .withColor(Formatting.GREEN)
                        .withClickEvent(new ClickEvent.RunCommand("/admin vanishrejoin yes"))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Enable temporary silent reconnect and disconnect you now"))));
        Text no = Text.literal(" [NO]")
                .setStyle(Style.EMPTY
                        .withColor(Formatting.RED)
                        .withClickEvent(new ClickEvent.RunCommand("/admin vanishrejoin no"))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Keep current session"))));
        actor.sendMessage(prompt.copy().append(yes).append(no), false);
    }

    private void setFlyingState(ServerPlayerEntity player, boolean allowFlying, boolean flying) {
        boolean changed = player.getAbilities().allowFlying != allowFlying || player.getAbilities().flying != flying;
        player.getAbilities().allowFlying = allowFlying;
        player.getAbilities().flying = flying;
        if (changed) {
            player.sendAbilitiesUpdate();
        }
    }

    private void setFlySpeed(ServerPlayerEntity player, float speed) {
        if (Float.compare(player.getAbilities().getFlySpeed(), speed) == 0) {
            return;
        }
        player.getAbilities().setFlySpeed(speed);
        player.sendAbilitiesUpdate();
    }

    private record ViewDistanceState(int chunkDistance, int simulationDistance) {
    }
}
