package com.b1progame.adminmod.gui.browser;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.GameMode;

import java.util.UUID;

public final class PlayerBrowserEntry {
    public UUID uuid;
    public String name;
    public boolean online;
    public int ping;
    public String worldName;
    public String dimensionName;
    public float health;
    public GameMode gameMode;
    public boolean dataAvailable;
    public GameProfile profile;
}
