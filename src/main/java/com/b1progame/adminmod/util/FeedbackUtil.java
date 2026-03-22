package com.b1progame.adminmod.util;

import com.b1progame.adminmod.config.ConfigManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class FeedbackUtil {
    private FeedbackUtil() {
    }

    public static void success(ServerPlayerEntity player, ConfigManager configManager, String message) {
        success(player, configManager, Text.literal(message));
    }

    public static void success(ServerPlayerEntity player, ConfigManager configManager, Text message) {
        player.sendMessage(message.copy().formatted(Formatting.GREEN), false);
        if (configManager.get().sound_feedback_enabled) {
            player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
        }
    }

    public static void error(ServerPlayerEntity player, ConfigManager configManager, String message) {
        error(player, configManager, Text.literal(message));
    }

    public static void error(ServerPlayerEntity player, ConfigManager configManager, Text message) {
        player.sendMessage(message.copy().formatted(Formatting.RED), false);
        if (configManager.get().sound_feedback_enabled) {
            player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.5f, 0.7f);
        }
    }
}
