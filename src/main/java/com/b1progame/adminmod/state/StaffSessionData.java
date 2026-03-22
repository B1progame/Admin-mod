package com.b1progame.adminmod.state;

import java.util.ArrayList;
import java.util.List;

public final class StaffSessionData {
    public long id = 0L;
    public String staffUuid = "";
    public String staffName = "";
    public long startedAtEpochMillis = 0L;
    public long endedAtEpochMillis = 0L;
    public String startReason = "";
    public String endReason = "";
    public List<StaffSessionActionData> actions = new ArrayList<>();
}
