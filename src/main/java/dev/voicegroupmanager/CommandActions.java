package dev.voicegroupmanager;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static dev.voicegroupmanager.VoiceGroupManagerPlugin.GROUP_TYPES;

/**
 * What each command does. {@link CommandTree} defines the command syntax and hands the already-parsed
 * arguments to these methods; permissions are checked there too.
 */
final class CommandActions {
    private static final double DEFAULT_RANGE = 128D;
    private static final double DEFAULT_MAX_RANGE = 256D;
    static final String EDIT_USAGE = "/persistentgroup edit <group> <name|type|hidden|password> <value>";

    private final VoiceGroupManagerPlugin plugin;
    private final GroupStore store;
    private final Messages messages;

    CommandActions(VoiceGroupManagerPlugin plugin) {
        this.plugin = plugin;
        this.store = plugin.store();
        this.messages = plugin.messages();
    }

    // ---- /voicegroupmanager ----

    void reload(CommandSender sender) {
        plugin.loadSettings();
        msg(sender, "reloaded");
    }

    void info(CommandSender sender) {
        VoicechatServerApi api = plugin.voicechat();
        msg(sender, "info_header", "version", plugin.getPluginMeta().getVersion());
        msg(sender, api == null ? "info_voicechat_offline" : "info_voicechat_online");

        int live = 0;
        if (api != null) {
            for (SavedGroup saved : store.all()) {
                if (api.getGroup(saved.id()) != null) live++;
            }
        }
        msg(sender, "info_persistent", "saved", String.valueOf(store.size()), "live", String.valueOf(live));

        SavedGroup global = store.get(store.globalAutoJoin());
        if (global == null) {
            msg(sender, "info_autojoin_none");
        } else {
            msg(sender, "info_autojoin", "group", global.name(), "force", store.forceGlobalAutoJoin() ? ", forced" : "");
        }
        msg(sender, "info_forced_type", "type", plugin.forcedType().toLowerCase(Locale.ROOT));
    }

    /** Lists the voice groups that online players are currently in, including hidden ones. */
    void groups(CommandSender sender) {
        VoicechatServerApi api = plugin.voicechat();
        if (api == null) {
            unavailable(sender);
            return;
        }
        Map<UUID, Group> groups = new LinkedHashMap<>();
        Map<UUID, Integer> members = new HashMap<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            VoicechatConnection connection = api.getConnectionOf(online.getUniqueId());
            if (connection == null || !connection.isInGroup()) continue;
            Group group = connection.getGroup();
            if (group == null) continue;
            groups.putIfAbsent(group.getId(), group);
            members.merge(group.getId(), 1, Integer::sum);
        }
        if (groups.isEmpty()) {
            msg(sender, "groups_none");
            return;
        }
        msg(sender, "groups_header", "count", String.valueOf(groups.size()));
        groups.values().stream()
                .sorted(Comparator.comparing((Group g) -> g.getName().toLowerCase(Locale.ROOT)))
                .forEach(g -> {
                    String id = g.getId().toString();
                    String flags = (g.isHidden() ? ", hidden" : "")
                            + (g.hasPassword() ? ", password" : "")
                            + (g.isPersistent() ? ", persistent" : "");
                    sender.sendMessage(copyable(messages.component("groups_entry",
                            "group", g.getName(),
                            "type", SvcHook.typeName(g.getType()),
                            "flags", flags,
                            "members", String.valueOf(members.get(g.getId())),
                            "id", id), id));
                });
    }

    // ---- /instantgroup ----

    /** @param requestedRange the range typed by the player, or null to use the configured default */
    void instant(CommandSender sender, Double requestedRange) {
        if (!(sender instanceof Player player)) {
            playerOnly(sender);
            return;
        }
        VoicechatServerApi api = plugin.voicechat();
        if (api == null) {
            unavailable(sender);
            return;
        }

        double max = maxInstantRange();
        double range;
        if (requestedRange == null) {
            range = plugin.getConfig().getDouble("default_instant_group_range", DEFAULT_RANGE);
            if (!Double.isFinite(range) || range < 1) range = DEFAULT_RANGE;
            range = Math.min(range, max);
        } else {
            range = requestedRange;
            if (!Double.isFinite(range) || range < 1) {
                msg(sender, "invalid_range");
                return;
            }
            if (range > max) {
                msg(sender, "range_too_large", "max", formatNumber(max));
                return;
            }
        }

        VoicechatConnection own = api.getConnectionOf(player.getUniqueId());
        if (own == null) {
            unavailable(sender);
            return;
        }
        Group target = own.isInGroup() ? own.getGroup() : null;
        boolean created = false;
        if (target == null) {
            try {
                target = api.groupBuilder()
                        .setName(plugin.getConfig().getString("instant_group_name", "Instant Group"))
                        .setType(Group.Type.OPEN)
                        .build();
                created = true;
            } catch (RuntimeException e) {
                msg(sender, "instant_invalid_name");
                return;
            }
        }

        double rangeSquared = range * range;
        Location center = player.getLocation();
        int added = 0;
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(center) > rangeSquared) continue;
            if (Visibility.isHidden(player, nearby)) continue; // never reveal vanished players
            VoicechatConnection connection = api.getConnectionOf(nearby.getUniqueId());
            if (connection == null || connection.isInGroup()) continue;
            connection.setGroup(target);
            added++;
            if (!nearby.equals(player)) {
                plugin.notifyMoved(nearby.getUniqueId(), "moved_instant",
                        "group", target.getName(), "player", player.getName());
            }
        }

        if (created && added == 0) {
            api.removeGroup(target.getId()); // never leave an empty group behind
        }
        msg(sender, created ? "instant_created" : "instant_existing",
                "count", String.valueOf(added), "group", target.getName());
    }

    /** Range values worth suggesting while a player types /instantgroup. */
    List<String> rangeSuggestions() {
        double max = maxInstantRange();
        double def = plugin.getConfig().getDouble("default_instant_group_range", DEFAULT_RANGE);
        if (!Double.isFinite(def) || def < 1) def = DEFAULT_RANGE;
        def = Math.min(def, max);
        return def == max ? List.of(formatNumber(def)) : List.of(formatNumber(def), formatNumber(max));
    }

    private double maxInstantRange() {
        double max = plugin.getConfig().getDouble("max_instant_group_range", DEFAULT_MAX_RANGE);
        return Double.isFinite(max) && max >= 1 ? max : DEFAULT_MAX_RANGE;
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.valueOf(value);
    }

    // ---- /persistentgroup ----

    void listGroups(CommandSender sender) {
        if (store.size() == 0) {
            msg(sender, "persistent_none");
            return;
        }
        store.all().stream()
                .sorted(Comparator.comparing((SavedGroup g) -> g.name().toLowerCase(Locale.ROOT)))
                .forEach(g -> sender.sendMessage(copyable(messages.component("persistent_entry",
                        "group", g.name(),
                        "type", g.type().toLowerCase(Locale.ROOT),
                        "flags", (g.hidden() ? ", hidden" : "") + (g.password() != null ? ", password" : ""),
                        "id", g.id().toString()), g.id().toString())));
    }

    /** @param type normal, open or isolated; @param password null for none */
    void addGroup(CommandSender sender, String name, String type, boolean hidden, String password) {
        VoicechatServerApi api = plugin.voicechat();
        if (api == null) {
            unavailable(sender);
            return;
        }
        if (store.findByName(name) != null) {
            msg(sender, "persistent_duplicate", "group", name);
            return;
        }

        UUID id = UUID.randomUUID();
        SavedGroup draft = new SavedGroup(id, name, password, type.toUpperCase(Locale.ROOT), hidden);
        Group group;
        try {
            group = SvcHook.build(api, draft);
        } catch (RuntimeException e) {
            msg(sender, "persistent_create_failed", "error", String.valueOf(e.getMessage()));
            return;
        }
        SavedGroup saved = draft.withName(group.getName());
        store.put(saved);
        store.requestSave();
        msg(sender, "persistent_created", "group", saved.name(), "id", id.toString());
    }

    /** @param property name, type, hidden or password */
    void editGroup(CommandSender sender, String query, String property, String value) {
        VoicechatServerApi api = plugin.voicechat();
        if (api == null) {
            unavailable(sender);
            return;
        }
        SavedGroup old = store.find(query);
        if (old == null) {
            msg(sender, "group_not_found");
            return;
        }

        SavedGroup updated;
        if (property.equals("name")) {
            SavedGroup clash = store.findByName(value);
            if (clash != null && !clash.id().equals(old.id())) {
                msg(sender, "persistent_duplicate", "group", value);
                return;
            }
            updated = old.withName(value);
        } else if (property.equals("type")) {
            if (!GROUP_TYPES.contains(value.toLowerCase(Locale.ROOT))) {
                usage(sender, EDIT_USAGE);
                return;
            }
            updated = old.withType(value.toUpperCase(Locale.ROOT));
        } else if (property.equals("hidden")) {
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                usage(sender, EDIT_USAGE);
                return;
            }
            updated = old.withHidden(Boolean.parseBoolean(value));
        } else if (property.equals("password")) {
            updated = old.withPassword(value.equalsIgnoreCase("none") ? null : value);
        } else {
            usage(sender, EDIT_USAGE);
            return;
        }

        String error = rebuild(api, old, updated);
        if (error != null) {
            msg(sender, "persistent_edit_failed", "error", error);
            return;
        }
        store.put(updated);
        store.requestSave();
        msg(sender, "persistent_edited", "group", updated.name(), "property", property);
    }

    /**
     * Simple Voice Chat groups cannot be changed after they are created, so an edit replaces the live group
     * (same ID) and moves its online members across. Returns an error text, or null on success.
     */
    private String rebuild(VoicechatServerApi api, SavedGroup old, SavedGroup updated) {
        List<VoicechatConnection> members = new ArrayList<>();
        Group live = api.getGroup(old.id());
        if (live != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                VoicechatConnection connection = api.getConnectionOf(online.getUniqueId());
                if (connection == null || !connection.isInGroup()) continue;
                Group group = connection.getGroup();
                if (group != null && group.getId().equals(old.id())) members.add(connection);
            }
            for (VoicechatConnection connection : members) connection.setGroup(null);
            if (!api.removeGroup(old.id())) {
                for (VoicechatConnection connection : members) connection.setGroup(live);
                return "Simple Voice Chat would not remove the old group.";
            }
        }
        Group rebuilt;
        try {
            rebuilt = SvcHook.build(api, updated);
        } catch (RuntimeException e) {
            try { // put things back the way they were
                Group restored = SvcHook.build(api, old);
                for (VoicechatConnection connection : members) connection.setGroup(restored);
            } catch (RuntimeException ignored) { }
            return String.valueOf(e.getMessage());
        }
        for (VoicechatConnection connection : members) connection.setGroup(rebuilt);
        return null;
    }

    void removeGroup(CommandSender sender, String query) {
        SavedGroup saved = store.find(query);
        if (saved == null) {
            msg(sender, "group_not_found");
            return;
        }
        VoicechatServerApi api = plugin.voicechat();
        // A saved group that never made it into Simple Voice Chat (failed restore, voice chat offline)
        // can still be deleted from the saved list.
        if (api != null && api.getGroup(saved.id()) != null && !api.removeGroup(saved.id())) {
            msg(sender, "persistent_remove_failed");
            return;
        }
        store.remove(saved.id());
        store.requestSave();
        msg(sender, "persistent_removed", "group", saved.name());
    }

    // ---- /autojoingroup ----

    void autoJoinSet(CommandSender sender, String query, String password) {
        if (!(sender instanceof Player player)) {
            playerOnly(sender);
            return;
        }
        SavedGroup group = store.find(query);
        if (group == null) {
            msg(sender, "group_not_found");
            return;
        }
        if (group.password() != null && !group.password().equals(password)) {
            msg(sender, "autojoin_wrong_password");
            return;
        }
        store.setPersonalAutoJoin(player.getUniqueId(), group.id());
        store.requestSave();
        msg(sender, "autojoin_personal_set", "group", group.name());
    }

    void autoJoinRemove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            playerOnly(sender);
            return;
        }
        store.removePersonalAutoJoin(player.getUniqueId());
        store.requestSave();
        msg(sender, "autojoin_personal_removed");
    }

    void autoJoinGlobalSet(CommandSender sender, String query) {
        SavedGroup group = store.find(query);
        if (group == null) {
            msg(sender, "group_not_found");
            return;
        }
        if (group.password() != null) {
            msg(sender, "autojoin_global_password");
            return;
        }
        store.setGlobalAutoJoin(group.id());
        store.requestSave();
        msg(sender, "autojoin_global_set", "group", group.name());
    }

    void autoJoinGlobalRemove(CommandSender sender) {
        store.setGlobalAutoJoin(null);
        store.requestSave();
        msg(sender, "autojoin_global_removed");
    }

    void autoJoinGlobalForce(CommandSender sender, boolean value) {
        store.setForceGlobalAutoJoin(value);
        store.requestSave();
        msg(sender, "autojoin_force_set", "value", String.valueOf(store.forceGlobalAutoJoin()));
    }

    // ---- /forcejoingroup ----

    void forceJoin(CommandSender sender, String targetName) {
        if (!(sender instanceof Player executor)) {
            playerOnly(sender);
            return;
        }
        VoicechatServerApi api = plugin.voicechat();
        if (api == null) {
            unavailable(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            msg(sender, "player_offline");
            return;
        }
        VoicechatConnection own = api.getConnectionOf(executor.getUniqueId());
        VoicechatConnection other = api.getConnectionOf(target.getUniqueId());
        Group group = own != null && own.isInGroup() ? own.getGroup() : null;
        if (group == null) {
            msg(sender, "forcejoin_not_in_group");
            return;
        }
        if (other == null) {
            msg(sender, "forcejoin_target_not_connected");
            return;
        }
        other.setGroup(group);
        plugin.notifyMoved(target.getUniqueId(), "moved_force", "group", group.getName(), "player", executor.getName());
        msg(sender, "forcejoin_moved", "player", target.getName());
    }

    List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    // ---- Helpers ----

    /** Makes a chat line copy {@code value} to the clipboard when clicked. */
    private Component copyable(Component line, String value) {
        return line.clickEvent(ClickEvent.copyToClipboard(value))
                .hoverEvent(HoverEvent.showText(messages.render(messages.template("click_to_copy"))));
    }

    void msg(CommandSender sender, String key, String... replacements) {
        messages.send(sender, key, replacements);
    }

    void usage(CommandSender sender, String value) {
        msg(sender, "usage", "usage", value);
    }

    private void playerOnly(CommandSender sender) {
        msg(sender, "player_only");
    }

    private void unavailable(CommandSender sender) {
        msg(sender, "voicechat_unavailable");
    }
}
