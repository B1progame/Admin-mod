package com.b1progame.adminmod.mixin;

import com.b1progame.adminmod.AdminMod;
import net.minecraft.network.packet.c2s.play.ChatCommandSignedC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow
    @Final
    public ServerPlayerEntity player;

    @Inject(method = "onUpdateSign", at = @At("HEAD"), cancellable = true)
    private void adminmod$captureSearchSignInput(UpdateSignC2SPacket packet, CallbackInfo ci) {
        if (AdminMod.get() == null || AdminMod.get().guiService() == null) {
            return;
        }
        boolean consumed = AdminMod.get().guiService().playerSearchInputManager()
                .handleSignUpdate(this.player, packet.getPos(), packet.getText());
        if (consumed) {
            ci.cancel();
        }
    }

    @Inject(method = "onClickSlot", at = @At("HEAD"), cancellable = true)
    private void adminmod$handleReplayControlSlotClick(ClickSlotC2SPacket packet, CallbackInfo ci) {
        if (AdminMod.get() == null || AdminMod.get().xrayReplayManager() == null) {
            return;
        }
        if (AdminMod.get().xrayReplayManager().handleInventoryControlClick(this.player, packet.slot())) {
            ci.cancel();
        }
    }

    @Inject(method = "onCreativeInventoryAction", at = @At("HEAD"), cancellable = true)
    private void adminmod$handleReplayCreativeControlClick(CreativeInventoryActionC2SPacket packet, CallbackInfo ci) {
        if (AdminMod.get() == null || AdminMod.get().xrayReplayManager() == null) {
            return;
        }
        if (AdminMod.get().xrayReplayManager().handleCreativeInventoryControl(this.player, packet.stack(), packet.slot())) {
            ci.cancel();
        }
    }

    @Inject(method = "onCommandExecution", at = @At("HEAD"), cancellable = true)
    private void adminmod$hideVanishedDirectMessages(CommandExecutionC2SPacket packet, CallbackInfo ci) {
        if (AdminMod.get() == null || AdminMod.get().vanishManager() == null) {
            return;
        }
        if (AdminMod.get().vanishManager().interceptDirectMessageCommand(this.player, packet.command())) {
            ci.cancel();
        }
    }

    @Inject(method = "onChatCommandSigned", at = @At("HEAD"), cancellable = true)
    private void adminmod$hideVanishedSignedDirectMessages(ChatCommandSignedC2SPacket packet, CallbackInfo ci) {
        if (AdminMod.get() == null || AdminMod.get().vanishManager() == null) {
            return;
        }
        if (AdminMod.get().vanishManager().interceptDirectMessageCommand(this.player, packet.command())) {
            ci.cancel();
        }
    }
}
