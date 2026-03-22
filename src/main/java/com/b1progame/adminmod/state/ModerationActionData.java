package com.b1progame.adminmod.state;

public final class ModerationActionData {
    public long id = 0L;
    public String targetUuid = "";
    public String targetName = "";
    public String actionType = "";
    public String actorUuid = "";
    public String actorName = "";
    public long createdAtEpochMillis = 0L;
    public String details = "";
    public long expiresAtEpochMillis = 0L;
}
