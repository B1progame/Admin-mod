package com.b1progame.adminmod.mixin;

import com.b1progame.adminmod.AdminMod;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void adminmod$blockDropWhileFrozen(boolean entireStack, CallbackInfo cir) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        if (AdminMod.get() != null && AdminMod.get().moderationManager() != null
                && AdminMod.get().moderationManager().isFrozen(self.getUuid())) {
            cir.cancel();
        }
    }
}
