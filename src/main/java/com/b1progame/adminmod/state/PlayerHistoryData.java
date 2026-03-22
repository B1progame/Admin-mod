package com.b1progame.adminmod.state;

public final class PlayerHistoryData {
    public String lastKnownName = "";
    public String lastWorld = "";
    public String lastDimension = "";
    public double lastX = 0.0D;
    public double lastY = 0.0D;
    public double lastZ = 0.0D;
    public int lastPing = -1;
    public float lastHealth = -1.0F;
    public int lastHunger = -1;
    public int lastXpLevel = -1;
    public String lastKnownIp = "";
    public boolean online = false;
    public long lastJoinEpochMillis = 0L;
    public long lastQuitEpochMillis = 0L;
    public long lastSeenEpochMillis = 0L;
}
