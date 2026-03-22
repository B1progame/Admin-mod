package com.b1progame.adminmod.state;

public final class BanRecordData {
    public String targetUuid = "";
    public String targetName = "";
    public String actorUuid = "";
    public String actorName = "";
    public String reason = "";
    public boolean permanent = true;
    public long createdAtEpochMillis = 0L;
    public long expiresAtEpochMillis = 0L;
}
