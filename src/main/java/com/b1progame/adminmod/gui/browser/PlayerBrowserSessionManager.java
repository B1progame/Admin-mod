package com.b1progame.adminmod.gui.browser;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerBrowserSessionManager {
    private final Map<UUID, PlayerBrowserState> states = new HashMap<>();

    public synchronized PlayerBrowserState getOrCreate(UUID viewer) {
        return this.states.computeIfAbsent(viewer, ignored -> new PlayerBrowserState());
    }
}
