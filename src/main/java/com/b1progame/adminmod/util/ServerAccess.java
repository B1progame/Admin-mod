package com.b1progame.adminmod.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ServerAccess {
    private ServerAccess() {
    }

    public static MinecraftServer server(ServerPlayerEntity player) {
        return player.getCommandSource().getServer();
    }
}
