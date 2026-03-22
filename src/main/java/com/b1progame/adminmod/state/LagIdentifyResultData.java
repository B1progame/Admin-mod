package com.b1progame.adminmod.state;

public final class LagIdentifyResultData {
    public long resultId;
    public long scanSessionId;
    public long createdAtEpochMillis;
    public String world = "";
    public String dimension = "";
    public int chunkX;
    public int chunkZ;
    public int centerX;
    public int centerY;
    public int centerZ;
    public double score;
    public String category = "";
    public String summary = "";
    public int droppedItems;
    public int totalEntities;
    public int mobs;
    public int projectiles;
    public int villagers;
    public int xpOrbs;
    public int blockEntities;
    public int tickingBlockEntities;
    public int hoppers;
    public int containers;
    public int redstoneComponents;
}
