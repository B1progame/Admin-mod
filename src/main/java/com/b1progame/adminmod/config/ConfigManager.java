package com.b1progame.adminmod.config;

import com.b1progame.adminmod.AdminMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("adminmod.json");
    private AdminConfig config = new AdminConfig();

    public synchronized void load() {
        try {
            Files.createDirectories(this.configPath.getParent());
            if (!Files.exists(this.configPath)) {
                this.config = new AdminConfig();
                save();
                return;
            }
            try (Reader reader = Files.newBufferedReader(this.configPath)) {
                AdminConfig loaded = GSON.fromJson(reader, AdminConfig.class);
                this.config = loaded == null ? new AdminConfig() : loaded;
            }
            save();
        } catch (Exception exception) {
            AdminMod.LOGGER.error("Failed to load config {}, using defaults.", this.configPath, exception);
            this.config = new AdminConfig();
            save();
        }
    }

    public synchronized void save() {
        try (Writer writer = Files.newBufferedWriter(this.configPath)) {
            GSON.toJson(this.config, writer);
        } catch (IOException exception) {
            AdminMod.LOGGER.error("Failed to save config {}", this.configPath, exception);
        }
    }

    public synchronized AdminConfig get() {
        return this.config;
    }

    public synchronized Set<UUID> allowedAdminUuids() {
        Set<UUID> uuids = new HashSet<>();
        for (String raw : this.config.allowed_admin_uuids) {
            try {
                uuids.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return uuids;
    }
}
