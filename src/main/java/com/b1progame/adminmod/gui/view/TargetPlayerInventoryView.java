package com.b1progame.adminmod.gui.view;

import com.b1progame.adminmod.gui.AdminGuiService;
import com.b1progame.adminmod.util.ServerAccess;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public final class TargetPlayerInventoryView implements Inventory {
    private final ServerPlayerEntity viewer;
    private final AdminGuiService guiService;
    private final UUID targetUuid;
    private final ItemStack[] controls = new ItemStack[54];
    private final SimpleInventory offlineInventory;
    private final boolean loadedOfflineData;

    public TargetPlayerInventoryView(ServerPlayerEntity viewer, AdminGuiService guiService, UUID targetUuid) {
        this.viewer = viewer;
        this.guiService = guiService;
        this.targetUuid = targetUuid;
        this.offlineInventory = this.guiService.playerDataRepository()
                .loadOfflineInventory(ServerAccess.server(viewer), targetUuid)
                .orElse(null);
        this.loadedOfflineData = this.offlineInventory != null;
    }

    @Override
    public int size() {
        return 54;
    }

    @Override
    public boolean isEmpty() {
        if (isOnline()) {
            return target().getInventory().isEmpty();
        }
        return this.offlineInventory == null || this.offlineInventory.isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        if (slot >= 41) {
            return this.controls[slot] == null ? ItemStack.EMPTY : this.controls[slot];
        }
        int mapped = mapSlot(slot);
        if (mapped < 0) {
            return ItemStack.EMPTY;
        }
        if (isOnline()) {
            return target().getInventory().getStack(mapped);
        }
        if (this.offlineInventory != null) {
            return this.offlineInventory.getStack(mapped);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (!isEditable() || slot >= 41) {
            return ItemStack.EMPTY;
        }
        int mapped = mapSlot(slot);
        if (mapped < 0) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = target().getInventory().removeStack(mapped, amount);
        if (!removed.isEmpty()) {
            this.guiService.moderationManager().recordSingleRemoval(
                    this.viewer,
                    this.targetUuid,
                    target().getGameProfile().name(),
                    "inventory",
                    "remove_stack",
                    mapped,
                    removed.copy()
            );
        }
        target().getInventory().markDirty();
        return removed;
    }

    @Override
    public ItemStack removeStack(int slot) {
        if (!isEditable() || slot >= 41) {
            return ItemStack.EMPTY;
        }
        int mapped = mapSlot(slot);
        if (mapped < 0) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = target().getInventory().removeStack(mapped);
        if (!removed.isEmpty()) {
            this.guiService.moderationManager().recordSingleRemoval(
                    this.viewer,
                    this.targetUuid,
                    target().getGameProfile().name(),
                    "inventory",
                    "remove_stack",
                    mapped,
                    removed.copy()
            );
        }
        target().getInventory().markDirty();
        return removed;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot >= 41) {
            this.controls[slot] = stack;
            return;
        }
        if (!isEditable()) {
            return;
        }
        int mapped = mapSlot(slot);
        if (mapped < 0) {
            return;
        }
        target().getInventory().setStack(mapped, stack);
        target().getInventory().markDirty();
    }

    @Override
    public void markDirty() {
        if (isOnline()) {
            target().getInventory().markDirty();
        }
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return isOnline() || this.offlineInventory != null;
    }

    @Override
    public void clear() {
        if (isEditable()) {
            target().getInventory().clear();
            target().getInventory().markDirty();
        }
    }

    public boolean isEditable() {
        return isOnline();
    }

    public boolean loadedOfflineData() {
        return this.loadedOfflineData;
    }

    private int mapSlot(int slot) {
        if (slot >= 0 && slot <= 40) {
            return slot;
        }
        return -1;
    }

    private ServerPlayerEntity target() {
        return ServerAccess.server(this.viewer).getPlayerManager().getPlayer(this.targetUuid);
    }

    private boolean isOnline() {
        return target() != null;
    }
}
