package com.b1progame.adminmod.mixin;

import com.b1progame.adminmod.AdminMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.ServerMetadata;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "createMetadataPlayers", at = @At("RETURN"), cancellable = true)
    private void adminmod$hideVanishedFromServerList(CallbackInfoReturnable<ServerMetadata.Players> cir) {
        ServerMetadata.Players players = cir.getReturnValue();
        if (players == null || AdminMod.get() == null || AdminMod.get().vanishManager() == null) {
            return;
        }

        int vanishedOnline = 0;
        for (UUID vanished : AdminMod.get().vanishManager().vanishedPlayers()) {
            if (((MinecraftServer) (Object) this).getPlayerManager().getPlayer(vanished) != null) {
                vanishedOnline++;
            }
        }
        if (vanishedOnline <= 0) {
            return;
        }

        List<PlayerConfigEntry> filteredSample = new ArrayList<>();
        for (PlayerConfigEntry profile : players.sample()) {
            UUID uuid = profile == null ? null : profile.id();
            if (uuid != null && AdminMod.get().vanishManager().isVanished(uuid)) {
                continue;
            }
            filteredSample.add(profile);
        }

        int visibleOnline = Math.max(0, players.online() - vanishedOnline);
        cir.setReturnValue(new ServerMetadata.Players(players.max(), visibleOnline, filteredSample));
    }
}
