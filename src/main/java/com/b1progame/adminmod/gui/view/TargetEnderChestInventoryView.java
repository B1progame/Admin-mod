package com.b1progame.adminmod.gui.view;

import com.b1progame.adminmod.gui.AdminGuiService;
import com.b1progame.adminmod.util.ServerAccess;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public final class TargetEnderChestInventoryView implements Inventory {
    private final ServerPlayerEntity viewer;
    private final AdminGuiService guiService;
    private final UUID targetUuid;
    private final ItemStack[] controls = new ItemStack[54];
    private final SimpleInventory offlineEnder;
    private final boolean loadedOfflineData;

    public TargetEnderChestInventoryView(ServerPlayerEntity viewer, AdminGuiService guiService, UUID targetUuid) {
        this.viewer = viewer;
        this.guiService = guiService;
        this.targetUuid = targetUuid;
        this.offlineEnder = this.guiService.playerDataRepository()
                .loadOfflineEnderChest(ServerAccess.server(viewer), targetUuid)
                .orElse(null);
        this.loadedOfflineData = this.offlineEnder != null;
    }

    @Override
    public int size() {
        return 54;
    }

    @Override
    public boolean isEmpty() {
        if (isOnline()) {
            return target().getEnderChestInventory().isEmpty();
        }
        return this.offlineEnder == null || this.offlineEnder.isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        if (slot >= 27) {
            return this.controls[slot] == null ? ItemStack.EMPTY : this.controls[slot];
        }
        if (isOnline()) {
            return target().getEnderChestInventory().getStack(slot);
        }
        if (this.offlineEnder != null) {
            return this.offlineEnder.getStack(slot);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot >= 27 || !isEditable()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = target().getEnderChestInventory().removeStack(slot, amount);
        if (!removed.isEmpty()) {
            this.guiService.moderationManager().recordSingleRemoval(
                    this.viewer,
                    this.targetUuid,
                    target().getGameProfile().name(),
                    "ender",
                    "remove_stack",
                    slot,
                    removed.copy()
            );
        }
        target().getEnderChestInventory().markDirty();
        return removed;
    }

    @Override
    public ItemStack removeStack(int slot) {
        if (slot >= 27 || !isEditable()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = target().getEnderChestInventory().removeStack(slot);
        if (!removed.isEmpty()) {
            this.guiService.moderationManager().recordSingleRemoval(
                    this.viewer,
                    this.targetUuid,
                    target().getGameProfile().name(),
                    "ender",
                    "remove_stack",
                    slot,
                    removed.copy()
            );
        }
        target().getEnderChestInventory().markDirty();
        return removed;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot >= 27) {
            this.controls[slot] = stack;
            return;
        }
        if (!isEditable()) {
            return;
        }
        target().getEnderChestInventory().setStack(slot, stack);
        target().getEnderChestInventory().markDirty();
    }

    @Override
    public void markDirty() {
        if (isOnline()) {
            target().getEnderChestInventory().markDirty();
        }
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return isOnline() || this.offlineEnder != null;
    }

    @Override
    public void clear() {
        if (isEditable()) {
            target().getEnderChestInventory().clear();
            target().getEnderChestInventory().markDirty();
        }
    }

    public boolean isEditable() {
        return isOnline();
    }

    public boolean loadedOfflineData() {
        return this.loadedOfflineData;
    }

    private ServerPlayerEntity target() {
        return ServerAccess.server(this.viewer).getPlayerManager().getPlayer(this.targetUuid);
    }

    private boolean isOnline() {
        return target() != null;
    }
}
