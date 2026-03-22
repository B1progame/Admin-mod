package com.b1progame.adminmod.state;

import java.util.ArrayList;
import java.util.List;

public final class InventorySnapshotData {
    public long id = 0L;
    public String targetUuid = "";
    public String targetName = "";
    public String createdByUuid = "";
    public String createdByName = "";
    public long createdAtEpochMillis = 0L;
    public boolean includeInventory = true;
    public boolean includeEnderChest = true;
    public List<SnapshotStackData> inventoryStacks = new ArrayList<>();
    public List<SnapshotStackData> enderStacks = new ArrayList<>();
}
