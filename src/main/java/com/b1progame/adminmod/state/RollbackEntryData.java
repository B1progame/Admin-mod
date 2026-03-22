package com.b1progame.adminmod.state;

import java.util.ArrayList;
import java.util.List;

public final class RollbackEntryData {
    public long id = 0L;
    public String targetUuid = "";
    public String targetName = "";
    public String actorUuid = "";
    public String actorName = "";
    public long createdAtEpochMillis = 0L;
    public String inventoryType = "";
    public String actionType = "";
    public List<SnapshotStackData> removedStacks = new ArrayList<>();
    public boolean rolledBack = false;
    public String rolledBackByUuid = "";
    public String rolledBackByName = "";
    public long rolledBackAtEpochMillis = 0L;
}
