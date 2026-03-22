package com.b1progame.adminmod.gui.data;

import com.b1progame.adminmod.gui.browser.PlayerBrowserEntry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.inventory.StackWithSlot;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.ReadView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.world.GameMode;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public final class PlayerDataRepository {
    public List<PlayerBrowserEntry> loadEntries(MinecraftServer server) {
        Map<UUID, PlayerBrowserEntry> byUuid = new LinkedHashMap<>();

        Map<UUID, String> knownNames = loadKnownNames(server);
        for (UUID uuid : loadOfflinePlayerDataUuids(server)) {
            knownNames.putIfAbsent(uuid, uuid.toString());
        }
        for (Map.Entry<UUID, String> entry : knownNames.entrySet()) {
            PlayerBrowserEntry data = new PlayerBrowserEntry();
            data.uuid = entry.getKey();
            data.name = entry.getValue();
            data.online = false;
            data.ping = -1;
            data.worldName = "-";
            data.dimensionName = "-";
            data.health = -1.0F;
            data.gameMode = null;
            data.dataAvailable = hasPlayerData(server, entry.getKey());
            byUuid.put(entry.getKey(), data);
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerBrowserEntry online = byUuid.computeIfAbsent(player.getUuid(), ignored -> new PlayerBrowserEntry());
            online.uuid = player.getUuid();
            online.name = player.getGameProfile().name();
            online.online = true;
            online.ping = player.networkHandler.getLatency();
            online.worldName = player.getEntityWorld().getRegistryKey().getValue().toString();
            online.dimensionName = player.getEntityWorld().getRegistryKey().getValue().toString();
            online.health = player.getHealth();
            online.gameMode = player.interactionManager.getGameMode();
            online.dataAvailable = true;
        }

        return new ArrayList<>(byUuid.values());
    }

    public Optional<SimpleInventory> loadOfflineInventory(MinecraftServer server, UUID uuid) {
        Optional<NbtCompound> playerData = readPlayerData(server, uuid);
        if (playerData.isEmpty()) {
            return Optional.empty();
        }
        SimpleInventory inventory = new SimpleInventory(41);
        ReadView readView = NbtReadView.create(ErrorReporter.EMPTY, registries(server), playerData.get());
        readView.getOptionalTypedListView("Inventory", StackWithSlot.CODEC).ifPresent(stacks -> {
            for (StackWithSlot stackWithSlot : stacks) {
                int slot = stackWithSlot.slot();
                if (slot >= 0 && slot < 41) {
                    inventory.setStack(slot, stackWithSlot.stack());
                }
            }
        });
        return Optional.of(inventory);
    }

    public Optional<SimpleInventory> loadOfflineEnderChest(MinecraftServer server, UUID uuid) {
        Optional<NbtCompound> playerData = readPlayerData(server, uuid);
        if (playerData.isEmpty()) {
            return Optional.empty();
        }
        SimpleInventory ender = new SimpleInventory(27);
        ReadView readView = NbtReadView.create(ErrorReporter.EMPTY, registries(server), playerData.get());
        readView.getOptionalTypedListView("EnderItems", StackWithSlot.CODEC).ifPresent(stacks -> {
            for (StackWithSlot stackWithSlot : stacks) {
                int slot = stackWithSlot.slot();
                if (slot >= 0 && slot < 27) {
                    ender.setStack(slot, stackWithSlot.stack());
                }
            }
        });
        return Optional.of(ender);
    }

    private boolean hasPlayerData(MinecraftServer server, UUID uuid) {
        return Files.exists(playerDataPath(server, uuid));
    }

    private Optional<NbtCompound> readPlayerData(MinecraftServer server, UUID uuid) {
        Path path = playerDataPath(server, uuid);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Path playerDataPath(MinecraftServer server, UUID uuid) {
        return server.getSavePath(net.minecraft.util.WorldSavePath.PLAYERDATA).resolve(uuid.toString() + ".dat");
    }

    private RegistryWrapper.WrapperLookup registries(MinecraftServer server) {
        return server.getRegistryManager();
    }

    private List<UUID> loadOfflinePlayerDataUuids(MinecraftServer server) {
        List<UUID> out = new ArrayList<>();
        Path playerdata = server.getSavePath(net.minecraft.util.WorldSavePath.PLAYERDATA);
        if (!Files.isDirectory(playerdata)) {
            return out;
        }
        try (Stream<Path> stream = Files.list(playerdata)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".dat")).forEach(path -> {
                String file = path.getFileName().toString();
                String raw = file.substring(0, file.length() - 4);
                try {
                    out.add(UUID.fromString(raw));
                } catch (IllegalArgumentException ignored) {
                }
            });
        } catch (Exception ignored) {
        }
        return out;
    }

    private Map<UUID, String> loadKnownNames(MinecraftServer server) {
        Path cachePath = server.getPath("usercache.json");
        Map<UUID, String> out = new HashMap<>();
        if (!Files.exists(cachePath)) {
            return out;
        }
        try (Reader reader = Files.newBufferedReader(cachePath)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!(parsed instanceof JsonArray array)) {
                return out;
            }
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                String uuidRaw = element.getAsJsonObject().has("uuid") ? element.getAsJsonObject().get("uuid").getAsString() : "";
                String name = element.getAsJsonObject().has("name") ? element.getAsJsonObject().get("name").getAsString() : "";
                if (uuidRaw.isBlank() || name.isBlank()) {
                    continue;
                }
                try {
                    out.put(UUID.fromString(uuidRaw), name);
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
