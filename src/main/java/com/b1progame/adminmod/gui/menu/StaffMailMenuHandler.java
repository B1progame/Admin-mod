package com.b1progame.adminmod.gui.menu;

import com.b1progame.adminmod.gui.AdminGuiService;
import com.b1progame.adminmod.gui.GuiItemFactory;
import com.b1progame.adminmod.state.StaffMailEntryData;
import com.b1progame.adminmod.util.DurationParser;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.Instant;
import java.util.List;

public final class StaffMailMenuHandler extends AbstractActionMenuScreenHandler {
    private static final List<Integer> ENTRY_SLOTS = List.of(
            9, 10, 11, 12, 13, 14, 15,
            18, 19, 20, 21, 22, 23, 24,
            27, 28, 29, 30, 31, 32, 33,
            36, 37, 38, 39, 40, 41, 42
    );
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_CLEAR = 51;
    private static final int SLOT_NEXT = 53;

    private final AdminGuiService guiService;
    private final int page;
    private int maxPage = 0;

    public StaffMailMenuHandler(int syncId, PlayerInventory playerInventory, AdminGuiService guiService, int page) {
        super(syncId, playerInventory, 6);
        this.guiService = guiService;
        this.page = Math.max(0, page);
        buildMenu();
    }

    @Override
    protected void buildMenu() {
        List<StaffMailEntryData> entries = this.guiService.moderationManager().listPendingStaffMail(500);
        int total = entries.size();
        this.maxPage = total == 0 ? 0 : (total - 1) / ENTRY_SLOTS.size();
        int safePage = Math.min(this.page, this.maxPage);
        int from = safePage * ENTRY_SLOTS.size();
        int to = Math.min(total, from + ENTRY_SLOTS.size());
        for (int i = from; i < to; i++) {
            StaffMailEntryData entry = entries.get(i);
            int slot = ENTRY_SLOTS.get(i - from);
            long ageMillis = Math.max(0L, System.currentTimeMillis() - entry.createdAtEpochMillis);
            this.menuInventory.setStack(slot, GuiItemFactory.button(
                    Items.PAPER,
                    Text.literal(entry.category == null || entry.category.isBlank() ? "mail" : entry.category).formatted(Formatting.GOLD),
                    List.of(
                            Text.literal(entry.message == null || entry.message.isBlank() ? "-" : entry.message),
                            Text.literal("At: " + Instant.ofEpochMilli(entry.createdAtEpochMillis)),
                            Text.literal("Age: " + DurationParser.formatMillis(ageMillis))
                    )
            ));
        }
        this.menuInventory.setStack(SLOT_PREV, GuiItemFactory.button(Items.ARROW, Text.literal("Previous").formatted(Formatting.YELLOW), List.of()));
        this.menuInventory.setStack(SLOT_NEXT, GuiItemFactory.button(Items.SPECTRAL_ARROW, Text.literal("Next").formatted(Formatting.YELLOW), List.of()));
        this.menuInventory.setStack(SLOT_CLEAR, GuiItemFactory.button(
                Items.BARRIER,
                Text.literal("Clear Mail").formatted(Formatting.RED),
                List.of(Text.literal("Remove all pending admin mail entries"))
        ));
        this.menuInventory.setStack(SLOT_BACK, GuiItemFactory.backButton(this.guiService.configManager()));
    }

    @Override
    protected void onMenuSlotClick(int slot) {
        if (slot == SLOT_BACK) {
            this.guiService.openPlayerList(this.viewer);
            return;
        }
        if (slot == SLOT_PREV) {
            this.guiService.openStaffMail(this.viewer, Math.max(0, this.page - 1));
            return;
        }
        if (slot == SLOT_NEXT) {
            this.guiService.openStaffMail(this.viewer, Math.min(this.maxPage, this.page + 1));
            return;
        }
        if (slot == SLOT_CLEAR) {
            int cleared = this.guiService.moderationManager().clearPendingStaffMail(this.viewer);
            this.viewer.sendMessage(Text.literal("Cleared " + cleared + " staff mail entries.").formatted(Formatting.GREEN), false);
            this.guiService.openStaffMail(this.viewer, 0);
        }
    }
}
