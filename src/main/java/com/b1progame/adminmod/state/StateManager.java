package com.b1progame.adminmod.state;

import com.b1progame.adminmod.AdminMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

public final class StateManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private PersistentStateData state = new PersistentStateData();

    public synchronized PersistentStateData state() {
        return this.state;
    }

    public synchronized void markDirty(MinecraftServer server) {
        save(server);
    }

    public synchronized void load(MinecraftServer server) {
        Path path = statePath(server);
        try {
            if (!Files.exists(path)) {
                this.state = new PersistentStateData();
                save(server);
                return;
            }
            try (Reader reader = Files.newBufferedReader(path)) {
                PersistentStateData loaded = GSON.fromJson(reader, PersistentStateData.class);
                this.state = loaded == null ? new PersistentStateData() : loaded;
                normalizeState(this.state);
            }
        } catch (Exception exception) {
            AdminMod.LOGGER.error("Failed to load state file {}, resetting state.", path, exception);
            this.state = new PersistentStateData();
            save(server);
        }
    }

    public synchronized void save(MinecraftServer server) {
        Path path = statePath(server);
        try {
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this.state, writer);
            }
        } catch (Exception exception) {
            AdminMod.LOGGER.error("Failed to save state file {}", path, exception);
        }
    }

    private void normalizeState(PersistentStateData state) {
        if (state.watchlistEntries == null) state.watchlistEntries = new HashMap<>();
        if (state.rollbackEntries == null) state.rollbackEntries = new ArrayList<>();
        if (state.staffSessions == null) state.staffSessions = new ArrayList<>();
        if (state.activeStaffSessionByUuid == null) state.activeStaffSessionByUuid = new HashMap<>();
        if (state.vanishLeaveMessageToggles == null) state.vanishLeaveMessageToggles = new HashMap<>();
        if (state.silentJoinToggles == null) state.silentJoinToggles = new HashMap<>();
        if (state.silentDisconnectToggles == null) state.silentDisconnectToggles = new HashMap<>();
        if (state.consoleChatToggles == null) state.consoleChatToggles = new HashMap<>();
        if (state.pendingSilentJoinRestore == null) state.pendingSilentJoinRestore = new HashMap<>();
        if (state.pendingSilentDisconnectRestore == null) state.pendingSilentDisconnectRestore = new HashMap<>();
        if (state.vanishFlySpeedLevels == null) state.vanishFlySpeedLevels = new HashMap<>();
        if (state.vanishNightVisionToggles == null) state.vanishNightVisionToggles = new HashMap<>();
        if (state.xrayOreEnabled == null) state.xrayOreEnabled = new HashMap<>();
        if (state.commandHistory == null) state.commandHistory = new ArrayList<>();
        if (state.playerHistory == null) state.playerHistory = new HashMap<>();
        if (state.inventorySnapshots == null) state.inventorySnapshots = new ArrayList<>();
        if (state.moderationActions == null) state.moderationActions = new ArrayList<>();
        if (state.activeBans == null) state.activeBans = new HashMap<>();
        if (state.moderationNotes == null) state.moderationNotes = new HashMap<>();
        if (state.moderationNoteCounters == null) state.moderationNoteCounters = new HashMap<>();
        if (state.tempOpEntries == null) state.tempOpEntries = new HashMap<>();
        if (state.xrayRecords == null) state.xrayRecords = new ArrayList<>();
        if (state.pendingStaffMail == null) state.pendingStaffMail = new ArrayList<>();
        if (state.xrayReplayWatchedPlayers == null) state.xrayReplayWatchedPlayers = new HashMap<>();
        if (state.heatmapCells == null) state.heatmapCells = new ArrayList<>();
        if (state.lagIdentifyResults == null) state.lagIdentifyResults = new ArrayList<>();
    }

    private Path statePath(MinecraftServer server) {
        return server.getPath("adminmod-state.json");
    }
}
