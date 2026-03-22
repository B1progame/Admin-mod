package com.b1progame.adminmod.gui.menu;

import com.b1progame.adminmod.gui.AdminGuiService;
import com.b1progame.adminmod.gui.GuiItemFactory;
import com.b1progame.adminmod.state.CommandHistoryEntryData;
import com.b1progame.adminmod.state.ModerationActionData;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PlayerActionHistoryMenuHandler extends AbstractActionMenuScreenHandler {
    private static final List<Integer> ENTRY_SLOTS = List.of(
            9, 10, 11, 12, 13, 14, 15,
            18, 19, 20, 21, 22, 23, 24,
            27, 28, 29, 30, 31, 32, 33,
            36, 37, 38, 39, 40, 41, 42
    );
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_NEXT = 53;

    private final AdminGuiService guiService;
    private final UUID targetUuid;
    private final int page;
    private int maxPage = 0;

    public PlayerActionHistoryMenuHandler(int syncId, PlayerInventory playerInventory, AdminGuiService guiService, UUID targetUuid, int page) {
        super(syncId, playerInventory, 6);
        this.guiService = guiService;
        this.targetUuid = targetUuid;
        this.page = Math.max(0, page);
        buildMenu();
    }

    @Override
    protected void buildMenu() {
        List<ModerationActionData> actions = this.guiService.moderationManager().listActionHistory(this.targetUuid);
        List<CommandHistoryEntryData> commands = this.guiService.moderationManager().listCommandHistory(this.targetUuid, 200);
        int total = actions.size() + commands.size();
        this.maxPage = total == 0 ? 0 : (total - 1) / ENTRY_SLOTS.size();
        int safePage = Math.min(this.page, this.maxPage);
        int from = safePage * ENTRY_SLOTS.size();
        int to = Math.min(total, from + ENTRY_SLOTS.size());
        for (int i = from; i < to; i++) {
            int slot = ENTRY_SLOTS.get(i - from);
            if (i < actions.size()) {
                ModerationActionData action = actions.get(i);
                this.menuInventory.setStack(slot, GuiItemFactory.button(
                        Items.PAPER,
                        Text.literal(action.actionType).formatted(Formatting.GOLD),
                        List.of(
                                Text.literal("By: " + action.actorName),
                                Text.literal("At: " + Instant.ofEpochMilli(action.createdAtEpochMillis)),
                                Text.literal("Details: " + (action.details == null || action.details.isBlank() ? "-" : action.details))
                        )
                ));
                continue;
            }
            CommandHistoryEntryData command = commands.get(i - actions.size());
            this.menuInventory.setStack(slot, GuiItemFactory.button(
                    Items.WRITABLE_BOOK,
                    Text.literal("command").formatted(Formatting.AQUA),
                    List.of(
                            Text.literal("At: " + Instant.ofEpochMilli(command.createdAtEpochMillis)),
                            Text.literal(command.command)
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
            this.guiService.openPlayerActionHistory(this.viewer, this.targetUuid, Math.max(0, this.page - 1));
            return;
        }
        if (slot == SLOT_NEXT) {
            this.guiService.openPlayerActionHistory(this.viewer, this.targetUuid, Math.min(this.maxPage, this.page + 1));
        }
    }
}
