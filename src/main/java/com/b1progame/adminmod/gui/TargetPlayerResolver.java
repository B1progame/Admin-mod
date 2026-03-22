package com.b1progame.adminmod.gui;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import com.b1progame.adminmod.util.ServerAccess;

import java.util.UUID;

public final class TargetPlayerResolver {
    private TargetPlayerResolver() {
    }

    public static ServerPlayerEntity resolve(ServerPlayerEntity viewer, UUID targetUuid) {
        MinecraftServer server = ServerAccess.server(viewer);
        return server.getPlayerManager().getPlayer(targetUuid);
    }
}
