package com.b1progame.adminmod.command;

import com.b1progame.adminmod.AdminMod;
import com.b1progame.adminmod.gui.browser.PlayerBrowserState;
import com.b1progame.adminmod.heatmap.HeatmapManager;
import com.b1progame.adminmod.lagidentify.LagIdentifyManager;
import com.b1progame.adminmod.state.RollbackEntryData;
import com.b1progame.adminmod.state.LagIdentifyResultData;
import com.b1progame.adminmod.state.StaffMailEntryData;
import com.b1progame.adminmod.state.StaffSessionData;
import com.b1progame.adminmod.state.WatchlistEntryData;
import com.b1progame.adminmod.state.ModerationNoteData;
import com.b1progame.adminmod.state.XrayRecordData;
import com.b1progame.adminmod.state.XrayReplayWatchEntryData;
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
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import com.b1progame.adminmod.xrayreplay.XrayReplayManager;

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
                    .then(CommandManager.literal("vanishrejoin")
                            .then(CommandManager.literal("yes").executes(context -> vanishRejoin(context, mod, true)))
                            .then(CommandManager.literal("no").executes(context -> vanishRejoin(context, mod, false))))
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

            dispatcher.register(CommandManager.literal("sstop")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .executes(context -> scheduledStopStatus(context, mod))
                    .then(CommandManager.argument("duration", StringArgumentType.word())
                            .executes(context -> scheduledStopStart(context, mod)))
                    .then(CommandManager.literal("bossbar")
                            .then(CommandManager.literal("off").executes(context -> scheduledStopBossbar(context, mod, "off")))
                            .then(CommandManager.literal("self").executes(context -> scheduledStopBossbar(context, mod, "self")))
                            .then(CommandManager.literal("all").executes(context -> scheduledStopBossbar(context, mod, "all"))))
                    .then(CommandManager.literal("kick")
                            .then(CommandManager.literal("all").executes(context -> scheduledStopKickPolicy(context, mod, "all")))
                            .then(CommandManager.literal("non_admin").executes(context -> scheduledStopKickPolicy(context, mod, "non_admin")))
                            .then(CommandManager.literal("nobody").executes(context -> scheduledStopKickPolicy(context, mod, "nobody"))))
                    .then(CommandManager.literal("cancel")
                            .executes(context -> scheduledStopCancel(context, mod))));

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
            if (mod.heatmapManager().isFeatureEnabled()) {
                dispatcher.register(CommandManager.literal("heatmap")
                        .requires(source -> hasAdminCommandPermission(source, mod))
                        .then(CommandManager.literal("player")
                                .then(CommandManager.argument("player", StringArgumentType.word())
                                        .executes(context -> heatmapPlayer(context, mod, HeatmapManager.HeatmapMode.MOVEMENT))
                                        .then(CommandManager.argument("duration", StringArgumentType.word())
                                                .executes(context -> heatmapPlayer(context, mod, HeatmapManager.HeatmapMode.MOVEMENT)))))
                        .then(CommandManager.literal("mining")
                                .then(CommandManager.argument("player", StringArgumentType.word())
                                        .executes(context -> heatmapPlayer(context, mod, HeatmapManager.HeatmapMode.MINING))
                                        .then(CommandManager.argument("duration", StringArgumentType.word())
                                                .executes(context -> heatmapPlayer(context, mod, HeatmapManager.HeatmapMode.MINING)))))
                        .then(CommandManager.literal("ore")
                                .then(CommandManager.argument("player", StringArgumentType.word())
                                        .executes(context -> heatmapPlayer(context, mod, HeatmapManager.HeatmapMode.ORE))
                                        .then(CommandManager.argument("duration", StringArgumentType.word())
                                                .executes(context -> heatmapPlayer(context, mod, HeatmapManager.HeatmapMode.ORE)))))
                        .then(CommandManager.literal("global")
                                .then(CommandManager.argument("world", StringArgumentType.word())
                                        .executes(context -> heatmapGlobal(context, mod, HeatmapManager.HeatmapMode.MOVEMENT))
                                        .then(CommandManager.argument("duration", StringArgumentType.word())
                                                .executes(context -> heatmapGlobal(context, mod, HeatmapManager.HeatmapMode.MOVEMENT)))))
                        .then(CommandManager.literal("watchlist")
                                .executes(context -> heatmapWatchlist(context, mod, HeatmapManager.HeatmapMode.ORE))
                                .then(CommandManager.argument("duration", StringArgumentType.word())
                                        .executes(context -> heatmapWatchlist(context, mod, HeatmapManager.HeatmapMode.ORE))))
                        .then(CommandManager.literal("mode")
                                .then(CommandManager.argument("mode", StringArgumentType.word())
                                        .executes(context -> heatmapMode(context, mod))))
                        .then(CommandManager.literal("radius")
                                .then(CommandManager.argument("value", IntegerArgumentType.integer(8, 512))
                                        .executes(context -> heatmapRadius(context, mod))))
                        .then(CommandManager.literal("info")
                                .then(CommandManager.argument("player", StringArgumentType.word())
                                        .executes(context -> heatmapInfo(context, mod, HeatmapManager.HeatmapMode.ORE))
                                        .then(CommandManager.argument("duration", StringArgumentType.word())
                                                .executes(context -> heatmapInfo(context, mod, HeatmapManager.HeatmapMode.ORE)))))
                        .then(CommandManager.literal("stop").executes(context -> heatmapStop(context, mod))));
            }

            if (mod.xrayReplayManager().isFeatureEnabled()) {
                dispatcher.register(
                        CommandManager.literal("xrayreplay")
                                .requires(source -> hasAdminCommandPermission(source, mod))
                                .then(CommandManager.literal("watch")
                                        .then(CommandManager.argument("player", StringArgumentType.word())
                                                .executes(context -> xrayReplayWatch(context, mod))
                                                .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                                                        .executes(context -> xrayReplayWatch(context, mod)))))
                                .then(CommandManager.literal("unwatch")
                                        .then(CommandManager.argument("player", StringArgumentType.word())
                                                .executes(context -> xrayReplayUnwatch(context, mod))))
                                .then(CommandManager.literal("watched").executes(context -> xrayReplayWatched(context, mod)))
                                .then(CommandManager.literal("status")
                                        .then(CommandManager.argument("player", StringArgumentType.word())
                                                .executes(context -> xrayReplayStatus(context, mod))))
                                .then(CommandManager.literal("list")
                                        .then(CommandManager.argument("player", StringArgumentType.word())
                                                .executes(context -> xrayReplayList(context, mod))))
                                .then(CommandManager.literal("info")
                                        .then(CommandManager.argument("player", StringArgumentType.word())
                                                .then(CommandManager.argument("segment", IntegerArgumentType.integer(1))
                                                        .executes(context -> xrayReplayInfo(context, mod)))))
                                .then(CommandManager.literal("play")
                                        .then(CommandManager.argument("player", StringArgumentType.word())
                                                .then(CommandManager.argument("segment", IntegerArgumentType.integer(1))
                                                        .executes(context -> xrayReplayPlay(context, mod)))))
                                .then(CommandManager.literal("latest")
                                        .then(CommandManager.argument("player", StringArgumentType.word())
                                                .executes(context -> xrayReplayLatest(context, mod))))
                                .then(CommandManager.literal("stop")
                                        .executes(context -> xrayReplayStop(context, mod)))
                                .then(CommandManager.literal("delete")
                                        .then(CommandManager.argument("player", StringArgumentType.word())
                                                .then(CommandManager.argument("segment", IntegerArgumentType.integer(1))
                                                        .executes(context -> xrayReplayDelete(context, mod)))))
                );
            }

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
            dispatcher.register(CommandManager.literal("lagidentify")
                    .requires(source -> hasAdminCommandPermission(source, mod))
                    .then(CommandManager.literal("scan")
                            .executes(context -> lagIdentifyScan(context, mod))
                            .then(CommandManager.argument("scope", StringArgumentType.word())
                                    .executes(context -> lagIdentifyScan(context, mod))))
                    .then(CommandManager.literal("refresh").executes(context -> lagIdentifyScan(context, mod)))
                    .then(CommandManager.literal("stop").executes(context -> lagIdentifyStop(context, mod)))
                    .then(CommandManager.literal("results").executes(context -> lagIdentifyList(context, mod)))
                    .then(CommandManager.literal("list").executes(context -> lagIdentifyList(context, mod)))
                    .then(CommandManager.literal("clear").executes(context -> lagIdentifyClear(context, mod)))
                    .then(CommandManager.literal("settings").executes(context -> lagIdentifySettings(context, mod)))
                    .then(CommandManager.literal("info")
                            .then(CommandManager.argument("result-id", IntegerArgumentType.integer(1))
                                    .executes(context -> lagIdentifyInfo(context, mod))))
                    .then(CommandManager.literal("tp")
                            .then(CommandManager.argument("result-id", IntegerArgumentType.integer(1))
                                    .executes(context -> lagIdentifyTp(context, mod))))
                    .then(CommandManager.literal("back").executes(context -> lagIdentifyBack(context, mod))));

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

    private static int vanishRejoin(CommandContext<ServerCommandSource> context, AdminMod mod, boolean confirmed) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("Only players can use this."));
            return 0;
        }
        if (!confirmed) {
            context.getSource().sendFeedback(() -> Text.literal("Vanish reconnect canceled."), false);
            return 1;
        }
        if (!mod.vanishManager().isVanished(actor.getUuid())) {
            context.getSource().sendError(Text.literal("Enable vanish first."));
            return 0;
        }
        mod.moderationManager().beginVanishReconnectFlow(actor);
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

    private static int xrayReplayWatch(CommandContext<ServerCommandSource> context, AdminMod mod) {
        UUID targetUuid = resolveUuid(mod, StringArgumentType.getString(context, "player"));
        if (targetUuid == null) {
            context.getSource().sendError(Text.literal("Player not found."));
            return 0;
        }
        String reason = context.getNodes().stream().anyMatch(n -> n.getNode().getName().equals("reason"))
                ? StringArgumentType.getString(context, "reason")
                : "Watched for xray replay";
        String name = resolveName(mod, targetUuid);
        boolean changed = mod.xrayReplayManager().watch(context.getSource().getPlayer(), targetUuid, name, reason);
        if (!changed) {
            context.getSource().sendError(Text.literal("Player is already watched."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Xray replay watching " + name + "."), true);
        return 1;
    }

    private static int xrayReplayUnwatch(CommandContext<ServerCommandSource> context, AdminMod mod) {
        UUID targetUuid = resolveUuid(mod, StringArgumentType.getString(context, "player"));
        if (targetUuid == null) {
            context.getSource().sendError(Text.literal("Player not found."));
            return 0;
        }
        boolean changed = mod.xrayReplayManager().unwatch(context.getSource().getPlayer(), targetUuid);
        if (!changed) {
            context.getSource().sendError(Text.literal("Player is not watched."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Removed player from xray replay watch list."), true);
        return 1;
    }

    private static int xrayReplayWatched(CommandContext<ServerCommandSource> context, AdminMod mod) {
        List<XrayReplayWatchEntryData> watched = mod.xrayReplayManager().watched();
        if (watched.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No watched players."), false);
            return 1;
        }
        context.getSource().sendFeedback(() -> Text.literal("Xray replay watched players:"), false);
        for (XrayReplayWatchEntryData entry : watched) {
            context.getSource().sendFeedback(() -> Text.literal(
                    entry.lastKnownName + " (" + entry.targetUuid + ") reason=" + (entry.reason == null || entry.reason.isBlank() ? "-" : entry.reason)
            ), false);
        }
        return 1;
    }

    private static int xrayReplayStatus(CommandContext<ServerCommandSource> context, AdminMod mod) {
        UUID targetUuid = resolveUuid(mod, StringArgumentType.getString(context, "player"));
        if (targetUuid == null) {
            context.getSource().sendError(Text.literal("Player not found."));
            return 0;
        }
        boolean watched = mod.xrayReplayManager().isWatched(targetUuid);
        XrayReplayManager.SegmentMeta latest = mod.xrayReplayManager().latestSegment(targetUuid);
        context.getSource().sendFeedback(() -> Text.literal("Watched: " + watched), false);
        if (latest != null) {
            context.getSource().sendFeedback(() -> Text.literal("Latest segment: #" + latest.segmentId + " duration=" + DurationParser.formatMillis(latest.durationMillis)), false);
        } else {
            context.getSource().sendFeedback(() -> Text.literal("Latest segment: none"), false);
        }
        return 1;
    }

    private static int xrayReplayList(CommandContext<ServerCommandSource> context, AdminMod mod) {
        UUID targetUuid = resolveUuid(mod, StringArgumentType.getString(context, "player"));
        if (targetUuid == null) {
            context.getSource().sendError(Text.literal("Player not found."));
            return 0;
        }
        List<XrayReplayManager.SegmentMeta> list = mod.xrayReplayManager().listSegments(targetUuid);
        if (list.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No replay segments for player."), false);
            return 1;
        }
        context.getSource().sendFeedback(() -> Text.literal("Replay segments for " + resolveName(mod, targetUuid) + ":"), false);
        for (XrayReplayManager.SegmentMeta meta : list) {
            context.getSource().sendFeedback(() -> Text.literal(
                    "#" + meta.segmentId + " " + Instant.ofEpochMilli(meta.startEpochMillis) + " duration=" + DurationParser.formatMillis(meta.durationMillis)
                            + " samples=" + meta.movementSampleCount + " visible=" + meta.visibleBlockCount
            ), false);
        }
        return 1;
    }

    private static int xrayReplayInfo(CommandContext<ServerCommandSource> context, AdminMod mod) {
        UUID targetUuid = resolveUuid(mod, StringArgumentType.getString(context, "player"));
        if (targetUuid == null) {
            context.getSource().sendError(Text.literal("Player not found."));
            return 0;
        }
        long segment = IntegerArgumentType.getInteger(context, "segment");
        XrayReplayManager.SegmentMeta meta = mod.xrayReplayManager().segmentInfo(targetUuid, segment);
        if (meta == null) {
            context.getSource().sendError(Text.literal("Segment not found."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Segment #" + meta.segmentId + " for " + meta.playerName + ":"), false);
        context.getSource().sendFeedback(() -> Text.literal("Start: " + Instant.ofEpochMilli(meta.startEpochMillis)), false);
        context.getSource().sendFeedback(() -> Text.literal("End: " + Instant.ofEpochMilli(meta.endEpochMillis)), false);
        context.getSource().sendFeedback(() -> Text.literal("Duration: " + DurationParser.formatMillis(meta.durationMillis)), false);
        context.getSource().sendFeedback(() -> Text.literal("Samples: " + meta.movementSampleCount + ", blockEvents: " + meta.blockEventCount + ", visibleBlocks: " + meta.visibleBlockCount), false);
        context.getSource().sendFeedback(() -> Text.literal("Suspicion: " + (meta.suspicionSummary == null ? "-" : meta.suspicionSummary)), false);
        return 1;
    }

    private static int xrayReplayPlay(CommandContext<ServerCommandSource> context, AdminMod mod) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("Only players can play replays."));
            return 0;
        }
        UUID targetUuid = resolveUuid(mod, StringArgumentType.getString(context, "player"));
        if (targetUuid == null) {
            context.getSource().sendError(Text.literal("Player not found."));
            return 0;
        }
        long segment = IntegerArgumentType.getInteger(context, "segment");
        boolean started = mod.xrayReplayManager().play(actor, targetUuid, segment);
        if (!started) {
            context.getSource().sendError(Text.literal("Replay segment failed to load."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Replay started."), false);
        return 1;
    }

    private static int xrayReplayLatest(CommandContext<ServerCommandSource> context, AdminMod mod) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("Only players can play replays."));
            return 0;
        }
        UUID targetUuid = resolveUuid(mod, StringArgumentType.getString(context, "player"));
        if (targetUuid == null) {
            context.getSource().sendError(Text.literal("Player not found."));
            return 0;
        }
        boolean started = mod.xrayReplayManager().playLatest(actor, targetUuid);
        if (!started) {
            context.getSource().sendError(Text.literal("No replay segment available."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Latest replay started."), false);
        return 1;
    }

    private static int xrayReplayStop(CommandContext<ServerCommandSource> context, AdminMod mod) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("Only players can stop replay."));
            return 0;
        }
        boolean stopped = mod.xrayReplayManager().stop(actor);
        if (!stopped) {
            context.getSource().sendError(Text.literal("No active replay session."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Replay stopped."), false);
        return 1;
    }

    private static int xrayReplayDelete(CommandContext<ServerCommandSource> context, AdminMod mod) {
        UUID targetUuid = resolveUuid(mod, StringArgumentType.getString(context, "player"));
        if (targetUuid == null) {
            context.getSource().sendError(Text.literal("Player not found."));
            return 0;
        }
        long segment = IntegerArgumentType.getInteger(context, "segment");
        boolean removed = mod.xrayReplayManager().deleteSegment(context.getSource().getPlayer(), targetUuid, segment);
        if (!removed) {
            context.getSource().sendError(Text.literal("Segment not found."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Replay segment deleted."), true);
        return 1;
    }

    private static int heatmapPlayer(CommandContext<ServerCommandSource> context, AdminMod mod, HeatmapManager.HeatmapMode mode) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("Only players can start heatmap review."));
            return 0;
        }
        UUID targetUuid = resolveUuid(mod, StringArgumentType.getString(context, "player"));
        if (targetUuid == null) {
            context.getSource().sendError(Text.literal("Player not found."));
            return 0;
        }
        long duration = resolveDurationArgument(context, 30 * 60_000L);
        if (duration < 0L) {
            context.getSource().sendError(Text.literal("Invalid duration. Use formats like 30m, 1h, 24h."));
            return 0;
        }
        boolean started = mod.heatmapManager().startPlayerView(actor, targetUuid, mode, duration);
        if (!started) {
            context.getSource().sendError(Text.literal("Unable to start heatmap review."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Heatmap started: mode=" + mode.id + ", target=" + resolveName(mod, targetUuid) + ", duration=" + DurationParser.formatMillis(duration)), false);
        return 1;
    }

    private static int heatmapGlobal(CommandContext<ServerCommandSource> context, AdminMod mod, HeatmapManager.HeatmapMode mode) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("Only players can start heatmap review."));
            return 0;
        }
        String world = StringArgumentType.getString(context, "world");
        if (!world.contains(":")) {
            world = "minecraft:" + world;
        }
        long duration = resolveDurationArgument(context, 30 * 60_000L);
        if (duration < 0L) {
            context.getSource().sendError(Text.literal("Invalid duration. Use formats like 30m, 1h, 24h."));
            return 0;
        }
        boolean started = mod.heatmapManager().startGlobalView(actor, world, mode, duration);
        if (!started) {
            context.getSource().sendError(Text.literal("Unable to start global heatmap review."));
            return 0;
        }
        final String worldId = world;
        context.getSource().sendFeedback(() -> Text.literal("Global heatmap started: mode=" + mode.id + ", world=" + worldId + ", duration=" + DurationParser.formatMillis(duration)), false);
        return 1;
    }

    private static int heatmapWatchlist(CommandContext<ServerCommandSource> context, AdminMod mod, HeatmapManager.HeatmapMode mode) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("Only players can start heatmap review."));
            return 0;
        }
        long duration = resolveDurationArgument(context, 30 * 60_000L);
        if (duration < 0L) {
            context.getSource().sendError(Text.literal("Invalid duration. Use formats like 30m, 1h, 24h."));
            return 0;
        }
        boolean started = mod.heatmapManager().startWatchlistView(actor, mode, duration);
        if (!started) {
            context.getSource().sendError(Text.literal("Unable to start watchlist heatmap review."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Watchlist heatmap started: mode=" + mode.id + ", duration=" + DurationParser.formatMillis(duration)), false);
        return 1;
    }

    private static int heatmapStop(CommandContext<ServerCommandSource> context, AdminMod mod) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("Only players can stop heatmap review."));
            return 0;
        }
        boolean stopped = mod.heatmapManager().stop(actor);
        if (!stopped) {
            context.getSource().sendError(Text.literal("No active heatmap review session."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Heatmap review stopped."), false);
        return 1;
    }

    private static int heatmapRadius(CommandContext<ServerCommandSource> context, AdminMod mod) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("Only players can set heatmap radius."));
            return 0;
        }
        int radius = IntegerArgumentType.getInteger(context, "value");
        boolean changed = mod.heatmapManager().setRadius(actor, radius);
        if (!changed) {
            context.getSource().sendError(Text.literal("No active heatmap session."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Heatmap radius set to " + radius + "."), false);
        return 1;
    }

    private static int heatmapMode(CommandContext<ServerCommandSource> context, AdminMod mod) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("Only players can set heatmap mode."));
            return 0;
        }
        String modeRaw = StringArgumentType.getString(context, "mode");
        HeatmapManager.HeatmapMode mode = HeatmapManager.HeatmapMode.parse(modeRaw);
        if (mode == null) {
            context.getSource().sendError(Text.literal("Invalid mode. Use movement|mining|ore|suspicious."));
            return 0;
        }
        boolean changed = mod.heatmapManager().setMode(actor, mode);
        if (!changed) {
            context.getSource().sendError(Text.literal("No active heatmap session."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Heatmap mode set to " + mode.id + "."), false);
        return 1;
    }

    private static int heatmapInfo(CommandContext<ServerCommandSource> context, AdminMod mod, HeatmapManager.HeatmapMode mode) {
        UUID targetUuid = resolveUuid(mod, StringArgumentType.getString(context, "player"));
        if (targetUuid == null) {
            context.getSource().sendError(Text.literal("Player not found."));
            return 0;
        }
        long duration = resolveDurationArgument(context, 24 * 60 * 60_000L);
        if (duration < 0L) {
            context.getSource().sendError(Text.literal("Invalid duration. Use formats like 30m, 1h, 24h."));
            return 0;
        }
        List<String> lines = mod.heatmapManager().describeTopHotspots(mode, targetUuid, false, "", duration, 10);
        if (lines.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No heatmap hotspots for " + resolveName(mod, targetUuid) + " in selected range."), false);
            return 1;
        }
        context.getSource().sendFeedback(() -> Text.literal("Heatmap hotspots for " + resolveName(mod, targetUuid) + " (" + mode.id + "):"), false);
        for (String line : lines) {
            context.getSource().sendFeedback(() -> Text.literal(line), false);
        }
        return 1;
    }

    private static int lagIdentifyScan(CommandContext<ServerCommandSource> context, AdminMod mod) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("Only players can run lag identify scan."));
            return 0;
        }
        String scope;
        try {
            scope = StringArgumentType.getString(context, "scope");
        } catch (IllegalArgumentException ignored) {
            scope = "";
        }
        if (mod.lagIdentifyManager().isScanRunning()) {
            context.getSource().sendError(Text.literal(mod.lagIdentifyManager().activeScanProgress()));
            return 0;
        }
        boolean started = mod.lagIdentifyManager().startScan(actor, scope);
        if (!started) {
            context.getSource().sendError(Text.literal("Could not start scan."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Lag identify scan started."), false);
        return 1;
    }

    private static int lagIdentifyStop(CommandContext<ServerCommandSource> context, AdminMod mod) {
        boolean stopped = mod.lagIdentifyManager().stopScan(context.getSource().getPlayer());
        if (!stopped) {
            context.getSource().sendError(Text.literal("No scan is running."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Lag identify scan stopped."), false);
        return 1;
    }

    private static int lagIdentifyList(CommandContext<ServerCommandSource> context, AdminMod mod) {
        List<LagIdentifyResultData> results = mod.lagIdentifyManager().listResults();
        if (results.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No lag hotspots found."), false);
            return 1;
        }
        context.getSource().sendFeedback(() -> Text.literal("Lag hotspot results (items>20 or entities>20):").formatted(Formatting.GOLD), false);
        for (int i = 0; i < results.size() && i < 20; i++) {
            LagIdentifyResultData result = results.get(i);
            Formatting scoreColor = result.score >= 120.0D ? Formatting.RED
                    : result.score >= 80.0D ? Formatting.GOLD
                    : result.score >= 50.0D ? Formatting.YELLOW
                    : Formatting.GREEN;
            Text idPart = Text.literal("#" + result.resultId + " ").formatted(Formatting.AQUA);
            Text scorePart = Text.literal("[" + String.format(Locale.ROOT, "%.1f", result.score) + "] ").formatted(scoreColor);
            Text worldPart = Text.literal(result.world + " ").formatted(Formatting.GRAY);
            Text chunkPart = Text.literal("chunk(" + result.chunkX + "," + result.chunkZ + ") ").formatted(Formatting.GRAY);
            Text amountPart = Text.literal("items=" + result.droppedItems + " entities=" + result.totalEntities + " ").formatted(Formatting.WHITE);
            Text summaryPart = Text.literal(result.summary).formatted(Formatting.DARK_GRAY);
            Text tpButton = Text.literal("[TP]").setStyle(
                    Style.EMPTY.withColor(Formatting.GREEN)
                            .withClickEvent(new ClickEvent.RunCommand("/lagidentify tp " + result.resultId))
                            .withHoverEvent(new net.minecraft.text.HoverEvent.ShowText(Text.literal("Teleport to hotspot #" + result.resultId)))
            );
            Text infoButton = Text.literal(" [INFO]").setStyle(
                    Style.EMPTY.withColor(Formatting.YELLOW)
                            .withClickEvent(new ClickEvent.RunCommand("/lagidentify info " + result.resultId))
                            .withHoverEvent(new net.minecraft.text.HoverEvent.ShowText(Text.literal("Show full hotspot details")))
            );
            Text line = idPart.copy().append(scorePart).append(worldPart).append(chunkPart).append(amountPart).append(summaryPart).append(Text.literal(" ")).append(tpButton).append(infoButton);
            context.getSource().sendFeedback(() -> line, false);
        }
        return 1;
    }

    private static int lagIdentifyInfo(CommandContext<ServerCommandSource> context, AdminMod mod) {
        long id = IntegerArgumentType.getInteger(context, "result-id");
        LagIdentifyResultData result = mod.lagIdentifyManager().getResult(id);
        if (result == null) {
            context.getSource().sendError(Text.literal("Result not found."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Lag hotspot #" + result.resultId + " " + result.category), false);
        context.getSource().sendFeedback(() -> Text.literal("world=" + result.world + " chunk=(" + result.chunkX + "," + result.chunkZ + ") pos=(" + result.centerX + "," + result.centerY + "," + result.centerZ + ")"), false);
        context.getSource().sendFeedback(() -> Text.literal("score=" + String.format(Locale.ROOT, "%.2f", result.score) + " summary=" + result.summary), false);
        context.getSource().sendFeedback(() -> Text.literal(
                "entities=" + result.totalEntities
                        + " items=" + result.droppedItems
                        + " mobs=" + result.mobs
                        + " projectiles=" + result.projectiles
                        + " villagers=" + result.villagers
                        + " xp_orbs=" + result.xpOrbs
        ), false);
        context.getSource().sendFeedback(() -> Text.literal(
                "block_entities=" + result.blockEntities
                        + " ticking=" + result.tickingBlockEntities
                        + " hoppers=" + result.hoppers
                        + " containers=" + result.containers
                        + " redstone=" + result.redstoneComponents
        ), false);
        return 1;
    }

    private static int lagIdentifyTp(CommandContext<ServerCommandSource> context, AdminMod mod) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("Only players can teleport to hotspot."));
            return 0;
        }
        long id = IntegerArgumentType.getInteger(context, "result-id");
        boolean ok = mod.lagIdentifyManager().teleportToResult(actor, id);
        if (!ok) {
            context.getSource().sendError(Text.literal("Failed to teleport to hotspot."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Teleported to lag hotspot #" + id + "."), false);
        return 1;
    }

    private static int lagIdentifyBack(CommandContext<ServerCommandSource> context, AdminMod mod) {
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (actor == null) {
            context.getSource().sendError(Text.literal("Only players can use this."));
            return 0;
        }
        boolean ok = mod.lagIdentifyManager().back(actor);
        if (!ok) {
            context.getSource().sendError(Text.literal("No previous lagidentify location."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Returned to previous location."), false);
        return 1;
    }

    private static int lagIdentifyClear(CommandContext<ServerCommandSource> context, AdminMod mod) {
        boolean ok = mod.lagIdentifyManager().clearResults(context.getSource().getPlayer());
        if (!ok) {
            context.getSource().sendError(Text.literal("Could not clear results."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Lag identify results cleared."), false);
        return 1;
    }

    private static int lagIdentifySettings(CommandContext<ServerCommandSource> context, AdminMod mod) {
        context.getSource().sendFeedback(() -> Text.literal("LagIdentify settings: " + mod.lagIdentifyManager().settingsSummary()), false);
        return 1;
    }

    private static int scheduledStopStart(CommandContext<ServerCommandSource> context, AdminMod mod) {
        String rawDuration = StringArgumentType.getString(context, "duration");
        DurationParser.ParseResult parsed = DurationParser.parse(normalizeDurationInput(rawDuration));
        if (!parsed.valid()) {
            context.getSource().sendError(Text.literal("Invalid duration. Use formats like 15min, 10m, 1h30m."));
            return 0;
        }
        if (mod.scheduledStopManager().isActive()) {
            context.getSource().sendError(Text.literal(mod.scheduledStopManager().statusText()));
            return 0;
        }
        boolean started = mod.scheduledStopManager().schedule(context.getSource().getPlayer(), context.getSource().getServer(), parsed.millis());
        if (!started) {
            context.getSource().sendError(Text.literal("Could not schedule server stop."));
            return 0;
        }
        context.getSource().sendFeedback(
                () -> Text.literal("Scheduled server stop in " + DurationParser.formatMillis(parsed.millis()) + "."),
                true
        );
        return 1;
    }

    private static int scheduledStopCancel(CommandContext<ServerCommandSource> context, AdminMod mod) {
        boolean canceled = mod.scheduledStopManager().cancel(context.getSource().getPlayer(), context.getSource().getServer());
        if (!canceled) {
            context.getSource().sendError(Text.literal("No scheduled server stop is active."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Scheduled server stop canceled."), true);
        return 1;
    }

    private static int scheduledStopStatus(CommandContext<ServerCommandSource> context, AdminMod mod) {
        context.getSource().sendFeedback(() -> Text.literal(mod.scheduledStopManager().statusText()), false);
        return 1;
    }

    private static int scheduledStopBossbar(CommandContext<ServerCommandSource> context, AdminMod mod, String rawMode) {
        com.b1progame.adminmod.maintenance.ScheduledStopManager.BossBarMode mode = switch (rawMode.toLowerCase(Locale.ROOT)) {
            case "off" -> com.b1progame.adminmod.maintenance.ScheduledStopManager.BossBarMode.OFF;
            case "self" -> com.b1progame.adminmod.maintenance.ScheduledStopManager.BossBarMode.SELF;
            case "all" -> com.b1progame.adminmod.maintenance.ScheduledStopManager.BossBarMode.ALL;
            default -> null;
        };
        if (mode == null) {
            context.getSource().sendError(Text.literal("Invalid mode. Use off|self|all."));
            return 0;
        }
        ServerPlayerEntity actor = context.getSource().getPlayer();
        if (mode == com.b1progame.adminmod.maintenance.ScheduledStopManager.BossBarMode.SELF && actor == null) {
            context.getSource().sendError(Text.literal("Console cannot use self mode. Use all or off."));
            return 0;
        }
        mod.scheduledStopManager().setBossBarMode(actor, context.getSource().getServer(), mode);
        context.getSource().sendFeedback(() -> Text.literal("Server stop bossbar mode set to " + mode.id + "."), true);
        return 1;
    }

    private static int scheduledStopKickPolicy(CommandContext<ServerCommandSource> context, AdminMod mod, String rawPolicy) {
        com.b1progame.adminmod.maintenance.ScheduledStopManager.KickPolicy policy = switch (rawPolicy.toLowerCase(Locale.ROOT)) {
            case "all" -> com.b1progame.adminmod.maintenance.ScheduledStopManager.KickPolicy.ALL;
            case "non_admin" -> com.b1progame.adminmod.maintenance.ScheduledStopManager.KickPolicy.NON_ADMIN;
            case "nobody" -> com.b1progame.adminmod.maintenance.ScheduledStopManager.KickPolicy.NOBODY;
            default -> null;
        };
        if (policy == null) {
            context.getSource().sendError(Text.literal("Invalid kick policy. Use all|non_admin|nobody."));
            return 0;
        }
        mod.scheduledStopManager().setKickPolicy(context.getSource().getPlayer(), policy);
        context.getSource().sendFeedback(() -> Text.literal("Server stop kick policy set to " + policy.id + "."), true);
        return 1;
    }

    private static long resolveDurationArgument(CommandContext<ServerCommandSource> context, long defaultMillis) {
        try {
            String raw = StringArgumentType.getString(context, "duration");
            DurationParser.ParseResult parsed = DurationParser.parse(normalizeDurationInput(raw));
            return parsed.valid() ? parsed.millis() : -1L;
        } catch (IllegalArgumentException ignored) {
            return defaultMillis;
        }
    }

    private static String normalizeDurationInput(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        normalized = normalized
                .replace("seconds", "s")
                .replace("second", "s")
                .replace("secs", "s")
                .replace("sec", "s")
                .replace("minutes", "m")
                .replace("minute", "m")
                .replace("mins", "m")
                .replace("min", "m")
                .replace("hours", "h")
                .replace("hour", "h")
                .replace("hrs", "h")
                .replace("hr", "h")
                .replace("days", "d")
                .replace("day", "d");
        return normalized.replaceAll("\\s+", "");
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
