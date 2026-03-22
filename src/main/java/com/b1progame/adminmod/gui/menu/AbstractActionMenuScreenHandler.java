package com.b1progame.adminmod.gui.menu;

import com.b1progame.adminmod.gui.GuiItemFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

public abstract class AbstractActionMenuScreenHandler extends GenericContainerScreenHandler {
    protected final ServerPlayerEntity viewer;
    protected final SimpleInventory menuInventory;
    private final int rows;

    protected AbstractActionMenuScreenHandler(int syncId, PlayerInventory playerInventory, int rows) {
        super(typeForRows(rows), syncId, playerInventory, new SimpleInventory(rows * 9), rows);
        this.viewer = (ServerPlayerEntity) playerInventory.player;
        this.menuInventory = (SimpleInventory) this.getInventory();
        this.rows = rows;
        fillFiller();
    }

    protected abstract void buildMenu();

    protected abstract void onMenuSlotClick(int slot);

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        int topSize = this.rows * 9;
        if (slotIndex >= 0 && slotIndex < topSize) {
            onMenuSlotClick(slotIndex);
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    protected void fillFiller() {
        for (int i = 0; i < this.menuInventory.size(); i++) {
            this.menuInventory.setStack(i, GuiItemFactory.filler());
        }
    }

    private static ScreenHandlerType<?> typeForRows(int rows) {
        return switch (rows) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };
    }
}
