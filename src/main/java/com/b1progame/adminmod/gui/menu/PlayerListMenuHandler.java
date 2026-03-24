package com.b1progame.adminmod.gui.menu;

import com.b1progame.adminmod.gui.AdminGuiService;
import com.b1progame.adminmod.gui.GuiItemFactory;
import com.b1progame.adminmod.gui.browser.PlayerBrowserEntry;
import com.b1progame.adminmod.gui.browser.PlayerBrowserMode;
import com.b1progame.adminmod.gui.browser.PlayerBrowserSortMode;
import com.b1progame.adminmod.gui.browser.PlayerBrowserState;
import com.b1progame.adminmod.util.ServerAccess;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PlayerListMenuHandler extends AbstractActionMenuScreenHandler {
    private static final List<Integer> PLAYER_SLOTS = List.of(
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    );

    private static final int SLOT_BACK = 45;
    private static final int SLOT_PREV = 46;
    private static final int SLOT_NEXT = 52;
    private static final int SLOT_MODE = 47;
    private static final int SLOT_SORT = 48;
    private static final int SLOT_SEARCH = 49;
    private static final int SLOT_CLEAR_SEARCH = 50;
    private static final int SLOT_REFRESH = 51;
    private static final int SLOT_MAIL = 53;

    private final AdminGuiService guiService;
    private final Map<Integer, UUID> entryBySlot = new HashMap<>();
    private int maxPage = 0;

    public PlayerListMenuHandler(int syncId, PlayerInventory playerInventory, AdminGuiService guiService) {
        super(syncId, playerInventory, 6);
        this.guiService = guiService;
        buildMenu();
    }

    @Override
    protected void buildMenu() {
        PlayerBrowserState state = this.guiService.browserSessions().getOrCreate(this.viewer.getUuid());
        List<PlayerBrowserEntry> all = this.guiService.playerDataRepository().loadEntries(ServerAccess.server(this.viewer));
        List<PlayerBrowserEntry> filtered = applyFilterAndSort(all, state);
        List<BrowserRow> displayRows = buildDisplayRows(filtered, state.mode);
        this.entryBySlot.clear();

        int pageSize = PLAYER_SLOTS.size();
        this.maxPage = displayRows.isEmpty() ? 0 : (displayRows.size() - 1) / pageSize;
        state.page = Math.min(Math.max(state.page, 0), this.maxPage);

        int from = state.page * pageSize;
        int to = Math.min(from + pageSize, displayRows.size());
        List<BrowserRow> pageEntries = displayRows.subList(from, to);

        for (int i = 0; i < pageEntries.size(); i++) {
            BrowserRow row = pageEntries.get(i);
            int slot = PLAYER_SLOTS.get(i);
            if (row.separator) {
                this.menuInventory.setStack(slot, GuiItemFactory.button(
                        Items.RED_STAINED_GLASS_PANE,
                        Text.literal("Offline Players").formatted(Formatting.RED),
                        List.of(Text.literal("Separator"))
                ));
                continue;
            }
            PlayerBrowserEntry entry = row.entry;
            this.entryBySlot.put(slot, entry.uuid);
            this.menuInventory.setStack(slot, GuiItemFactory.playerHead(entry.profile, entry.name, buildLore(entry)));
        }

        this.menuInventory.setStack(SLOT_BACK, GuiItemFactory.backButton(this.guiService.configManager()));
        this.menuInventory.setStack(SLOT_PREV, GuiItemFactory.button(
                Items.ARROW,
                Text.literal("Previous").formatted(Formatting.YELLOW),
                List.of(Text.literal("Page " + (state.page + 1) + " / " + (this.maxPage + 1)))
        ));
        this.menuInventory.setStack(SLOT_NEXT, GuiItemFactory.button(
                Items.SPECTRAL_ARROW,
                Text.literal("Next").formatted(Formatting.YELLOW),
                List.of(Text.literal("Page " + (state.page + 1) + " / " + (this.maxPage + 1)))
        ));
        this.menuInventory.setStack(SLOT_MODE, GuiItemFactory.button(
                Items.COMPARATOR,
                Text.literal("List Mode").formatted(Formatting.AQUA),
                List.of(Text.literal(
                        switch (state.mode) {
                            case ONLINE -> "Online Players";
                            case OFFLINE -> "Offline Players";
                            case ALL -> "All Players";
                        }
                ))
        ));
        this.menuInventory.setStack(SLOT_SORT, GuiItemFactory.button(
                Items.HOPPER,
                Text.literal("Sorting").formatted(Formatting.BLUE),
                List.of(sortLabel(state.sortMode))
        ));
        this.menuInventory.setStack(SLOT_SEARCH, GuiItemFactory.button(
                Items.NAME_TAG,
                Text.literal("Search").formatted(Formatting.GOLD),
                List.of(Text.literal("Current filter: " + (state.search.isBlank() ? "-" : state.search)))
        ));
        this.menuInventory.setStack(SLOT_CLEAR_SEARCH, GuiItemFactory.button(
                Items.BARRIER,
                Text.literal("Clear Search").formatted(Formatting.RED),
                List.of(Text.literal("Remove current filter"))
        ));
        this.menuInventory.setStack(SLOT_REFRESH, GuiItemFactory.button(
                Items.SPYGLASS,
                Text.literal("Refresh").formatted(Formatting.AQUA),
                List.of(Text.literal("Reload player list"))
        ));
        int pendingMail = this.guiService.moderationManager().pendingStaffMailCount();
        this.menuInventory.setStack(SLOT_MAIL, GuiItemFactory.button(
                pendingMail > 0 ? Items.WRITABLE_BOOK : Items.BOOK,
                Text.literal("Staff Mail").formatted(pendingMail > 0 ? Formatting.GOLD : Formatting.GRAY),
                List.of(Text.literal("Pending: " + pendingMail), Text.literal("Open admin notification mailbox"))
        ));
    }

    @Override
    protected void onMenuSlotClick(int slot) {
        PlayerBrowserState state = this.guiService.browserSessions().getOrCreate(this.viewer.getUuid());
        if (slot == SLOT_BACK) {
            this.guiService.openMain(this.viewer);
            return;
        }
        if (slot == SLOT_PREV) {
            state.page = Math.max(0, state.page - 1);
            this.guiService.openPlayerList(this.viewer);
            return;
        }
        if (slot == SLOT_NEXT) {
            state.page = Math.min(this.maxPage, state.page + 1);
            this.guiService.openPlayerList(this.viewer);
            return;
        }
        if (slot == SLOT_MODE) {
            state.mode = switch (state.mode) {
                case ONLINE -> PlayerBrowserMode.OFFLINE;
                case OFFLINE -> PlayerBrowserMode.ALL;
                case ALL -> PlayerBrowserMode.ONLINE;
            };
            state.page = 0;
            this.guiService.openPlayerList(this.viewer);
            return;
        }
        if (slot == SLOT_SORT) {
            state.sortMode = nextSort(state.sortMode);
            state.page = 0;
            this.guiService.openPlayerList(this.viewer);
            return;
        }
        if (slot == SLOT_SEARCH) {
            this.guiService.openPlayerSearchInput(this.viewer);
            return;
        }
        if (slot == SLOT_CLEAR_SEARCH) {
            state.search = "";
            state.page = 0;
            this.guiService.openPlayerList(this.viewer);
            return;
        }
        if (slot == SLOT_REFRESH) {
            this.guiService.openPlayerList(this.viewer);
            return;
        }
        if (slot == SLOT_MAIL) {
            this.guiService.openStaffMail(this.viewer, 0);
            return;
        }

        UUID target = this.entryBySlot.get(slot);
        if (target != null) {
            this.guiService.openPlayerManagement(this.viewer, target);
        }
    }

    private List<String> buildLore(PlayerBrowserEntry entry) {
        List<String> lore = new ArrayList<>();
        lore.add(entry.online ? "Online" : "Offline");
        lore.add("Ping: " + (entry.ping < 0 ? "-" : entry.ping));
        lore.add("World: " + entry.worldName);
        lore.add("Dimension: " + entry.dimensionName);
        lore.add("Health: " + (entry.health < 0 ? "-" : String.format(Locale.ROOT, "%.1f", entry.health)));
        lore.add("Gamemode: " + (entry.gameMode == null ? "-" : gameModeName(entry.gameMode)));
        lore.add(entry.dataAvailable ? "Stored data: available" : "Stored data: missing");
        if (this.guiService.moderationManager().isWatchlisted(entry.uuid)) {
            lore.add("Watchlist: YES");
        }
        int score = this.guiService.moderationManager().calculateXraySuspicionScore(entry.uuid);
        if (score > 0) {
            lore.add("Xray suspicion: " + score + " (" + this.guiService.moderationManager().suspicionLabel(score) + ")");
        }
        lore.add("Click to open player page");
        return lore;
    }

    private String gameModeName(GameMode gameMode) {
        return switch (gameMode) {
            case SURVIVAL -> "Survival";
            case CREATIVE -> "Creative";
            case ADVENTURE -> "Adventure";
            case SPECTATOR -> "Spectator";
            default -> gameMode.name();
        };
    }

    private Text sortLabel(PlayerBrowserSortMode mode) {
        return switch (mode) {
            case NAME -> Text.literal("Name");
            case ONLINE_STATUS -> Text.literal("Online Status");
            case DIMENSION -> Text.literal("Dimension");
            case PING -> Text.literal("Ping");
            case HEALTH -> Text.literal("Health");
        };
    }

    private PlayerBrowserSortMode nextSort(PlayerBrowserSortMode current) {
        PlayerBrowserSortMode[] values = PlayerBrowserSortMode.values();
        int index = (current.ordinal() + 1) % values.length;
        return values[index];
    }

    private List<PlayerBrowserEntry> applyFilterAndSort(List<PlayerBrowserEntry> all, PlayerBrowserState state) {
        List<PlayerBrowserEntry> out = new ArrayList<>();
        String search = state.search == null ? "" : state.search.trim().toLowerCase(Locale.ROOT);

        for (PlayerBrowserEntry entry : all) {
            if (state.mode == PlayerBrowserMode.ONLINE && !entry.online) {
                continue;
            }
            if (state.mode == PlayerBrowserMode.OFFLINE && entry.online) {
                continue;
            }
            if (state.mode == PlayerBrowserMode.ALL) {
                // keep both
            }
            if (!this.guiService.vanishManager().canViewerSeeVanished(this.viewer)
                    && entry.online
                    && this.guiService.vanishManager().isVanished(entry.uuid)) {
                continue;
            }
            if (!search.isBlank() && !entry.name.toLowerCase(Locale.ROOT).contains(search)) {
                continue;
            }
            out.add(entry);
        }

        if (state.mode == PlayerBrowserMode.ALL) {
            out.sort(Comparator.comparing((PlayerBrowserEntry e) -> !e.online).thenComparing(comparator(state.sortMode)));
        } else {
            out.sort(comparator(state.sortMode));
        }
        return out;
    }

    private Comparator<PlayerBrowserEntry> comparator(PlayerBrowserSortMode mode) {
        return switch (mode) {
            case NAME -> Comparator.comparing((PlayerBrowserEntry e) -> e.name.toLowerCase(Locale.ROOT));
            case ONLINE_STATUS -> Comparator
                    .comparing((PlayerBrowserEntry e) -> !e.online)
                    .thenComparing(e -> e.name.toLowerCase(Locale.ROOT));
            case DIMENSION -> Comparator
                    .comparing((PlayerBrowserEntry e) -> e.dimensionName == null ? "" : e.dimensionName)
                    .thenComparing(e -> e.name.toLowerCase(Locale.ROOT));
            case PING -> Comparator
                    .comparingInt((PlayerBrowserEntry e) -> e.ping < 0 ? Integer.MAX_VALUE : e.ping)
                    .thenComparing(e -> e.name.toLowerCase(Locale.ROOT));
            case HEALTH -> Comparator
                    .comparingDouble((PlayerBrowserEntry e) -> e.health < 0 ? Double.MAX_VALUE : -e.health)
                    .thenComparing(e -> e.name.toLowerCase(Locale.ROOT));
        };
    }

    private List<BrowserRow> buildDisplayRows(List<PlayerBrowserEntry> entries, PlayerBrowserMode mode) {
        if (mode != PlayerBrowserMode.ALL || entries.isEmpty()) {
            return entries.stream().map(BrowserRow::entry).toList();
        }
        int firstOffline = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).online) {
                firstOffline = i;
                break;
            }
        }
        if (firstOffline <= 0) {
            return entries.stream().map(BrowserRow::entry).toList();
        }
        List<BrowserRow> rows = new ArrayList<>(entries.size() + 1);
        for (int i = 0; i < entries.size(); i++) {
            if (i == firstOffline) {
                rows.add(BrowserRow.divider());
            }
            rows.add(BrowserRow.entry(entries.get(i)));
        }
        return rows;
    }

    private record BrowserRow(PlayerBrowserEntry entry, boolean separator) {
        private static BrowserRow entry(PlayerBrowserEntry entry) {
            return new BrowserRow(entry, false);
        }

        private static BrowserRow divider() {
            return new BrowserRow(null, true);
        }
    }
}
