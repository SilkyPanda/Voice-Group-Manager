package dev.voicegroupmanager;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Server-side voice group management for Simple Voice Chat on Bukkit/Purpur servers. */
public final class VoiceGroupManagerPlugin extends JavaPlugin implements Listener {
    private static final String PERM_INSTANT = "voicegroupmanager.instantgroup";
    private static final String PERM_PERSISTENT = "voicegroupmanager.persistentgroup";
    private static final String PERM_AUTOJOIN = "voicegroupmanager.autojoingroup";
    private static final String PERM_AUTOJOIN_GLOBAL = "voicegroupmanager.autojoingroup.global";
    private static final String PERM_FORCEJOIN = "voicegroupmanager.forcejoingroup";
    private static final String PERM_RELOAD = "voicegroupmanager.reload";

    private static final List<String> GROUP_TYPES = List.of("normal", "open", "isolated");
    private static final String DEFAULT_PREFIX = "&8[&aVoice Group Manager&8] &r";
    private static final double DEFAULT_RANGE = 128D;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ConcurrentMap<UUID, SavedGroup> persistentGroups = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> personalAutoJoin = new ConcurrentHashMap<>();
    private final AtomicBoolean passwordReadWarned = new AtomicBoolean();
    private volatile UUID globalAutoJoin;
    private volatile boolean forceGlobalAutoJoin;
    private volatile VoicechatServerApi voicechat;
    private volatile String forcedType = "OFF";
    private volatile YamlConfiguration messages;
    private volatile boolean storesLoaded;

    // ---------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------

    @Override
    public void onEnable() {
        BukkitVoicechatService service = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            getLogger().severe("Simple Voice Chat's Bukkit API service was not found; disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        loadSettings();
        loadStores();
        getServer().getPluginManager().registerEvents(this, this);

        Commands commands = new Commands();
        for (String name : List.of("instantgroup", "persistentgroup", "autojoingroup", "forcejoingroup", "voicegroupmanager")) {
            PluginCommand command = getCommand(name);
            if (command == null) {
                getLogger().warning("Command '" + name + "' is missing from plugin.yml.");
                continue;
            }
            command.setExecutor(commands);
            command.setTabCompleter(commands);
        }

        service.registerPlugin(new SvcHook());
        getLogger().info("Voice Group Manager enabled; voice features are available to players connected to Simple Voice Chat.");
    }

    @Override
    public void onDisable() {
        saveStores();
        voicechat = null;
    }

    /** (Re)reads config.yml and messages.yml and re-applies everything derived from them. */
    private void loadSettings() {
        reloadConfig();
        mergeDefaultConfigComments();
        loadMessages();

        String type = getConfig().getString("force_group_type", "OFF").trim().toUpperCase(Locale.ROOT);
        if (!type.equals("OFF") && !GROUP_TYPES.contains(type.toLowerCase(Locale.ROOT))) {
            getLogger().warning("Invalid force_group_type '" + type + "' in config.yml; use OFF, NORMAL, OPEN or ISOLATED. Using OFF.");
            type = "OFF";
        }
        forcedType = type;
        configurePermissionDefaults();
    }

    /** Adds default comments to an existing config without changing its values or custom comments. */
    private void mergeDefaultConfigComments() {
        try (InputStream resource = getResource("config.yml")) {
            if (resource == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String path : defaults.getKeys(true)) {
                if (getConfig().contains(path) && getConfig().getComments(path).isEmpty()) {
                    List<String> comments = defaults.getComments(path);
                    if (!comments.isEmpty()) {
                        getConfig().setComments(path, comments);
                        changed = true;
                    }
                }
            }
            if (changed) saveConfig();
        } catch (Exception e) {
            getLogger().warning("Could not update config.yml comments: " + e.getMessage());
        }
    }

    /** Loads messages.yml; keys missing from an older file fall back to the bundled defaults. */
    private void loadMessages() {
        File file = new File(getDataFolder(), "messages.yml");
        if (!file.exists()) {
            saveResource("messages.yml", false);
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        try (InputStream resource = getResource("messages.yml")) {
            if (resource != null) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(resource, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            getLogger().warning("Could not read bundled messages.yml defaults: " + e.getMessage());
        }
        messages = loaded;
    }

    private void configurePermissionDefaults() {
        Map<String, String> nodes = Map.of(
                "instantgroup", PERM_INSTANT,
                "persistentgroup", PERM_PERSISTENT,
                "autojoingroup", PERM_AUTOJOIN,
                "autojoingroup_global", PERM_AUTOJOIN_GLOBAL,
                "forcejoingroup", PERM_FORCEJOIN);
        nodes.forEach((key, node) -> {
            Permission permission = getServer().getPluginManager().getPermission(node);
            if (permission == null) return;
            String setting = getConfig().getString("permission_defaults." + key, "OPS").trim().toUpperCase(Locale.ROOT);
            permission.setDefault(switch (setting) {
                case "EVERYONE" -> PermissionDefault.TRUE;
                case "NOONE" -> PermissionDefault.FALSE;
                case "OPS" -> PermissionDefault.OP;
                default -> {
                    getLogger().warning("Invalid permission_defaults." + key + " '" + setting
                            + "' in config.yml; use EVERYONE, OPS or NOONE. Using OPS.");
                    yield PermissionDefault.OP;
                }
            });
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------------------------------

    private void loadStores() {
        YamlConfiguration persistent = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "persistent-groups.yml"));
        ConfigurationSection section = persistent.getConfigurationSection("groups");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    ConfigurationSection g = section.getConfigurationSection(key);
                    if (g == null) continue;
                    persistentGroups.put(id, new SavedGroup(id, g.getString("name", ""), g.getString("password"),
                            g.getString("type", "NORMAL"), g.getBoolean("hidden")));
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Ignoring invalid persistent group id: " + key);
                }
            }
        }

        YamlConfiguration autoJoin = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "auto-join-groups.yml"));
        ConfigurationSection personal = autoJoin.getConfigurationSection("players");
        if (personal != null) {
            for (String player : personal.getKeys(false)) {
                try {
                    String group = personal.getString(player);
                    if (group == null) throw new IllegalArgumentException("missing group");
                    personalAutoJoin.put(UUID.fromString(player), UUID.fromString(group));
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Ignoring invalid auto-join entry for " + player);
                }
            }
        }
        globalAutoJoin = parseUuid(autoJoin.getString("global_group"));
        forceGlobalAutoJoin = autoJoin.getBoolean("force_global", false);
        storesLoaded = true;
    }

    private synchronized void saveStores() {
        if (!storesLoaded) return;

        YamlConfiguration persistent = new YamlConfiguration();
        persistentGroups.values().stream()
                .sorted(Comparator.comparing((SavedGroup g) -> g.id().toString()))
                .forEach(g -> {
                    String path = "groups." + g.id();
                    persistent.set(path + ".name", g.name());
                    persistent.set(path + ".password", g.password());
                    persistent.set(path + ".type", g.type());
                    persistent.set(path + ".hidden", g.hidden());
                });

        YamlConfiguration autoJoin = new YamlConfiguration();
        personalAutoJoin.forEach((player, group) -> autoJoin.set("players." + player, group.toString()));
        UUID global = globalAutoJoin;
        autoJoin.set("global_group", global == null ? null : global.toString());
        autoJoin.set("force_global", forceGlobalAutoJoin);

        atomicSave(persistent, new File(getDataFolder(), "persistent-groups.yml"));
        atomicSave(autoJoin, new File(getDataFolder(), "auto-join-groups.yml"));
    }

    private void atomicSave(YamlConfiguration config, File target) {
        File temp = new File(getDataFolder(), target.getName() + ".tmp");
        try {
            getDataFolder().mkdirs();
            config.save(temp);
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            getLogger().severe("Could not save " + target.getName() + ": " + e.getMessage());
            temp.delete();
        }
    }

    private UUID parseUuid(String value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Messages
    // ---------------------------------------------------------------------------------------------

    private String template(String key) {
        YamlConfiguration source = messages;
        String value = source == null ? null : source.getString(key);
        return value != null ? value : "&c[missing message: " + key + "]";
    }

    /**
     * Renders a legacy-formatted ('&') template. Placeholders such as {group} are replaced with plain text,
     * so player-supplied values (for example group names) can never inject colour codes.
     */
    private Component render(String template, String... replacements) {
        Component component = LEGACY.deserialize(template);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            String token = "{" + replacements[i] + "}";
            Component value = Component.text(replacements[i + 1]);
            component = component.replaceText(builder -> builder.matchLiteral(token).replacement(value));
        }
        return component;
    }

    private void msg(CommandSender sender, String key, String... replacements) {
        YamlConfiguration source = messages;
        String prefix = source == null ? DEFAULT_PREFIX : source.getString("prefix", DEFAULT_PREFIX);
        sender.sendMessage(render(prefix + template(key), replacements));
    }

    // ---------------------------------------------------------------------------------------------
    // Join summary
    // ---------------------------------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("group_summary", true)) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> sendSummary(player), 20L);
    }

    private void sendSummary(Player joining) {
        VoicechatServerApi api = voicechat;
        if (api == null || !joining.isOnline()) return;

        Map<UUID, Group> groups = new LinkedHashMap<>();
        int memberCount = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            VoicechatConnection connection = api.getConnectionOf(online.getUniqueId());
            if (connection == null || !connection.isInGroup()) continue;
            Group group = connection.getGroup();
            if (group == null || group.isHidden()) continue;
            memberCount++;
            groups.putIfAbsent(group.getId(), group);
        }
        if (memberCount == 0) return;

        joining.sendMessage(render(template("summary_header"), "count", String.valueOf(memberCount)));
        for (Group group : groups.values()) {
            Component row = render(template("summary_entry"), "group", group.getName());
            if (!group.hasPassword()) {
                Component join = render(template("summary_join"))
                        .clickEvent(ClickEvent.runCommand("/voicechat join " + group.getId()));
                row = row.append(Component.text(" ")).append(join);
            }
            joining.sendMessage(row);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Simple Voice Chat hook
    // ---------------------------------------------------------------------------------------------

    private Group.Type groupType(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "OPEN" -> Group.Type.OPEN;
            case "ISOLATED" -> Group.Type.ISOLATED;
            default -> Group.Type.NORMAL;
        };
    }

    /** Builds the live voice group for a saved one. Its voice-chat ID is always the saved ID. */
    private Group buildPersistentGroup(VoicechatServerApi api, SavedGroup saved) {
        return api.groupBuilder()
                .setPersistent(true)
                .setName(saved.name())
                .setPassword(saved.password())
                .setType(groupType(saved.type()))
                .setHidden(saved.hidden())
                .setId(saved.id())
                .build();
    }

    private final class SvcHook implements VoicechatPlugin {
        @Override
        public String getPluginId() {
            return "voicegroupmanager";
        }

        @Override
        public void initialize(VoicechatApi api) { }

        @Override
        public void registerEvents(EventRegistration events) {
            events.registerEvent(VoicechatServerStartedEvent.class, this::started);
            events.registerEvent(VoicechatServerStoppedEvent.class, event -> voicechat = null);
            events.registerEvent(PlayerConnectedEvent.class, this::playerConnected);
            events.registerEvent(CreateGroupEvent.class, this::createGroup);
        }

        private void started(VoicechatServerStartedEvent event) {
            VoicechatServerApi api = event.getVoicechat();
            voicechat = api;
            int restored = 0;
            for (SavedGroup saved : persistentGroups.values()) {
                try {
                    buildPersistentGroup(api, saved);
                    restored++;
                } catch (RuntimeException e) {
                    getLogger().warning("Could not restore persistent group '" + saved.name() + "': " + e.getMessage());
                }
            }
            getLogger().info("Restored " + restored + " persistent voice group(s).");
        }

        private void playerConnected(PlayerConnectedEvent event) {
            UUID player = event.getConnection().getPlayer().getUuid();
            UUID global = globalAutoJoin;
            UUID savedId = forceGlobalAutoJoin && global != null ? global : personalAutoJoin.getOrDefault(player, global);
            if (savedId == null) return;
            Group group = event.getVoicechat().getGroup(savedId);
            if (group != null) event.getConnection().setGroup(group);
        }

        /** Replaces groups created through the Simple Voice Chat GUI with ones of the forced type. */
        private void createGroup(CreateGroupEvent event) {
            String forced = forcedType;
            // A null connection means the group was created by a plugin (including this one).
            if ("OFF".equals(forced) || event.getConnection() == null) return;
            Group original = event.getGroup();
            Group.Type desired = groupType(forced);
            if (original == null || original.getType().equals(desired)) return;

            String password = readPassword(original);
            event.cancel();
            if (original.hasPassword() && password == null) {
                getLogger().warning("Cancelled group type replacement because the group password could not be read safely.");
                return;
            }
            try {
                Group replacement = event.getVoicechat().groupBuilder()
                        .setType(desired)
                        .setPassword(password)
                        .setName(original.getName())
                        .setPersistent(original.isPersistent())
                        .setHidden(original.isHidden())
                        .build();
                event.getConnection().setGroup(replacement);
            } catch (RuntimeException e) {
                getLogger().warning("Could not replace group type: " + e.getMessage());
            }
        }
    }

    /** The public API cannot read group passwords, so this reaches into Simple Voice Chat's internals. */
    private String readPassword(Group group) {
        try {
            Field wrapped = group.getClass().getDeclaredField("group");
            wrapped.setAccessible(true);
            Object model = wrapped.get(group);
            for (Class<?> c = model.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    Field password = c.getDeclaredField("password");
                    password.setAccessible(true);
                    return (String) password.get(model);
                } catch (NoSuchFieldException ignored) { }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (passwordReadWarned.compareAndSet(false, true)) {
                getLogger().warning("Unable to read a voice group password for a forced type change: " + e);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Commands
    // ---------------------------------------------------------------------------------------------

    private SavedGroup findByName(String name) {
        return persistentGroups.values().stream()
                .filter(g -> g.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private List<String> groupNames() {
        return persistentGroups.values().stream()
                .map(SavedGroup::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(name -> name.contains(" ") ? "\"" + name + "\"" : name)
                .toList();
    }

    private final class Commands implements CommandExecutor, TabCompleter {
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
            if (!sender.hasPermission(PERM_RELOAD)) return deny(sender);
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                loadSettings();
                msg(sender, "reloaded");
                return true;
            }
            usage(sender, "/voicegroupmanager reload");
            return true;
        }

        // ---- /instantgroup ----

        private boolean instant(CommandSender sender, String[] args) {
            if (!sender.hasPermission(PERM_INSTANT)) return deny(sender);
            if (!(sender instanceof Player player)) return playerOnly(sender);
            VoicechatServerApi api = voicechat;
            if (api == null) return unavailable(sender);
            if (args.length > 1) {
                usage(sender, "/instantgroup [range]");
                return true;
            }

            double range = getConfig().getDouble("default_instant_group_range", DEFAULT_RANGE);
            if (!Double.isFinite(range) || range < 1) range = DEFAULT_RANGE;
            if (args.length == 1) {
                try {
                    range = Double.parseDouble(args[0]);
                } catch (NumberFormatException e) {
                    msg(sender, "invalid_range");
                    return true;
                }
            }
            if (!Double.isFinite(range) || range < 1) {
                msg(sender, "invalid_range");
                return true;
            }

            VoicechatConnection own = api.getConnectionOf(player.getUniqueId());
            if (own == null) return unavailable(sender);
            Group target = own.isInGroup() ? own.getGroup() : null;
            boolean created = false;
            if (target == null) {
                try {
                    target = api.groupBuilder()
                            .setName(getConfig().getString("instant_group_name", "Instant Group"))
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
                VoicechatConnection connection = api.getConnectionOf(nearby.getUniqueId());
                if (connection == null || connection.isInGroup()) continue;
                connection.setGroup(target);
                added++;
            }

            if (created && added == 0) {
                api.removeGroup(target.getId()); // never leave an empty group behind
            }
            msg(sender, created ? "instant_created" : "instant_existing",
                    "count", String.valueOf(added), "group", target.getName());
            return true;
        }

        // ---- /persistentgroup ----

        private boolean persistent(CommandSender sender, String[] args) {
            if (!sender.hasPermission(PERM_PERSISTENT)) return deny(sender);
            if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
                listGroups(sender);
            } else if (args.length >= 2 && args[0].equalsIgnoreCase("add")) {
                addGroup(sender, args);
            } else if (args.length >= 2 && args[0].equalsIgnoreCase("remove")) {
                removeGroup(sender, args);
            } else {
                usage(sender, "/persistentgroup <list|add|remove>");
            }
            return true;
        }

        private void listGroups(CommandSender sender) {
            if (persistentGroups.isEmpty()) {
                msg(sender, "persistent_none");
                return;
            }
            persistentGroups.values().stream()
                    .sorted(Comparator.comparing((SavedGroup g) -> g.name().toLowerCase(Locale.ROOT)))
                    .forEach(g -> msg(sender, "persistent_entry",
                            "group", g.name(),
                            "type", g.type().toLowerCase(Locale.ROOT),
                            "flags", (g.hidden() ? ", hidden" : "") + (g.password() != null ? ", password" : ""),
                            "id", g.id().toString()));
        }

        private void addGroup(CommandSender sender, String[] args) {
            VoicechatServerApi api = voicechat;
            if (api == null) {
                unavailable(sender);
                return;
            }
            // Supports a quoted name: add "Staff Chat" [type] [hidden] [password]
            List<String> tokens = parseArgs(remainder(args, 1));
            if (tokens.isEmpty()) {
                usage(sender, "/persistentgroup add <name> [normal|open|isolated] [true|false] [password]");
                return;
            }

            String name = tokens.get(0);
            int i = 1;
            String type = "normal";
            boolean hidden = false;
            String password = null;
            if (i < tokens.size() && GROUP_TYPES.contains(tokens.get(i).toLowerCase(Locale.ROOT))) {
                type = tokens.get(i++).toLowerCase(Locale.ROOT);
            }
            if (i < tokens.size() && (tokens.get(i).equalsIgnoreCase("true") || tokens.get(i).equalsIgnoreCase("false"))) {
                hidden = Boolean.parseBoolean(tokens.get(i++));
            }
            if (i < tokens.size()) {
                password = String.join(" ", tokens.subList(i, tokens.size()));
            }
            if (findByName(name) != null) {
                msg(sender, "persistent_duplicate", "group", name);
                return;
            }

            UUID id = UUID.randomUUID();
            Group group;
            try {
                group = api.groupBuilder()
                        .setPersistent(true)
                        .setName(name)
                        .setPassword(password)
                        .setType(groupType(type))
                        .setHidden(hidden)
                        .setId(id)
                        .build();
            } catch (RuntimeException e) {
                msg(sender, "persistent_create_failed", "error", String.valueOf(e.getMessage()));
                return;
            }
            SavedGroup saved = new SavedGroup(id, group.getName(), password, type.toUpperCase(Locale.ROOT), hidden);
            persistentGroups.put(id, saved);
            saveStores();
            msg(sender, "persistent_created", "group", saved.name(), "id", id.toString());
        }

        private void removeGroup(CommandSender sender, String[] args) {
            SavedGroup saved = findGroup(remainder(args, 1));
            if (saved == null) {
                msg(sender, "group_not_found");
                return;
            }
            VoicechatServerApi api = voicechat;
            // A saved group that never made it into Simple Voice Chat (failed restore, voice chat offline)
            // can still be deleted from the saved list.
            if (api != null && api.getGroup(saved.id()) != null && !api.removeGroup(saved.id())) {
                msg(sender, "persistent_remove_failed");
                return;
            }
            persistentGroups.remove(saved.id());
            personalAutoJoin.values().removeIf(saved.id()::equals);
            if (saved.id().equals(globalAutoJoin)) globalAutoJoin = null;
            saveStores();
            msg(sender, "persistent_removed", "group", saved.name());
        }

        // ---- /autojoingroup ----

        private boolean autoJoin(CommandSender sender, String[] args) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("global")) return autoJoinGlobal(sender, args);
            if (!sender.hasPermission(PERM_AUTOJOIN)) return deny(sender);
            if (!(sender instanceof Player player)) return playerOnly(sender);

            if (args.length == 1 && args[0].equalsIgnoreCase("remove")) {
                personalAutoJoin.remove(player.getUniqueId());
                saveStores();
                msg(sender, "autojoin_personal_removed");
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("set")) {
                List<String> parts = parseArgs(remainder(args, 1));
                if (parts.isEmpty()) {
                    usage(sender, "/autojoingroup set <group> [password]");
                    return true;
                }
                SavedGroup group = findGroup(parts.get(0));
                if (group == null) {
                    msg(sender, "group_not_found");
                    return true;
                }
                String password = parts.size() > 1 ? String.join(" ", parts.subList(1, parts.size())) : null;
                if (group.password() != null && !group.password().equals(password)) {
                    msg(sender, "autojoin_wrong_password");
                    return true;
                }
                personalAutoJoin.put(player.getUniqueId(), group.id());
                saveStores();
                msg(sender, "autojoin_personal_set", "group", group.name());
                return true;
            }
            usage(sender, "/autojoingroup <set <group> [password]|remove|global>");
            return true;
        }

        private boolean autoJoinGlobal(CommandSender sender, String[] args) {
            if (!sender.hasPermission(PERM_AUTOJOIN_GLOBAL)) return deny(sender);

            if (args.length == 2 && args[1].equalsIgnoreCase("remove")) {
                globalAutoJoin = null;
                saveStores();
                msg(sender, "autojoin_global_removed");
                return true;
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("force")) {
                if (!args[2].equalsIgnoreCase("true") && !args[2].equalsIgnoreCase("false")) {
                    usage(sender, "/autojoingroup global force <true|false>");
                    return true;
                }
                forceGlobalAutoJoin = Boolean.parseBoolean(args[2]);
                saveStores();
                msg(sender, "autojoin_force_set", "value", String.valueOf(forceGlobalAutoJoin));
                return true;
            }
            if (args.length >= 3 && args[1].equalsIgnoreCase("set")) {
                SavedGroup group = findGroup(remainder(args, 2));
                if (group == null) {
                    msg(sender, "group_not_found");
                    return true;
                }
                if (group.password() != null) {
                    msg(sender, "autojoin_global_password");
                    return true;
                }
                globalAutoJoin = group.id();
                saveStores();
                msg(sender, "autojoin_global_set", "group", group.name());
                return true;
            }
            usage(sender, "/autojoingroup global <set <group>|remove|force <true|false>>");
            return true;
        }

        // ---- /forcejoingroup ----

        private boolean forceJoin(CommandSender sender, String[] args) {
            if (!sender.hasPermission(PERM_FORCEJOIN)) return deny(sender);
            if (!(sender instanceof Player executor)) return playerOnly(sender);
            VoicechatServerApi api = voicechat;
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
            msg(sender, "forcejoin_moved", "player", target.getName());
            return true;
        }

        // ---- Tab completion ----

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 0) return List.of();
            List<String> options = switch (command.getName().toLowerCase(Locale.ROOT)) {
                case "persistentgroup" -> sender.hasPermission(PERM_PERSISTENT) ? completePersistent(args) : List.of();
                case "autojoingroup" -> completeAutoJoin(sender, args);
                case "forcejoingroup" -> args.length == 1 && sender.hasPermission(PERM_FORCEJOIN)
                        ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                        : List.of();
                case "voicegroupmanager" -> args.length == 1 && sender.hasPermission(PERM_RELOAD)
                        ? List.of("reload")
                        : List.of();
                default -> List.of();
            };
            String start = args[args.length - 1].toLowerCase(Locale.ROOT);
            return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(start)).toList();
        }

        private List<String> completePersistent(String[] args) {
            boolean add = args[0].equalsIgnoreCase("add");
            return switch (args.length) {
                case 1 -> List.of("list", "add", "remove");
                case 2 -> args[0].equalsIgnoreCase("remove") ? groupNames() : List.of();
                case 3 -> add ? GROUP_TYPES : List.of();
                case 4 -> add ? List.of("true", "false") : List.of();
                default -> List.of();
            };
        }

        private List<String> completeAutoJoin(CommandSender sender, String[] args) {
            boolean personal = sender.hasPermission(PERM_AUTOJOIN);
            boolean global = sender.hasPermission(PERM_AUTOJOIN_GLOBAL);
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
                if (args.length == 3 && args[1].equalsIgnoreCase("set")) return groupNames();
                return List.of();
            }
            if (personal && args.length == 2 && args[0].equalsIgnoreCase("set")) return groupNames();
            return List.of();
        }

        // ---- Helpers ----

        /** Finds a saved group by UUID or by (optionally quoted) name. */
        private SavedGroup findGroup(String query) {
            String value = unquote(query.trim()).trim();
            try {
                SavedGroup byId = persistentGroups.get(UUID.fromString(value));
                if (byId != null) return byId;
            } catch (IllegalArgumentException ignored) { }
            return findByName(value);
        }

        private String remainder(String[] args, int from) {
            return String.join(" ", Arrays.copyOfRange(args, from, args.length)).trim();
        }

        /** Splits on whitespace, keeping double-quoted sections together. */
        private List<String> parseArgs(String value) {
            List<String> out = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '"') {
                    quoted = !quoted;
                } else if (Character.isWhitespace(c) && !quoted) {
                    if (!current.isEmpty()) {
                        out.add(current.toString());
                        current.setLength(0);
                    }
                } else {
                    current.append(c);
                }
            }
            if (!current.isEmpty()) out.add(current.toString());
            return out;
        }

        private String unquote(String value) {
            return value.replaceAll("^\"|\"$", "");
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

    private record SavedGroup(UUID id, String name, String password, String type, boolean hidden) { }
}
