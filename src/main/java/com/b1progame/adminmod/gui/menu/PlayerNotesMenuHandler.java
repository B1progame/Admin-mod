package com.b1progame.adminmod.gui.menu;

import com.b1progame.adminmod.gui.AdminGuiService;
import com.b1progame.adminmod.gui.GuiItemFactory;
import com.b1progame.adminmod.state.ModerationNoteData;
import com.b1progame.adminmod.util.FeedbackUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayerNotesMenuHandler extends AbstractActionMenuScreenHandler {
    private static final List<Integer> NOTE_SLOTS = List.of(
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    );
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_NEXT = 53;
    private static final int SLOT_ADD_HELP = 4;

    private final AdminGuiService guiService;
    private final UUID targetUuid;
    private final int page;
    private final List<Integer> noteIds = new ArrayList<>();
    private int maxPage = 0;

    public PlayerNotesMenuHandler(int syncId, PlayerInventory playerInventory, AdminGuiService guiService, UUID targetUuid, int page) {
        super(syncId, playerInventory, 6);
        this.guiService = guiService;
        this.targetUuid = targetUuid;
        this.page = Math.max(0, page);
        buildMenu();
    }

    @Override
    protected void buildMenu() {
        this.menuInventory.setStack(SLOT_ADD_HELP, GuiItemFactory.button(
                Items.WRITABLE_BOOK,
                "Add Note",
                Formatting.YELLOW,
                List.of("Use command:", "/pnote add <player> <text>")
        ));

        List<ModerationNoteData> notes = this.guiService.moderationManager().listNotes(this.targetUuid);
        int pageSize = NOTE_SLOTS.size();
        this.maxPage = notes.isEmpty() ? 0 : (notes.size() - 1) / pageSize;
        int safePage = Math.min(this.page, this.maxPage);
        int from = safePage * pageSize;
        int to = Math.min(from + pageSize, notes.size());
        List<ModerationNoteData> sub = notes.subList(from, to);

        for (int i = 0; i < sub.size(); i++) {
            ModerationNoteData note = sub.get(i);
            this.noteIds.add(note.id);
            this.menuInventory.setStack(NOTE_SLOTS.get(i), GuiItemFactory.button(
                    Items.PAPER,
                    "Note #" + note.id,
                    Formatting.AQUA,
                    List.of(
                            note.createdAtIso,
                            "By: " + note.authorName,
                            note.text,
                            "Click to delete"
                    )
            ));
        }

        this.menuInventory.setStack(SLOT_PREV, GuiItemFactory.button(Items.ARROW, "Previous", Formatting.YELLOW, List.of("Previous page")));
        this.menuInventory.setStack(SLOT_NEXT, GuiItemFactory.button(Items.SPECTRAL_ARROW, "Next", Formatting.YELLOW, List.of("Next page")));
        this.menuInventory.setStack(SLOT_BACK, GuiItemFactory.button(Items.BARRIER, "Back", Formatting.RED, List.of("Return to player page")));
    }

    @Override
    protected void onMenuSlotClick(int slot) {
        if (slot == SLOT_BACK) {
            this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_PREV) {
            this.guiService.openNotes(this.viewer, this.targetUuid, Math.max(this.page - 1, 0));
            return;
        }
        if (slot == SLOT_NEXT) {
            this.guiService.openNotes(this.viewer, this.targetUuid, Math.min(this.page + 1, this.maxPage));
            return;
        }
        int index = NOTE_SLOTS.indexOf(slot);
        if (index >= 0 && index < this.noteIds.size()) {
            int noteId = this.noteIds.get(index);
            boolean removed = this.guiService.moderationManager().removeNote(this.viewer, this.targetUuid, noteId);
            if (removed) {
                FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.note.removed", noteId));
                this.guiService.openNotes(this.viewer, this.targetUuid, this.page);
            } else {
                FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.translatable("message.adminmod.note.missing"));
            }
        }
    }
}
