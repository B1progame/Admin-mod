package com.b1progame.adminmod.util;

import com.b1progame.adminmod.AdminMod;
import com.b1progame.adminmod.config.ConfigManager;
import net.minecraft.server.network.ServerPlayerEntity;

public final class AuditLogger {
    private AuditLogger() {
    }

    public static void sensitive(ConfigManager configManager, String message) {
        if (configManager.get().logging.sensitive_actions) {
            AdminMod.LOGGER.info("[AUDIT] {}", message);
        }
    }

    public static void inventory(ConfigManager configManager, String message) {
        if (configManager.get().logging.inventory_actions) {
            AdminMod.LOGGER.info("[AUDIT] {}", message);
        }
    }

    public static String actor(ServerPlayerEntity actor) {
        return actor.getGameProfile().name() + " (" + actor.getUuidAsString() + ")";
    }
}
