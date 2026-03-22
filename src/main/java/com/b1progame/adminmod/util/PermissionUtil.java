package com.b1progame.adminmod.util;

import com.b1progame.adminmod.config.ConfigManager;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public final class PermissionUtil {
    private PermissionUtil() {
    }

    public static boolean canUseAdminGui(ServerPlayerEntity player, ConfigManager configManager) {
        if (ServerAccess.server(player).getPlayerManager().isOperator(new PlayerConfigEntry(player.getGameProfile()))) {
            return true;
        }
        return configManager.allowedAdminUuids().contains(player.getUuid());
    }

    public static boolean canUseAdminGui(UUID uuid, ServerCommandSource source, ConfigManager configManager) {
        if (source.getServer().getPlayerManager().isOperator(new PlayerConfigEntry(uuid, ""))) {
            return true;
        }
        return configManager.allowedAdminUuids().contains(uuid);
    }
}
