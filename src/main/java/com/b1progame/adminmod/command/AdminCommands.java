package com.b1progame.adminmod.command;

import com.b1progame.adminmod.AdminMod;
import com.b1progame.adminmod.gui.browser.PlayerBrowserState;
import com.b1progame.adminmod.state.RollbackEntryData;
import com.b1progame.adminmod.state.StaffMailEntryData;
import com.b1progame.adminmod.state.StaffSessionData;
import com.b1progame.adminmod.state.WatchlistEntryData;
import com.b1progame.adminmod.state.ModerationNoteData;
import com.b1progame.adminmod.state.XrayRecordData;
import com.b1progame.adminmod.util.DurationParser;
import com.b1progame.adminmod.util.FeedbackUtil;
import com.b1progame.adminmod.util.PermissionUtil;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AdminCommands {
    private AdminCommands() {
    }

    public static void register(AdminMod mod) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("admin")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .executes(context -> openMain(context, mod))
                    .then(CommandManager.literal("search")
                            .then(CommandManager.argument("query", StringArgumentType.greedyString())
                                    .executes(context -> setBrowserSearch(context, mod))))
                    .then(CommandManager.literal("clearsearch")
                            .executes(context -> clearBrowserSearch(context, mod)))
                    .then(CommandManager.literal("xraytracker")
                            .then(CommandManager.literal("on").executes(context -> xrayToggle(context, mod, true)))
                            .then(CommandManager.literal("off").executes(context -> xrayToggle(context, mod, false)))
                            .then(CommandManager.literal("settings").executes(context -> xraySettings(context, mod))))
                    .then(CommandManager.literal("silentjoin")
                            .then(CommandManager.literal("on").executes(context -> silentJoin(context, mod, true)))
                            .then(CommandManager.literal("off").executes(context -> silentJoin(context, mod, false))))
                    .then(CommandManager.literal("silentdisconnect")
                            .then(CommandManager.literal("on").executes(context -> silentDisconnect(context, mod, true)))
                            .then(CommandManager.literal("off").executes(context -> silentDisconnect(context, mod, false))))
                    .then(CommandManager.literal("mail")
                            .executes(context -> openStaffMail(context, mod))
                            .then(CommandManager.literal("open").executes(context -> openStaffMail(context, mod)))
                            .then(CommandManager.literal("list").executes(context -> listStaffMail(context, mod)))
                            .then(CommandManager.literal("clear").executes(context -> clearStaffMail(context, mod))))
                    .then(CommandManager.literal("commandhistory")
                            .then(CommandManager.argument("player", StringArgumentType.word())
                                    .executes(context -> commandHistory(context, mod)))));
            dispatcher.register(CommandManager.literal("admingui")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .executes(context -> openMain(context, mod)));

            dispatcher.register(CommandManager.literal("maintenance")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .then(CommandManager.literal("on").executes(context -> maintenance(context, mod, true)))
                    .then(CommandManager.literal("off").executes(context -> maintenance(context, mod, false))));

            dispatcher.register(CommandManager.literal("vanish")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .executes(context -> vanishSelf(context, mod))
                    .then(CommandManager.literal("leavemessage")
                            .then(CommandManager.literal("on").executes(context -> vanishLeaveMessage(context, mod, true)))
                            .then(CommandManager.literal("off").executes(context -> vanishLeaveMessage(context, mod, false))))
                    .then(CommandManager.argument("player", EntityArgumentType.player()).executes(context -> vanishOther(context, mod))));

            dispatcher.register(CommandManager.literal("freeze")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .then(CommandManager.argument("player", EntityArgumentType.player()).executes(context -> freeze(context, mod, true))));

            dispatcher.register(CommandManager.literal("unfreeze")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .then(CommandManager.argument("player", EntityArgumentType.player()).executes(context -> freeze(context, mod, false))));

            dispatcher.register(CommandManager.literal("mute")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .then(CommandManager.argument("player", EntityArgumentType.player()).executes(context -> mute(context, mod, true))));

            dispatcher.register(CommandManager.literal("unmute")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .then(CommandManager.argument("player", EntityArgumentType.player()).executes(context -> mute(context, mod, false))));

            dispatcher.register(CommandManager.literal("staffchat")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .executes(context -> staffChatToggle(context, mod)));
            dispatcher.register(CommandManager.literal("sc")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .executes(context -> staffChatToggle(context, mod))
                    .then(CommandManager.argument("message", StringArgumentType.greedyString()).executes(context -> staffChatOneOff(context, mod))));

            dispatcher.register(
                    CommandManager.literal("pnote")
                            .requires(source -> hasAdminCommandPermission(source, mod))
                            .then(CommandManager.literal("add")
                                    .then(CommandManager.argument("player", EntityArgumentType.player())
                                            .then(CommandManager.argument("text", StringArgumentType.greedyString())
                                                    .executes(context -> pnoteAdd(context, mod)))))
                            .then(CommandManager.literal("list")
                                    .then(CommandManager.argument("player", EntityArgumentType.player())
                                            .executes(context -> pnoteList(context, mod))))
                            .then(CommandManager.literal("remove")
                                    .then(CommandManager.argument("player", EntityArgumentType.player())
                                            .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                                                    .executes(context -> pnoteRemove(context, mod)))))
            );

            dispatcher.register(CommandManager.literal("adminreload")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .executes(context -> reload(context, mod)));

            dispatcher.register(CommandManager.literal("ban")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                            .executes(context -> ban(context, mod, true))
                            .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                                    .executes(context -> ban(context, mod, true)))));

            dispatcher.register(CommandManager.literal("tempban")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                            .then(CommandManager.argument("duration", StringArgumentType.word())
                                    .executes(context -> tempBan(context, mod))
                                    .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                                            .executes(context -> tempBan(context, mod))))));

            dispatcher.register(CommandManager.literal("unban")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .then(CommandManager.argument("player", StringArgumentType.word())
                            .executes(context -> unban(context, mod))));

            dispatcher.register(CommandManager.literal("xraytracker")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .then(CommandManager.literal("recent").executes(context -> xrayRecent(context, mod)))
                    .then(CommandManager.literal("settings").executes(context -> xraySettings(context, mod)))
                    .then(CommandManager.literal("ore")
                            .then(CommandManager.argument("id", StringArgumentType.word())
                                    .then(CommandManager.literal("on").executes(context -> xrayOre(context, mod, true)))
                                    .then(CommandManager.literal("off").executes(context -> xrayOre(context, mod, false)))))
                    .then(CommandManager.literal("player")
                            .then(CommandManager.argument("name", StringArgumentType.word())
                                    .executes(context -> xrayPlayer(context, mod))))
                    .then(CommandManager.literal("tp")
                            .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                                    .executes(context -> xrayTeleport(context, mod)))));

            dispatcher.register(CommandManager.literal("watchlist")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .then(CommandManager.literal("add")
                            .then(CommandManager.argument("player", StringArgumentType.word())
                                    .executes(context -> watchlistAdd(context, mod))
                                    .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                                            .executes(context -> watchlistAdd(context, mod)))))
                    .then(CommandManager.literal("remove")
                            .then(CommandManager.argument("player", StringArgumentType.word())
                                    .executes(context -> watchlistRemove(context, mod))))
                    .then(CommandManager.literal("list")
                            .executes(context -> watchlistList(context, mod)))
                    .then(CommandManager.literal("info")
                            .then(CommandManager.argument("player", StringArgumentType.word())
                                    .executes(context -> watchlistInfo(context, mod)))));

            dispatcher.register(CommandManager.literal("rollbacklite")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .then(CommandManager.literal("list")
                            .then(CommandManager.argument("player", StringArgumentType.word())
                                    .executes(context -> rollbackList(context, mod))))
                    .then(CommandManager.literal("apply")
                            .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                                    .executes(context -> rollbackApply(context, mod)))));

            dispatcher.register(CommandManager.literal("staffsession")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .then(CommandManager.literal("list")
                            .executes(context -> staffSessionList(context, mod))
                            .then(CommandManager.argument("player", StringArgumentType.word())
                                    .executes(context -> staffSessionList(context, mod)))));

            dispatcher.register(CommandManager.literal("silentconnect")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .then(CommandManager.literal("on").executes(context -> silentJoin(context, mod, true)))
                    .then(CommandManager.literal("off").executes(context -> silentJoin(context, mod, false))));
            dispatcher.register(CommandManager.literal("silentdisconnect")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .then(CommandManager.literal("on").executes(context -> silentDisconnect(context, mod, true)))
                    .then(CommandManager.literal("off").executes(context -> silentDisconnect(context, mod, false))));
            dispatcher.register(CommandManager.literal("staffmail")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .executes(context -> openStaffMail(context, mod))
                    .then(CommandManager.literal("open").executes(context -> openStaffMail(context, mod)))
                    .then(CommandManager.literal("list").executes(context -> listStaffMail(context, mod)))
                    .then(CommandManager.literal("clear").executes(context -> clearStaffMail(context, mod))));
        });
    }

    private static int openMain(CommandContext<ServerCommandSource> context, AdminMod mod) {
        ServerPlayerEntity player;
        try {
            player = context.getSource().getPlayerOrThrow();
        } catch (Exception exception) {
            context.getSource().sendError(Text.translatable("message.adminmod.player_only"));
            return 0;
        }
        if (!PermissionUtil.canUseAdminGui(player, mod.configManager())) {
            context.getSource().sendError(Text.translatable("message.adminmod.no_permission"));
            return 0;
        }
        mod.guiService().openMain(player);
        return 1;
    }

    private static int setBrowserSearch(CommandContext<ServerCommandSource> context, AdminMod mod) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.translatable("message.adminmod.player_only"));
            return 0;
        }
        PlayerBrowserState state = mod.guiService().browserSessions().getOrCreate(actor.getUuid());
        state.search = StringArgumentType.getString(context, "query");
        state.page = 0;
        mod.guiService().openPlayerList(actor);
        return 1;
    }

    private static int clearBrowserSearch(CommandContext<ServerCommandSource> context, AdminMod mod) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.translatable("message.adminmod.player_only"));
            return 0;
        }
        PlayerBrowserState state = mod.guiService().browserSessions().getOrCreate(actor.getUuid());
        state.search = "";
        state.page = 0;
        mod.guiService().openPlayerList(actor);
        return 1;
    }

    private static int xrayToggle(CommandContext<ServerCommandSource> context, AdminMod mod, boolean enabled) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        mod.moderationManager().setXrayTrackerEnabled(actor, enabled);
        context.getSource().sendFeedback(() -> Text.literal("Xray tracker " + (enabled ? "enabled" : "disabled") + "."), true);
        return 1;
    }

    private static int xraySettings(CommandContext<ServerCommandSource> context, AdminMod mod) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("Only players can open xray settings."));
            return 0;
        }
        mod.guiService().openXraySettings(actor);
        return 1;
    }

    private static int xrayOre(CommandContext<ServerCommandSource> context, AdminMod mod, boolean enabled) {
        String id = StringArgumentType.getString(context, "id");
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        final String oreId = id;
        ServerPlayerEntity actor = context.getSource().getPlayer();
        mod.moderationManager().setXrayOreEnabled(actor, oreId, enabled);
        context.getSource().sendFeedback(() -> Text.literal("Xray ore " + oreId + " set to " + enabled + "."), true);
        return 1;
    }

    private static int silentJoin(CommandContext<ServerCommandSource> context, AdminMod mod, boolean enabled) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("Only players can toggle this."));
            return 0;
        }
        mod.moderationManager().setSilentJoin(actor, enabled);
        context.getSource().sendFeedback(() -> Text.literal("Silent join set to " + enabled + "."), false);
        return 1;
    }

    private static int silentDisconnect(CommandContext<ServerCommandSource> context, AdminMod mod, boolean enabled) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("Only players can toggle this."));
            return 0;
        }
        mod.moderationManager().setSilentDisconnect(actor, enabled);
        context.getSource().sendFeedback(() -> Text.literal("Silent disconnect set to " + enabled + "."), false);
        return 1;
    }

    private static int commandHistory(CommandContext<ServerCommandSource> context, AdminMod mod) {
        UUID targetUuid = resolveUuid(mod, StringArgumentType.getString(context, "player"));
        if (targetUuid == null) {
            context.getSource().sendError(Text.literal("Player not found."));
            return 0;
        }
        List<com.b1progame.adminmod.state.CommandHistoryEntryData> entries = mod.moderationManager().listCommandHistory(targetUuid, 20);
        if (entries.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No command history for " + resolveName(mod, targetUuid) + "."), false);
            return 1;
        }
        if (mod.configManager().get().logging.command_history_access && context.getSource().getPlayer() != null) {
            com.b1progame.adminmod.util.AuditLogger.sensitive(mod.configManager(),
                    com.b1progame.adminmod.util.AuditLogger.actor(context.getSource().getPlayer()) + " viewed command history for " + resolveName(mod, targetUuid));
        }
        context.getSource().sendFeedback(() -> Text.literal("Command history for " + resolveName(mod, targetUuid) + ":"), false);
        for (com.b1progame.adminmod.state.CommandHistoryEntryData entry : entries) {
            context.getSource().sendFeedback(() -> Text.literal(
                    "[" + entry.createdAtEpochMillis + "] " + entry.command
            ), false);
        }
        return 1;
    }

    private static int openStaffMail(CommandContext<ServerCommandSource> context, AdminMod mod) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            return listStaffMail(context, mod);
        }
        mod.guiService().openStaffMail(actor, 0);
        return 1;
    }

    private static int listStaffMail(CommandContext<ServerCommandSource> context, AdminMod mod) {
        List<StaffMailEntryData> entries = mod.moderationManager().listPendingStaffMail(50);
        if (entries.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No pending staff mail."), false);
            return 1;
        }
        context.getSource().sendFeedback(() -> Text.literal("Pending staff mail (" + entries.size() + " shown):"), false);
        for (StaffMailEntryData entry : entries) {
            String age = DurationParser.formatMillis(Math.max(0L, System.currentTimeMillis() - entry.createdAtEpochMillis));
            context.getSource().sendFeedback(() -> Text.literal(
                    "#" + entry.id + " [" + entry.category + "] " + entry.message + " (" + age + " ago)"
            ), false);
        }
        return 1;
    }

    private static int clearStaffMail(CommandContext<ServerCommandSource> context, AdminMod mod) {
        int cleared = mod.moderationManager().clearPendingStaffMail(context.getSource().getPlayer());
        context.getSource().sendFeedback(() -> Text.literal("Cleared " + cleared + " pending staff mail entries."), true);
        return 1;
    }

    private static int maintenance(CommandContext<ServerCommandSource> context, AdminMod mod, boolean enable) {
        if (!hasAdminCommandPermission(context.getSource(), mod)) {
            context.getSource().sendError(Text.literal("You do not have permission."));
            return 0;
        }
        ServerPlayerEntity actor = context.getSource().getPlayer();
        boolean changed = enable
                ? mod.maintenanceManager().enable(context.getSource().getServer(), actor)
                : mod.maintenanceManager().disable(context.getSource().getServer(), actor);
        if (!changed) {
            context.getSource().sendError(Text.literal("Maintenance is already " + (enable ? "enabled" : "disabled") + "."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Maintenance " + (enable ? "enabled" : "disabled") + "."), true);
        return 1;
    }

    private static int vanishSelf(CommandContext<ServerCommandSource> context, AdminMod mod) {
        ServerPlayerEntity player;
        try {
            player = context.getSource().getPlayerOrThrow();
        } catch (Exception exception) {
            context.getSource().sendError(Text.literal("Only players can toggle self vanish."));
            return 0;
        }
        if (!PermissionUtil.canUseAdminGui(player, mod.configManager())) {
            context.getSource().sendError(Text.literal("You do not have permission to use vanish."));
            return 0;
        }
        boolean vanished = mod.vanishManager().toggleVanish(player, player);
        FeedbackUtil.success(player, mod.configManager(), vanished ? "Vanish enabled." : "Vanish disabled.");
        return 1;
    }

    private static int vanishOther(CommandContext<ServerCommandSource> context, AdminMod mod) {
        ServerPlayerEntity target = getPlayerArgument(context, "player");
        if (target == null) {
            return 0;
        }
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (!hasAdminCommandPermission(context.getSource(), mod)) {
            context.getSource().sendError(Text.literal("You do not have permission to toggle vanish."));
            return 0;
        }
        boolean vanished = mod.vanishManager().toggleVanish(actor, target);
        context.getSource().sendFeedback(() -> Text.literal(
                (vanished ? "Enabled" : "Disabled") + " vanish for " + target.getGameProfile().name() + "."
        ), true);
        return 1;
    }

    private static int vanishLeaveMessage(CommandContext<ServerCommandSource> context, AdminMod mod, boolean enabled) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("Only players can toggle this."));
            return 0;
        }
        mod.vanishManager().setLeaveMessageEnabled(actor, enabled);
        context.getSource().sendFeedback(() -> Text.literal("Vanish leave-message set to " + enabled + "."), false);
        return 1;
    }

    private static int freeze(CommandContext<ServerCommandSource> context, AdminMod mod, boolean enable) {
        if (!hasAdminCommandPermission(context.getSource(), mod)) {
            context.getSource().sendError(Text.literal("You do not have permission."));
            return 0;
        }
        ServerPlayerEntity target = getPlayerArgument(context, "player");
        if (target == null) {
            return 0;
        }
        ServerPlayerEntity actor = context.getSource().getPlayer();
        boolean changed = enable
                ? mod.moderationManager().freeze(actor, target)
                : mod.moderationManager().unfreeze(actor, target);
        if (!changed) {
            context.getSource().sendError(Text.literal("Target is already " + (enable ? "frozen" : "unfrozen") + "."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Target " + target.getGameProfile().name() + " is now " + (enable ? "frozen" : "unfrozen") + "."), true);
        return 1;
    }

    private static int mute(CommandContext<ServerCommandSource> context, AdminMod mod, boolean enable) {
        if (!hasAdminCommandPermission(context.getSource(), mod)) {
            context.getSource().sendError(Text.literal("You do not have permission."));
            return 0;
        }
        ServerPlayerEntity target = getPlayerArgument(context, "player");
        if (target == null) {
            return 0;
        }
        ServerPlayerEntity actor = context.getSource().getPlayer();
        boolean changed = enable
                ? mod.moderationManager().mute(actor, target)
                : mod.moderationManager().unmute(actor, target);
        if (!changed) {
            context.getSource().sendError(Text.literal("Target is already " + (enable ? "muted" : "unmuted") + "."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Target " + target.getGameProfile().name() + " is now " + (enable ? "muted" : "unmuted") + "."), true);
        return 1;
    }

    private static int staffChatToggle(CommandContext<ServerCommandSource> context, AdminMod mod) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("Console cannot toggle staff chat mode."));
            return 0;
        }
        if (!hasAdminCommandPermission(context.getSource(), mod)) {
            context.getSource().sendError(Text.literal("You do not have permission."));
            return 0;
        }
        boolean enabled = mod.moderationManager().toggleStaffChat(actor);
        FeedbackUtil.success(actor, mod.configManager(), "Staff chat mode: " + (enabled ? "ON" : "OFF"));
        return 1;
    }

    private static int staffChatOneOff(CommandContext<ServerCommandSource> context, AdminMod mod) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("Console cannot send staff chat through this command."));
            return 0;
        }
        if (!hasAdminCommandPermission(context.getSource(), mod)) {
            context.getSource().sendError(Text.literal("You do not have permission."));
            return 0;
        }
        String message = StringArgumentType.getString(context, "message");
        mod.moderationManager().sendStaffChat(actor, message);
        return 1;
    }

    private static int pnoteAdd(CommandContext<ServerCommandSource> context, AdminMod mod) {
        if (!hasAdminCommandPermission(context.getSource(), mod)) {
            context.getSource().sendError(Text.literal("You do not have permission."));
            return 0;
        }
        ServerPlayerEntity target = getPlayerArgument(context, "player");
        if (target == null) {
            return 0;
        }
        String text = StringArgumentType.getString(context, "text");
        ServerPlayerEntity actor = context.getSource().getPlayer();
        ModerationNoteData note = mod.moderationManager().addNote(actor, target.getUuid(), text);
        context.getSource().sendFeedback(() -> Text.literal("Added note #" + note.id + " for " + target.getGameProfile().name() + "."), false);
        return 1;
    }

    private static int pnoteList(CommandContext<ServerCommandSource> context, AdminMod mod) {
        if (!hasAdminCommandPermission(context.getSource(), mod)) {
            context.getSource().sendError(Text.literal("You do not have permission."));
            return 0;
        }
        ServerPlayerEntity target = getPlayerArgument(context, "player");
        if (target == null) {
            return 0;
        }
        List<ModerationNoteData> notes = mod.moderationManager().listNotes(target.getUuid());
        if (notes.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No notes for " + target.getGameProfile().name() + "."), false);
            return 1;
        }
        context.getSource().sendFeedback(() -> Text.literal("Notes for " + target.getGameProfile().name() + ":"), false);
        for (ModerationNoteData note : notes) {
            context.getSource().sendFeedback(() -> Text.literal(
                    "#" + note.id + " [" + note.createdAtIso + "] " + note.authorName + ": " + note.text
            ), false);
        }
        return 1;
    }

    private static int pnoteRemove(CommandContext<ServerCommandSource> context, AdminMod mod) {
        if (!hasAdminCommandPermission(context.getSource(), mod)) {
            context.getSource().sendError(Text.literal("You do not have permission."));
            return 0;
        }
        ServerPlayerEntity target = getPlayerArgument(context, "player");
        if (target == null) {
            return 0;
        }
        int noteId = IntegerArgumentType.getInteger(context, "id");
        ServerPlayerEntity actor = context.getSource().getPlayer();
        boolean removed = mod.moderationManager().removeNote(actor, target.getUuid(), noteId);
        if (!removed) {
            context.getSource().sendError(Text.literal("Note #" + noteId + " not found."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Removed note #" + noteId + " for " + target.getGameProfile().name() + "."), false);
        return 1;
    }

    private static int reload(CommandContext<ServerCommandSource> context, AdminMod mod) {
        if (!hasAdminCommandPermission(context.getSource(), mod)) {
            context.getSource().sendError(Text.literal("You do not have permission."));
            return 0;
        }
        mod.reload();
        context.getSource().sendFeedback(() -> Text.literal("adminmod configuration reloaded."), true);
        return 1;
    }

    private static int ban(CommandContext<ServerCommandSource> context, AdminMod mod, boolean permanent) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        ServerPlayerEntity target = getPlayerArgument(context, "player");
        if (target == null) {
            return 0;
        }
        String reason = context.getNodes().stream().anyMatch(n -> n.getNode().getName().equals("reason"))
                ? StringArgumentType.getString(context, "reason")
                : "";
        mod.moderationManager().ban(actor, target.getUuid(), target.getGameProfile().name(), reason, permanent, 0L);
        context.getSource().sendFeedback(() -> Text.literal("Banned " + target.getGameProfile().name() + "."), true);
        return 1;
    }

    private static int tempBan(CommandContext<ServerCommandSource> context, AdminMod mod) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        ServerPlayerEntity target = getPlayerArgument(context, "player");
        if (target == null) {
            return 0;
        }
        String durationRaw = StringArgumentType.getString(context, "duration");
        var parseResult = DurationParser.parse(durationRaw);
        if (!parseResult.valid()) {
            context.getSource().sendError(Text.literal("Invalid duration: " + parseResult.error()));
            return 0;
        }
        long durationMillis = parseResult.millis();
        String reason = context.getNodes().stream().anyMatch(n -> n.getNode().getName().equals("reason"))
                ? StringArgumentType.getString(context, "reason")
                : "";
        long expiresAt = System.currentTimeMillis() + durationMillis;
        mod.moderationManager().ban(actor, target.getUuid(), target.getGameProfile().name(), reason, false, expiresAt);
        context.getSource().sendFeedback(() -> Text.literal("Temp-banned " + target.getGameProfile().name() + " for " + DurationParser.formatMillis(durationMillis) + "."), true);
        return 1;
    }

    private static int unban(CommandContext<ServerCommandSource> context, AdminMod mod) {
        String target = StringArgumentType.getString(context, "player");
        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(target);
        } catch (IllegalArgumentException exception) {
            targetUuid = mod.moderationManager().findPlayerUuidByName(target);
        }
        if (targetUuid == null) {
            context.getSource().sendError(Text.literal("Unknown player identifier."));
            return 0;
        }
        boolean removed = mod.moderationManager().unban(context.getSource().getPlayer(), targetUuid, target);
        if (!removed) {
            context.getSource().sendError(Text.literal("Player is not banned."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Unbanned " + target + "."), true);
        return 1;
    }

    private static int xrayRecent(CommandContext<ServerCommandSource> context, AdminMod mod) {
        List<XrayRecordData> records = mod.moderationManager().listRecentXrayRecords(10);
        if (records.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No xray tracker records."), false);
            return 1;
        }
        context.getSource().sendFeedback(() -> Text.literal("Recent xray tracker records:"), false);
        for (XrayRecordData record : records) {
            int score = mod.moderationManager().calculateXraySuspicionScore(UUID.fromString(record.playerUuid));
            context.getSource().sendFeedback(() -> Text.literal(
                    "#" + record.id + " " + record.playerName + " " + record.oreBlockId + " @ "
                            + record.x + " " + record.y + " " + record.z
                            + " | score=" + score + " (" + mod.moderationManager().suspicionLabel(score) + ")"
            ), false);
        }
        return 1;
    }

    private static int xrayPlayer(CommandContext<ServerCommandSource> context, AdminMod mod) {
        String name = StringArgumentType.getString(context, "name");
        UUID targetUuid = mod.moderationManager().findPlayerUuidByName(name);
        if (targetUuid == null) {
            context.getSource().sendError(Text.literal("Player not found in history."));
            return 0;
        }
        List<XrayRecordData> records = mod.moderationManager().listXrayRecordsFor(targetUuid, 10);
        if (records.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No xray records for " + name + "."), false);
            return 1;
        }
        context.getSource().sendFeedback(() -> Text.literal("Xray records for " + name + ":"), false);
        int score = mod.moderationManager().calculateXraySuspicionScore(targetUuid);
        context.getSource().sendFeedback(() -> Text.literal("Suspicion score: " + score + " (" + mod.moderationManager().suspicionLabel(score) + ")"), false);
        for (XrayRecordData record : records) {
            context.getSource().sendFeedback(() -> Text.literal(
                    "#" + record.id + " " + record.oreBlockId + " @ " + record.x + " " + record.y + " " + record.z
            ), false);
        }
        return 1;
    }

    private static int xrayTeleport(CommandContext<ServerCommandSource> context, AdminMod mod) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("Only players can teleport."));
            return 0;
        }
        int id = IntegerArgumentType.getInteger(context, "id");
        XrayRecordData record = mod.moderationManager().findXrayRecordById(id);
        if (record == null) {
            context.getSource().sendError(Text.literal("Xray record not found."));
            return 0;
        }
        if (mod.configManager().get().xray_tracker.auto_stealth_review && !mod.vanishManager().isVanished(actor.getUuid())) {
            mod.vanishManager().toggleVanish(actor, actor);
        }
        actor.teleport(actor.getEntityWorld(), record.x + 0.5D, record.y + 1.0D, record.z + 0.5D, Set.of(), actor.getYaw(), actor.getPitch(), false);
        mod.moderationManager().recordStaffAction(actor, "xray_review_tp", "record#" + id);
        context.getSource().sendFeedback(() -> Text.literal("Teleported to xray record #" + id + "."), false);
        return 1;
    }

    private static int watchlistAdd(CommandContext<ServerCommandSource> context, AdminMod mod) {
        String raw = StringArgumentType.getString(context, "player");
        String reason = context.getNodes().stream().anyMatch(n -> n.getNode().getName().equals("reason"))
                ? StringArgumentType.getString(context, "reason")
                : "Added by staff";
        UUID targetUuid = resolveUuid(mod, raw);
        if (targetUuid == null) {
            context.getSource().sendError(Text.literal("Player not found."));
            return 0;
        }
        String targetName = resolveName(mod, targetUuid);
        mod.moderationManager().addWatchlist(context.getSource().getPlayer(), targetUuid, targetName, reason);
        context.getSource().sendFeedback(() -> Text.literal("Watchlisted " + targetName + "."), true);
        return 1;
    }

    private static int watchlistRemove(CommandContext<ServerCommandSource> context, AdminMod mod) {
        UUID targetUuid = resolveUuid(mod, StringArgumentType.getString(context, "player"));
        if (targetUuid == null) {
            context.getSource().sendError(Text.literal("Player not found."));
            return 0;
        }
        boolean removed = mod.moderationManager().removeWatchlist(context.getSource().getPlayer(), targetUuid);
        if (!removed) {
            context.getSource().sendError(Text.literal("Player is not watchlisted."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Removed from watchlist."), true);
        return 1;
    }

    private static int watchlistList(CommandContext<ServerCommandSource> context, AdminMod mod) {
        List<WatchlistEntryData> entries = mod.moderationManager().listWatchlist();
        if (entries.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("Watchlist is empty."), false);
            return 1;
        }
        context.getSource().sendFeedback(() -> Text.literal("Watchlist entries:"), false);
        for (WatchlistEntryData entry : entries) {
            context.getSource().sendFeedback(() -> Text.literal(
                    entry.lastKnownName + " (" + entry.targetUuid + ") reason=" + entry.reason + " by=" + entry.actorName + " " + entry.suspicionSummary
            ), false);
        }
        return 1;
    }

    private static int watchlistInfo(CommandContext<ServerCommandSource> context, AdminMod mod) {
        UUID targetUuid = resolveUuid(mod, StringArgumentType.getString(context, "player"));
        if (targetUuid == null) {
            context.getSource().sendError(Text.literal("Player not found."));
            return 0;
        }
        WatchlistEntryData entry = mod.moderationManager().getWatchlistEntry(targetUuid);
        if (entry == null) {
            context.getSource().sendError(Text.literal("Player is not watchlisted."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Watchlist info for " + entry.lastKnownName + ":"), false);
        context.getSource().sendFeedback(() -> Text.literal("Reason: " + entry.reason), false);
        context.getSource().sendFeedback(() -> Text.literal("Added by: " + entry.actorName + " at " + entry.addedAtEpochMillis), false);
        context.getSource().sendFeedback(() -> Text.literal("Suspicion: " + entry.suspicionSummary), false);
        return 1;
    }

    private static int rollbackList(CommandContext<ServerCommandSource> context, AdminMod mod) {
        UUID targetUuid = resolveUuid(mod, StringArgumentType.getString(context, "player"));
        if (targetUuid == null) {
            context.getSource().sendError(Text.literal("Player not found."));
            return 0;
        }
        List<RollbackEntryData> entries = mod.moderationManager().listRollbackEntries(targetUuid, 15);
        if (entries.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No rollback entries."), false);
            return 1;
        }
        context.getSource().sendFeedback(() -> Text.literal("Rollback entries for " + resolveName(mod, targetUuid) + ":"), false);
        for (RollbackEntryData entry : entries) {
            context.getSource().sendFeedback(() -> Text.literal(
                    "#" + entry.id + " " + entry.actionType + " " + entry.inventoryType + " by=" + entry.actorName + " rolledBack=" + entry.rolledBack
            ), false);
        }
        return 1;
    }

    private static int rollbackApply(CommandContext<ServerCommandSource> context, AdminMod mod) {
        int id = IntegerArgumentType.getInteger(context, "id");
        var result = mod.moderationManager().applyRollback(context.getSource().getPlayer(), id);
        if (!result.success()) {
            context.getSource().sendError(Text.literal(result.message()));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal(result.message() + " restored=" + result.restoredCount() + " dropped=" + result.droppedCount()), true);
        return 1;
    }

    private static int staffSessionList(CommandContext<ServerCommandSource> context, AdminMod mod) {
        UUID filter = null;
        if (context.getNodes().stream().anyMatch(n -> n.getNode().getName().equals("player"))) {
            filter = resolveUuid(mod, StringArgumentType.getString(context, "player"));
        }
        List<StaffSessionData> sessions = mod.moderationManager().listStaffSessions(20, filter);
        if (sessions.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No staff sessions found."), false);
            return 1;
        }
        context.getSource().sendFeedback(() -> Text.literal("Recent staff sessions:"), false);
        for (StaffSessionData session : sessions) {
            long end = session.endedAtEpochMillis > 0L ? session.endedAtEpochMillis : System.currentTimeMillis();
            long durationSeconds = Math.max(0L, (end - session.startedAtEpochMillis) / 1000L);
            context.getSource().sendFeedback(() -> Text.literal(
                    "#" + session.id + " " + session.staffName + " start=" + session.startedAtEpochMillis
                            + " end=" + (session.endedAtEpochMillis > 0L ? session.endedAtEpochMillis : "active")
                            + " duration=" + durationSeconds + "s actions=" + session.actions.size()
            ), false);
        }
        return 1;
    }

    private static UUID resolveUuid(AdminMod mod, String player) {
        try {
            return UUID.fromString(player);
        } catch (IllegalArgumentException ignored) {
            return mod.moderationManager().findPlayerUuidByName(player);
        }
    }

    private static String resolveName(AdminMod mod, UUID uuid) {
        var history = mod.moderationManager().getPlayerHistory(uuid);
        if (history != null && history.lastKnownName != null && !history.lastKnownName.isBlank()) {
            return history.lastKnownName;
        }
        return uuid.toString();
    }

    private static boolean hasAdminCommandPermission(ServerCommandSource source, AdminMod mod) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return true;
        }
        return PermissionUtil.canUseAdminGui(player, mod.configManager());
    }

    private static ServerPlayerEntity getPlayerArgument(CommandContext<ServerCommandSource> context, String argument) {
        try {
            return EntityArgumentType.getPlayer(context, argument);
        } catch (CommandSyntaxException exception) {
            context.getSource().sendError(Text.literal("Target player not found."));
            return null;
        }
    }
}
