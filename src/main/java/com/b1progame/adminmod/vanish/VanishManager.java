package com.b1progame.adminmod.vanish;

import com.b1progame.adminmod.AdminMod;
import com.b1progame.adminmod.config.ConfigManager;
import com.b1progame.adminmod.mixin.EntityNoClipAccessor;
import com.b1progame.adminmod.state.PersistentStateData;
import com.b1progame.adminmod.state.StateManager;
import com.b1progame.adminmod.util.AuditLogger;
import com.b1progame.adminmod.util.PermissionUtil;
import com.b1progame.adminmod.util.ServerAccess;
import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class VanishManager {
    private static final int ACTIONBAR_PERIOD_TICKS = 40;

    private final ConfigManager configManager;
    private final StateManager stateManager;
    private final Map<UUID, GameMode> noClipPreviousGameModes = new HashMap<>();
    private int actionbarTicker = 0;

    public VanishManager(ConfigManager configManager, StateManager stateManager) {
        this.configManager = configManager;
        this.stateManager = stateManager;
    }

    public synchronized void onServerStarted(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (isVanished(player.getUuid())) {
                applyVanish(player);
            }
        }
        refreshAllVisibility(server);
    }

    public synchronized void onServerStopping(MinecraftServer server) {
        this.stateManager.save(server);
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
            removeVanish(target);
            nowVanished = false;
        } else {
            applyVanish(target);
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
        return nowVanished;
    }

    public synchronized void handleJoin(ServerPlayerEntity joined) {
        MinecraftServer server = ServerAccess.server(joined);

        if (isVanished(joined.getUuid())) {
            applyVanish(joined);
        }
        refreshVisibilityFor(joined, server);
        refreshVisibilityOf(joined, server);
    }

    public synchronized void handleDisconnect(ServerPlayerEntity disconnected) {
        if (isVanished(disconnected.getUuid())) {
            this.stateManager.markDirty(ServerAccess.server(disconnected));
        }
    }

    public synchronized void tick(MinecraftServer server) {
        applyNoClipState(server);
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

    private void applyVanish(ServerPlayerEntity player) {
        PersistentStateData data = this.stateManager.state();
        data.vanishedPlayers.add(player.getUuidAsString());

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
        player.getAbilities().allowFlying = true;
        player.getAbilities().flying = true;
        player.sendAbilitiesUpdate();
        applyNoClipForPlayer(player);
        player.sendMessage(Text.literal(this.configManager.get().vanish_status_messages.enabled).formatted(Formatting.GREEN), false);
    }

    private void removeVanish(ServerPlayerEntity player) {
        PersistentStateData data = this.stateManager.state();
        data.vanishedPlayers.remove(player.getUuidAsString());

        player.removeStatusEffect(StatusEffects.INVISIBILITY);
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().flying = false;
            player.getAbilities().allowFlying = false;
            player.sendAbilitiesUpdate();
        }
        disableNoClipForPlayer(player);
        player.sendMessage(Text.literal(this.configManager.get().vanish_status_messages.disabled).formatted(Formatting.RED), false);
    }

    private void applyNoClipState(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (isVanished(player.getUuid())) {
                applyNoClipForPlayer(player);
            } else {
                disableNoClipForPlayer(player);
            }
        }
    }

    private void applyNoClipForPlayer(ServerPlayerEntity player) {
        if (!this.configManager.get().vanish_noclip_enabled) {
            disableNoClipForPlayer(player);
            return;
        }
        UUID uuid = player.getUuid();
        this.noClipPreviousGameModes.putIfAbsent(uuid, player.interactionManager.getGameMode());
        if (player.interactionManager.getGameMode() != GameMode.SPECTATOR) {
            player.changeGameMode(GameMode.SPECTATOR);
        }
        setNoClip(player, true);
    }

    private void disableNoClipForPlayer(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        GameMode previous = this.noClipPreviousGameModes.remove(uuid);
        if (previous != null && player.interactionManager.getGameMode() != previous) {
            player.changeGameMode(previous);
        }
        if (!player.isSpectator()) {
            setNoClip(player, false);
        }
    }

    private void setNoClip(ServerPlayerEntity player, boolean enabled) {
        EntityNoClipAccessor accessor = (EntityNoClipAccessor) player;
        if (accessor.adminmod$isNoClip() != enabled) {
            accessor.adminmod$setNoClip(enabled);
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
}
