package com.b1progame.adminmod.gui.menu;

import com.b1progame.adminmod.gui.AdminGuiService;
import com.b1progame.adminmod.gui.GuiItemFactory;
import com.b1progame.adminmod.state.RollbackEntryData;
import com.b1progame.adminmod.util.FeedbackUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RollbackLiteMenuHandler extends AbstractActionMenuScreenHandler {
    private static final List<Integer> ENTRY_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    );
    private static final int SLOT_BACK = 49;
    private static final int SLOT_PREV = 52;
    private static final int SLOT_NEXT = 53;

    private static final Map<UUID, Long> PENDING_CONFIRM = new HashMap<>();

    private final AdminGuiService guiService;
    private final UUID targetUuid;
    private final int page;
    private int maxPage = 0;
    private final Map<Integer, Long> entryIdsBySlot = new HashMap<>();

    public RollbackLiteMenuHandler(int syncId, PlayerInventory playerInventory, AdminGuiService guiService, UUID targetUuid, int page) {
        super(syncId, playerInventory, 6);
        this.guiService = guiService;
        this.targetUuid = targetUuid;
        this.page = Math.max(0, page);
        buildMenu();
    }

    @Override
    protected void buildMenu() {
        List<RollbackEntryData> entries = this.guiService.moderationManager().listRollbackEntries(this.targetUuid, 100);
        this.maxPage = entries.isEmpty() ? 0 : (entries.size() - 1) / ENTRY_SLOTS.size();
        int safePage = Math.min(this.page, this.maxPage);
        int from = safePage * ENTRY_SLOTS.size();
        int to = Math.min(entries.size(), from + ENTRY_SLOTS.size());
        for (int i = from; i < to; i++) {
            RollbackEntryData entry = entries.get(i);
            int slot = ENTRY_SLOTS.get(i - from);
            this.entryIdsBySlot.put(slot, entry.id);
            this.menuInventory.setStack(slot, GuiItemFactory.button(
                    entry.rolledBack ? Items.GRAY_DYE : Items.HONEY_BOTTLE,
                    Text.literal("Rollback #" + entry.id).formatted(entry.rolledBack ? Formatting.GRAY : Formatting.YELLOW),
                    List.of(
                            Text.literal("Type: " + entry.actionType + " (" + entry.inventoryType + ")"),
                            Text.literal("By: " + entry.actorName),
                            Text.literal("Removed stacks: " + entry.removedStacks.size()),
                            Text.literal("Applied: " + entry.rolledBack),
                            Text.literal(entry.rolledBack ? "Already applied" : "Click twice to confirm apply")
                    )
            ));
        }
        this.menuInventory.setStack(SLOT_PREV, GuiItemFactory.button(Items.ARROW, Text.literal("Previous").formatted(Formatting.YELLOW), List.of()));
        this.menuInventory.setStack(SLOT_NEXT, GuiItemFactory.button(Items.SPECTRAL_ARROW, Text.literal("Next").formatted(Formatting.YELLOW), List.of()));
        this.menuInventory.setStack(SLOT_BACK, GuiItemFactory.backButton(this.guiService.configManager()));
    }

    @Override
    protected void onMenuSlotClick(int slot) {
        if (slot == SLOT_BACK) {
            this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_PREV) {
            this.guiService.openRollbackLite(this.viewer, this.targetUuid, Math.max(0, this.page - 1));
            return;
        }
        if (slot == SLOT_NEXT) {
            this.guiService.openRollbackLite(this.viewer, this.targetUuid, Math.min(this.maxPage, this.page + 1));
            return;
        }
        Long entryId = this.entryIdsBySlot.get(slot);
        if (entryId == null) {
            return;
        }
        Long pending = PENDING_CONFIRM.get(this.viewer.getUuid());
        if (pending == null || pending.longValue() != entryId) {
            PENDING_CONFIRM.put(this.viewer.getUuid(), entryId);
            FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.literal("Click the same rollback again to confirm."));
            return;
        }
        PENDING_CONFIRM.remove(this.viewer.getUuid());
        var result = this.guiService.moderationManager().applyRollback(this.viewer, entryId);
        if (!result.success()) {
            FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.literal(result.message()));
            return;
        }
        FeedbackUtil.success(this.viewer, this.guiService.configManager(),
                Text.literal(result.message() + " restored=" + result.restoredCount() + " dropped=" + result.droppedCount()));
        this.guiService.openRollbackLite(this.viewer, this.targetUuid, this.page);
    }
}
