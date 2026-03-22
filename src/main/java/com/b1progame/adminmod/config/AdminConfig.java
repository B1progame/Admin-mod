package com.b1progame.adminmod.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class AdminConfig {
    public List<String> allowed_admin_uuids = new ArrayList<>();
    public String maintenance_kick_message = "Server entered maintenance mode.";
    public String maintenance_join_denied_message = "Server is in maintenance mode. Please try again later.";
    public boolean silent_inventory_inspection = true;
    public VanishMessages vanish_status_messages = new VanishMessages();
    public boolean vanish_leave_message_default = false;
    public String vanish_fake_join_message_format = "%player% joined the game";
    public String vanish_fake_leave_message_format = "%player% left the game";
    public boolean vanished_admins_can_see_other_vanished = true;
    public boolean staff_can_see_vanished_staff = true;
    public boolean silent_connect_default = false;
    public boolean silent_disconnect_default = false;
    public FreezeSettings freeze = new FreezeSettings();
    public MuteSettings mute = new MuteSettings();
    public StaffChatSettings staff_chat = new StaffChatSettings();
    public BanSettings bans = new BanSettings();
    public XrayTrackerSettings xray_tracker = new XrayTrackerSettings();
    public JoinNotificationsSettings join_notifications = new JoinNotificationsSettings();
    public RollbackLiteSettings rollback_lite = new RollbackLiteSettings();
    public StaffSessionSettings staff_sessions = new StaffSessionSettings();
    public CommandHistorySettings command_history = new CommandHistorySettings();
    public IpViewSettings ip_view = new IpViewSettings();
    public XrayReplaySettings xray_replay = new XrayReplaySettings();
    public GuiSettings gui = new GuiSettings();
    public GuiTitles gui_titles = new GuiTitles();
    public boolean sound_feedback_enabled = true;
    public Logging logging = new Logging();

    public static final class VanishMessages {
        public String enabled = "Vanish enabled.";
        public String disabled = "Vanish disabled.";
        public String actionbar = "VANISH ACTIVE";
    }

    public static final class GuiTitles {
        public String main = "gui.adminmod.main.title";
        public String player_list = "gui.adminmod.player_browser.title";
        public String player_manage = "gui.adminmod.player_manage.title";
        public String inventory_menu = "gui.adminmod.inventory_menu.title";
        public String world = "gui.adminmod.world.title";
        public String admin = "gui.adminmod.admin.title";
        public String op_confirm = "gui.adminmod.confirm.op.title";
        public String temp_op_duration = "gui.adminmod.temp_op.duration.title";
        public String temp_op_confirm = "gui.adminmod.temp_op.confirm.title";
        public String notes = "gui.adminmod.notes.title";
        public String inventory_view = "gui.adminmod.inventory.title";
        public String ender_view = "gui.adminmod.ender.title";
        public String confirm_action = "gui.adminmod.confirm.action.title";
        public String player_overview = "gui.adminmod.player_overview.title";
        public String action_history = "gui.adminmod.action_history.title";
        public String snapshots = "gui.adminmod.snapshots.title";
        public String temp_ban_duration = "gui.adminmod.temp_ban.duration.title";
        public String rollback_lite = "gui.adminmod.rollback.title";
        public String xray_settings = "gui.adminmod.xray_settings.title";
    }

    public static final class GuiSettings {
        public String back_button_texture = "";
    }

    public static final class FreezeSettings {
        public String freeze_message = "You have been frozen by staff.";
        public boolean freeze_actionbar_enabled = true;
        public boolean persist_through_relog = true;
    }

    public static final class MuteSettings {
        public String mute_message = "You are muted and cannot chat.";
    }

    public static final class StaffChatSettings {
        public String prefix = "[Staff]";
        public boolean log_messages = true;
    }

    public static final class BanSettings {
        public String ban_join_denied_message = "You are banned from this server.";
        public String temp_ban_join_denied_message = "You are temporarily banned.";
    }

    public static final class XrayTrackerSettings {
        public boolean enabled = true;
        public boolean alert_sounds = true;
        public boolean auto_stealth_review = false;
        public boolean staff_alerts_enabled = true;
        public boolean click_to_teleport_enabled = true;
        public boolean suspicion_score_enabled = true;
        public int suspicion_score_window_minutes = 20;
        public int suspicion_decay_half_life_minutes = 5;
        public int suspicion_alert_threshold = 45;
        public boolean valuable_ore_immediate_alerts = true;
        public int valuable_repeat_window_minutes = 3;
        public int valuable_repeat_count = 2;
        public int retention_days = 14;
        public int suspicious_ore_count = 8;
        public int suspicious_window_minutes = 10;
        public Set<String> tracked_ores = Set.of(
                "minecraft:diamond_ore",
                "minecraft:deepslate_diamond_ore",
                "minecraft:emerald_ore",
                "minecraft:deepslate_emerald_ore",
                "minecraft:ancient_debris"
        );
    }

    public static final class JoinNotificationsSettings {
        public boolean enabled = true;
        public boolean watchlist_join_notifications = true;
        public boolean suspicious_player_join_notifications = true;
        public boolean whitelist_denied_notifications = true;
        public boolean queue_offline_staff_mail = true;
        public int suspicious_join_score_threshold = 70;
        public String watchlist_join_template = "[Watchlist] %player% joined. Reason: %reason%";
        public String suspicious_join_template = "[XrayNotice] %player% joined with suspicion score %score%.";
        public String banned_attempt_template = "[BanNotice] %player% attempted to join while banned.";
        public String whitelist_denied_template = "[WhitelistNotice] %player% (%uuid%) attempted to join but is not whitelisted.";
    }

    public static final class RollbackLiteSettings {
        public int max_entries_per_player = 50;
    }

    public static final class StaffSessionSettings {
        public boolean enabled = true;
        public int max_saved_sessions = 500;
    }

    public static final class CommandHistorySettings {
        public boolean enabled = true;
        public int max_entries = 5000;
        public List<String> redaction_rules = List.of("/login", "/register", "/changepassword");
    }

    public static final class IpViewSettings {
        public boolean ip_view_enabled = true;
        public boolean strict_admin_only_ip_access = true;
    }

    public static final class XrayReplaySettings {
        public boolean enabled = true;
        public int movement_sample_interval_ticks = 2;
        public int rotation_sample_interval_ticks = 2;
        public int visibility_refresh_interval_ticks = 10;
        public int block_snapshot_update_interval_ticks = 10;
        public int max_segment_duration_minutes = 30;
        public int inactivity_stop_seconds = 120;
        public int replay_retention_days = 14;
        public boolean compression_enabled = true;
        public int max_visible_block_capture_distance = 8;
        public int max_reconstructed_replay_radius_per_step = 24;
        public int replay_playback_tick_ms = 50;
        public double replay_default_speed = 1.0D;
    }

    public static final class Logging {
        public boolean sensitive_actions = true;
        public boolean inventory_actions = true;
        public boolean staff_chat_toggles = true;
        public boolean command_history_access = true;
        public boolean ip_view_access = true;
    }
}
