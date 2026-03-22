package com.b1progame.adminmod.gui.menu;

import com.b1progame.adminmod.gui.AdminGuiService;
import com.b1progame.adminmod.state.InventorySnapshotData;
import com.b1progame.adminmod.util.FeedbackUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerSnapshotsMenuHandler extends AbstractActionMenuScreenHandler {
    private static final List<Integer> SNAPSHOT_SLOTS = List.of(
            9, 10, 11, 12, 13, 14, 15,
            18, 19, 20, 21, 22, 23, 24,
            27, 28, 29, 30, 31, 32, 33
    );

    private static final int SLOT_CREATE_INV = 45;
    private static final int SLOT_CREATE_ENDER = 46;
    private static final int SLOT_CREATE_BOTH = 47;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_PREV = 52;
    private static final int SLOT_NEXT = 53;
    private static final int SLOT_COMPARE_INV = 48;
    private static final int SLOT_COMPARE_ENDER = 49;
    private static final int SLOT_QUICK_LATEST_PREV = 50;
    private static final int SLOT_QUICK_LATEST_CURRENT = 51;

    private static final Map<UUID, Long> SELECTED_SNAPSHOT_A = new HashMap<>();
    private static final Map<UUID, Long> SELECTED_SNAPSHOT_B = new HashMap<>();
    private final Map<Integer, Long> snapshotBySlot = new HashMap<>();

    private final AdminGuiService guiService;
    private final UUID targetUuid;
    private final int page;
    private int maxPage = 0;

    public PlayerSnapshotsMenuHandler(int syncId, PlayerInventory playerInventory, AdminGuiService guiService, UUID targetUuid, int page) {
        super(syncId, playerInventory, 6);
        this.guiService = guiService;
        this.targetUuid = targetUuid;
        this.page = Math.max(0, page);
        buildMenu();
    }

    @Override
    protected void buildMenu() {
        List<InventorySnapshotData> snapshots = this.guiService.moderationManager().listSnapshots(this.targetUuid);
        this.maxPage = snapshots.isEmpty() ? 0 : (snapshots.size() - 1) / SNAPSHOT_SLOTS.size();
        int safePage = Math.min(this.page, this.maxPage);
        int from = safePage * SNAPSHOT_SLOTS.size();
        int to = Math.min(snapshots.size(), from + SNAPSHOT_SLOTS.size());
        for (int i = from; i < to; i++) {
            InventorySnapshotData snapshot = snapshots.get(i);
            int slot = SNAPSHOT_SLOTS.get(i - from);
            this.snapshotBySlot.put(slot, snapshot.id);
            this.menuInventory.setStack(slot, com.b1progame.adminmod.gui.GuiItemFactory.button(
                    Items.BOOK,
                    Text.literal("Snapshot #" + snapshot.id).formatted(Formatting.AQUA),
                    List.of(
                            Text.literal("By: " + snapshot.createdByName),
                            Text.literal("At: " + Instant.ofEpochMilli(snapshot.createdAtEpochMillis)),
                            Text.literal("Inv entries: " + snapshot.inventoryStacks.size()),
                            Text.literal("Ender entries: " + snapshot.enderStacks.size())
                    )
            ));
        }

        this.menuInventory.setStack(SLOT_CREATE_INV, com.b1progame.adminmod.gui.GuiItemFactory.button(
                Items.CHEST, Text.literal("Snapshot Inventory").formatted(Formatting.GOLD), List.of()));
        this.menuInventory.setStack(SLOT_CREATE_ENDER, com.b1progame.adminmod.gui.GuiItemFactory.button(
                Items.ENDER_CHEST, Text.literal("Snapshot Ender").formatted(Formatting.LIGHT_PURPLE), List.of()));
        this.menuInventory.setStack(SLOT_CREATE_BOTH, com.b1progame.adminmod.gui.GuiItemFactory.button(
                Items.WRITABLE_BOOK, Text.literal("Snapshot Both").formatted(Formatting.GREEN), List.of()));
        this.menuInventory.setStack(SLOT_PREV, com.b1progame.adminmod.gui.GuiItemFactory.button(Items.ARROW, Text.literal("Previous").formatted(Formatting.YELLOW), List.of()));
        this.menuInventory.setStack(SLOT_NEXT, com.b1progame.adminmod.gui.GuiItemFactory.button(Items.SPECTRAL_ARROW, Text.literal("Next").formatted(Formatting.YELLOW), List.of()));
        this.menuInventory.setStack(SLOT_COMPARE_INV, com.b1progame.adminmod.gui.GuiItemFactory.button(
                Items.IRON_INGOT, Text.literal("Compare Selected (Inv)").formatted(Formatting.YELLOW), List.of(Text.literal("Select two snapshots first"))
        ));
        this.menuInventory.setStack(SLOT_COMPARE_ENDER, com.b1progame.adminmod.gui.GuiItemFactory.button(
                Items.ENDER_EYE, Text.literal("Compare Selected (Ender)").formatted(Formatting.LIGHT_PURPLE), List.of(Text.literal("Select two snapshots first"))
        ));
        this.menuInventory.setStack(SLOT_QUICK_LATEST_PREV, com.b1progame.adminmod.gui.GuiItemFactory.button(
                Items.CLOCK, Text.literal("Quick: Latest vs Previous").formatted(Formatting.AQUA), List.of()
        ));
        this.menuInventory.setStack(SLOT_QUICK_LATEST_CURRENT, com.b1progame.adminmod.gui.GuiItemFactory.button(
                Items.COMPASS, Text.literal("Quick: Latest vs Current").formatted(Formatting.GREEN), List.of()
        ));
        this.menuInventory.setStack(SLOT_BACK, com.b1progame.adminmod.gui.GuiItemFactory.backButton(this.guiService.configManager()));
    }

    @Override
    protected void onMenuSlotClick(int slot) {
        if (slot == SLOT_BACK) {
            this.guiService.openPlayerManagement(this.viewer, this.targetUuid);
            return;
        }
        if (slot == SLOT_PREV) {
            this.guiService.openPlayerSnapshots(this.viewer, this.targetUuid, Math.max(0, this.page - 1));
            return;
        }
        if (slot == SLOT_NEXT) {
            this.guiService.openPlayerSnapshots(this.viewer, this.targetUuid, Math.min(this.maxPage, this.page + 1));
            return;
        }
        if (slot == SLOT_CREATE_INV || slot == SLOT_CREATE_ENDER || slot == SLOT_CREATE_BOTH) {
            boolean inv = slot == SLOT_CREATE_INV || slot == SLOT_CREATE_BOTH;
            boolean ender = slot == SLOT_CREATE_ENDER || slot == SLOT_CREATE_BOTH;
            String name = this.targetUuid.toString();
            var history = this.guiService.moderationManager().getPlayerHistory(this.targetUuid);
            if (history != null && history.lastKnownName != null && !history.lastKnownName.isBlank()) {
                name = history.lastKnownName;
            }
            this.guiService.moderationManager().createSnapshot(this.viewer, this.targetUuid, name, inv, ender);
            FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.literal("Snapshot created."));
            this.guiService.openPlayerSnapshots(this.viewer, this.targetUuid, this.page);
            return;
        }
        if (slot == SLOT_COMPARE_INV || slot == SLOT_COMPARE_ENDER) {
            Long first = SELECTED_SNAPSHOT_A.get(this.viewer.getUuid());
            Long second = SELECTED_SNAPSHOT_B.get(this.viewer.getUuid());
            if (first == null || second == null) {
                FeedbackUtil.error(this.viewer, this.guiService.configManager(), Text.literal("Select two snapshots first."));
                return;
            }
            List<String> diff = this.guiService.moderationManager().compareSnapshots(this.viewer, this.targetUuid, first, second, slot == SLOT_COMPARE_ENDER);
            for (String line : diff) {
                this.viewer.sendMessage(Text.literal(line).formatted(Formatting.GRAY), false);
            }
            return;
        }
        if (slot == SLOT_QUICK_LATEST_PREV) {
            List<String> diff = this.guiService.moderationManager().compareLatestToPrevious(this.viewer, this.targetUuid, false);
            for (String line : diff) {
                this.viewer.sendMessage(Text.literal(line).formatted(Formatting.GRAY), false);
            }
            return;
        }
        if (slot == SLOT_QUICK_LATEST_CURRENT) {
            List<String> diff = this.guiService.moderationManager().compareLatestToCurrent(this.viewer, this.targetUuid, false);
            for (String line : diff) {
                this.viewer.sendMessage(Text.literal(line).formatted(Formatting.GRAY), false);
            }
            return;
        }
        Long selected = this.snapshotBySlot.get(slot);
        if (selected != null) {
            Long first = SELECTED_SNAPSHOT_A.get(this.viewer.getUuid());
            if (first == null || first.longValue() == selected) {
                SELECTED_SNAPSHOT_A.put(this.viewer.getUuid(), selected);
                FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.literal("Selected snapshot A: #" + selected));
            } else {
                SELECTED_SNAPSHOT_B.put(this.viewer.getUuid(), selected);
                FeedbackUtil.success(this.viewer, this.guiService.configManager(), Text.literal("Selected snapshot B: #" + selected));
            }
        }
    }
}
