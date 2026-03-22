package com.b1progame.adminmod.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityNoClipAccessor {
    @Accessor("noClip")
    void adminmod$setNoClip(boolean value);

    @Accessor("noClip")
    boolean adminmod$isNoClip();
}
