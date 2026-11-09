package dev.voicegroupmanager;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static dev.voicegroupmanager.VoiceGroupManagerPlugin.GROUP_TYPES;

/** All commands and their tab completion. */
final class Commands implements CommandExecutor, TabCompleter {
    private static final double DEFAULT_RANGE = 128D;
    private static final double DEFAULT_MAX_RANGE = 256D;
    private static final String EDIT_USAGE = "/persistentgroup edit <group> <name|type|hidden|password> <value>";

    private final VoiceGroupManagerPlugin plugin;
    private final GroupStore store;
    private final Messages messages;

    Commands(VoiceGroupManagerPlugin plugin) {
        this.plugin = plugin;
        this.store = plugin.store();
        this.messages = plugin.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "instantgroup" -> instant(sender, args);
            case "persistentgroup" -> persistent(sender, args);
            case "autojoingroup" -> autoJoin(sender, args);
            case "forcejoingroup" -> forceJoin(sender, args);
            case "voicegroupmanager" -> manage(sender, args);
            default -> false;
        };
    }

    // ---- /voicegroupmanager ----

    private boolean manage(CommandSender sender, String[] args) {
        String sub = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission(Permissions.RELOAD)) return deny(sender);
                plugin.loadSettings();
                msg(sender, "reloaded");
            }
            case "info" -> {
                if (!sender.hasPermission(Permissions.INFO)) return deny(sender);
                info(sender);
            }
            case "groups" -> {
                if (!sender.hasPermission(Permissions.INFO)) return deny(sender);
                groups(sender);
            }
            default -> {
                if (!sender.hasPermission(Permissions.RELOAD) && !sender.hasPermission(Permissions.INFO)) return deny(sender);
                usage(sender, "/voicegroupmanager <reload|info|groups>");
            }
        }
        return true;
    }

    private void info(CommandSender sender) {
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
    private void groups(CommandSender sender) {
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
                            "type", g.getType().name().toLowerCase(Locale.ROOT),
                            "flags", flags,
                            "members", String.valueOf(members.get(g.getId())),
                            "id", id), id));
                });
    }

    // ---- /instantgroup ----

    private boolean instant(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.INSTANT)) return deny(sender);
        if (!(sender instanceof Player player)) return playerOnly(sender);
        VoicechatServerApi api = plugin.voicechat();
        if (api == null) return unavailable(sender);
        if (args.length > 1) {
            usage(sender, "/instantgroup [range]");
            return true;
        }

        double max = maxInstantRange();
        double range = plugin.getConfig().getDouble("default_instant_group_range", DEFAULT_RANGE);
        if (!Double.isFinite(range) || range < 1) range = DEFAULT_RANGE;
        range = Math.min(range, max);
        if (args.length == 1) {
            try {
                range = Double.parseDouble(args[0]);
            } catch (NumberFormatException e) {
                msg(sender, "invalid_range");
                return true;
            }
            if (!Double.isFinite(range) || range < 1) {
                msg(sender, "invalid_range");
                return true;
            }
            if (range > max) {
                msg(sender, "range_too_large", "max", formatNumber(max));
                return true;
            }
        }

        VoicechatConnection own = api.getConnectionOf(player.getUniqueId());
        if (own == null) return unavailable(sender);
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
                return true;
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
        return true;
    }

    private double maxInstantRange() {
        double max = plugin.getConfig().getDouble("max_instant_group_range", DEFAULT_MAX_RANGE);
        return Double.isFinite(max) && max >= 1 ? max : DEFAULT_MAX_RANGE;
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.valueOf(value);
    }

    // ---- /persistentgroup ----

    private boolean persistent(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.PERSISTENT)) return deny(sender);
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            listGroups(sender);
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("add")) {
            addGroup(sender, args);
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("edit")) {
            editGroup(sender, args);
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("remove")) {
            removeGroup(sender, args);
        } else {
            usage(sender, "/persistentgroup <list|add|edit|remove>");
        }
        return true;
    }

    private void listGroups(CommandSender sender) {
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

    private void addGroup(CommandSender sender, String[] args) {
        VoicechatServerApi api = plugin.voicechat();
        if (api == null) {
            unavailable(sender);
            return;
        }
        // Supports a quoted name: add "Staff Chat" [type] [hidden] [password]
        // A quoted word is never mistaken for a type or the hidden flag, so "true" can be used as a password.
        List<ArgParser.Token> tokens = ArgParser.tokenize(remainder(args, 1));
        if (tokens.isEmpty()) {
            usage(sender, "/persistentgroup add <n> [normal|open|isolated] [true|false] [password]");
            return;
        }

        String name = tokens.get(0).text();
        int i = 1;
        String type = "normal";
        boolean hidden = false;
        String password = null;
        if (i < tokens.size() && !tokens.get(i).quoted()
                && GROUP_TYPES.contains(tokens.get(i).text().toLowerCase(Locale.ROOT))) {
            type = tokens.get(i++).text().toLowerCase(Locale.ROOT);
        }
        if (i < tokens.size() && !tokens.get(i).quoted()
                && (tokens.get(i).text().equalsIgnoreCase("true") || tokens.get(i).text().equalsIgnoreCase("false"))) {
            hidden = Boolean.parseBoolean(tokens.get(i++).text());
        }
        if (i < tokens.size()) {
            password = ArgParser.join(tokens, i);
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

    /** /persistentgroup edit <group> <name|type|hidden|password> <value> */
    private void editGroup(CommandSender sender, String[] args) {
        VoicechatServerApi api = plugin.voicechat();
        if (api == null) {
            unavailable(sender);
            return;
        }
        List<ArgParser.Token> tokens = ArgParser.tokenize(remainder(args, 1));
        if (tokens.size() < 3) {
            usage(sender, EDIT_USAGE);
            return;
        }
        SavedGroup old = store.find(tokens.get(0).text());
        if (old == null) {
            msg(sender, "group_not_found");
            return;
        }
        String property = tokens.get(1).text().toLowerCase(Locale.ROOT);
        String value = ArgParser.join(tokens, 2);

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

    private void removeGroup(CommandSender sender, String[] args) {
        SavedGroup saved = store.find(remainder(args, 1));
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

    private boolean autoJoin(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("global")) return autoJoinGlobal(sender, args);
        if (!sender.hasPermission(Permissions.AUTOJOIN)) return deny(sender);
        if (!(sender instanceof Player player)) return playerOnly(sender);

        if (args.length == 1 && args[0].equalsIgnoreCase("remove")) {
            store.removePersonalAutoJoin(player.getUniqueId());
            store.requestSave();
            msg(sender, "autojoin_personal_removed");
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("set")) {
            List<String> parts = ArgParser.split(remainder(args, 1));
            if (parts.isEmpty()) {
                usage(sender, "/autojoingroup set <group> [password]");
                return true;
            }
            SavedGroup group = store.find(parts.get(0));
            if (group == null) {
                msg(sender, "group_not_found");
                return true;
            }
            String password = parts.size() > 1 ? String.join(" ", parts.subList(1, parts.size())) : null;
            if (group.password() != null && !group.password().equals(password)) {
                msg(sender, "autojoin_wrong_password");
                return true;
            }
            store.setPersonalAutoJoin(player.getUniqueId(), group.id());
            store.requestSave();
            msg(sender, "autojoin_personal_set", "group", group.name());
            return true;
        }
        usage(sender, "/autojoingroup <set <group> [password]|remove|global>");
        return true;
    }

    private boolean autoJoinGlobal(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.AUTOJOIN_GLOBAL)) return deny(sender);

        if (args.length == 2 && args[1].equalsIgnoreCase("remove")) {
            store.setGlobalAutoJoin(null);
            store.requestSave();
            msg(sender, "autojoin_global_removed");
            return true;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("force")) {
            if (!args[2].equalsIgnoreCase("true") && !args[2].equalsIgnoreCase("false")) {
                usage(sender, "/autojoingroup global force <true|false>");
                return true;
            }
            store.setForceGlobalAutoJoin(Boolean.parseBoolean(args[2]));
            store.requestSave();
            msg(sender, "autojoin_force_set", "value", String.valueOf(store.forceGlobalAutoJoin()));
            return true;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("set")) {
            SavedGroup group = store.find(remainder(args, 2));
            if (group == null) {
                msg(sender, "group_not_found");
                return true;
            }
            if (group.password() != null) {
                msg(sender, "autojoin_global_password");
                return true;
            }
            store.setGlobalAutoJoin(group.id());
            store.requestSave();
            msg(sender, "autojoin_global_set", "group", group.name());
            return true;
        }
        usage(sender, "/autojoingroup global <set <group>|remove|force <true|false>>");
        return true;
    }

    // ---- /forcejoingroup ----

    private boolean forceJoin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.FORCEJOIN)) return deny(sender);
        if (!(sender instanceof Player executor)) return playerOnly(sender);
        VoicechatServerApi api = plugin.voicechat();
        if (api == null) return unavailable(sender);
        if (args.length != 1) {
            usage(sender, "/forcejoingroup <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            msg(sender, "player_offline");
            return true;
        }
        VoicechatConnection own = api.getConnectionOf(executor.getUniqueId());
        VoicechatConnection other = api.getConnectionOf(target.getUniqueId());
        Group group = own != null && own.isInGroup() ? own.getGroup() : null;
        if (group == null) {
            msg(sender, "forcejoin_not_in_group");
            return true;
        }
        if (other == null) {
            msg(sender, "forcejoin_target_not_connected");
            return true;
        }
        other.setGroup(group);
        plugin.notifyMoved(target.getUniqueId(), "moved_force", "group", group.getName(), "player", executor.getName());
        msg(sender, "forcejoin_moved", "player", target.getName());
        return true;
    }

    // ---- Tab completion ----

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return List.of();
        List<String> options = switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "persistentgroup" -> sender.hasPermission(Permissions.PERSISTENT) ? completePersistent(args) : List.of();
            case "autojoingroup" -> completeAutoJoin(sender, args);
            case "forcejoingroup" -> args.length == 1 && sender.hasPermission(Permissions.FORCEJOIN)
                    ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                    : List.of();
            case "voicegroupmanager" -> args.length == 1 ? completeManage(sender) : List.of();
            default -> List.of();
        };
        String start = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(start)).toList();
    }

    private List<String> completeManage(CommandSender sender) {
        List<String> subs = new ArrayList<>();
        if (sender.hasPermission(Permissions.RELOAD)) subs.add("reload");
        if (sender.hasPermission(Permissions.INFO)) subs.addAll(List.of("info", "groups"));
        return subs;
    }

    private List<String> completePersistent(String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean add = sub.equals("add");
        boolean edit = sub.equals("edit");
        return switch (args.length) {
            case 1 -> List.of("list", "add", "edit", "remove");
            case 2 -> sub.equals("remove") || edit ? store.names() : List.of();
            case 3 -> edit ? List.of("name", "type", "hidden", "password") : add ? GROUP_TYPES : List.of();
            case 4 -> edit ? completeEditValue(args[2]) : add ? List.of("true", "false") : List.of();
            default -> List.of();
        };
    }

    private List<String> completeEditValue(String property) {
        return switch (property.toLowerCase(Locale.ROOT)) {
            case "type" -> GROUP_TYPES;
            case "hidden" -> List.of("true", "false");
            case "password" -> List.of("none");
            default -> List.of();
        };
    }

    private List<String> completeAutoJoin(CommandSender sender, String[] args) {
        boolean personal = sender.hasPermission(Permissions.AUTOJOIN);
        boolean global = sender.hasPermission(Permissions.AUTOJOIN_GLOBAL);
        if (args.length == 1) {
            List<String> first = new ArrayList<>();
            if (personal) first.addAll(List.of("set", "remove"));
            if (global) first.add("global");
            return first;
        }
        if (args[0].equalsIgnoreCase("global")) {
            if (!global) return List.of();
            if (args.length == 2) return List.of("set", "remove", "force");
            if (args.length == 3 && args[1].equalsIgnoreCase("force")) return List.of("true", "false");
            if (args.length == 3 && args[1].equalsIgnoreCase("set")) return store.names();
            return List.of();
        }
        if (personal && args.length == 2 && args[0].equalsIgnoreCase("set")) return store.names();
        return List.of();
    }

    // ---- Helpers ----

    /** Makes a chat line copy {@code value} to the clipboard when clicked. */
    private Component copyable(Component line, String value) {
        return line.clickEvent(ClickEvent.copyToClipboard(value))
                .hoverEvent(HoverEvent.showText(messages.render(messages.template("click_to_copy"))));
    }

    private void msg(CommandSender sender, String key, String... replacements) {
        messages.send(sender, key, replacements);
    }

    private String remainder(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length)).trim();
    }

    private boolean deny(CommandSender sender) {
        msg(sender, "no_permission");
        return true;
    }

    private boolean playerOnly(CommandSender sender) {
        msg(sender, "player_only");
        return true;
    }

    private boolean unavailable(CommandSender sender) {
        msg(sender, "voicechat_unavailable");
        return true;
    }

    private void usage(CommandSender sender, String value) {
        msg(sender, "usage", "usage", value);
    }
}
