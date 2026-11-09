package dev.voicegroupmanager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Persistent groups and auto-join settings, kept in memory and saved to the plugin folder. */
final class GroupStore {
    private final VoiceGroupManagerPlugin plugin;
    private final ConcurrentMap<UUID, SavedGroup> groups = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> personalAutoJoin = new ConcurrentHashMap<>();
    private final AtomicBoolean saveQueued = new AtomicBoolean();
    private volatile UUID globalAutoJoin;
    private volatile boolean forceGlobalAutoJoin;
    private volatile boolean loaded;

    GroupStore(VoiceGroupManagerPlugin plugin) {
        this.plugin = plugin;
    }

    // ---- Groups ----

    Collection<SavedGroup> all() {
        return groups.values();
    }

    int size() {
        return groups.size();
    }

    SavedGroup get(UUID id) {
        return id == null ? null : groups.get(id);
    }

    void put(SavedGroup group) {
        groups.put(group.id(), group);
    }

    /** Removes a group and every auto-join setting that pointed at it. */
    void remove(UUID id) {
        groups.remove(id);
        personalAutoJoin.values().removeIf(id::equals);
        if (id.equals(globalAutoJoin)) globalAutoJoin = null;
    }

    SavedGroup findByName(String name) {
        return groups.values().stream()
                .filter(g -> g.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    /** Finds a saved group by UUID or by (optionally quoted) name. */
    SavedGroup find(String query) {
        String value = ArgParser.unquote(query.trim()).trim();
        try {
            SavedGroup byId = groups.get(UUID.fromString(value));
            if (byId != null) return byId;
        } catch (IllegalArgumentException ignored) { }
        return findByName(value);
    }

    /** Group names for tab completion. Names Brigadier cannot read as a single plain word are quoted. */
    List<String> names() {
        return groups.values().stream()
                .map(SavedGroup::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(name -> ArgParser.needsQuotes(name) ? "\"" + name + "\"" : name)
                .toList();
    }

    // ---- Auto-join ----

    UUID personalAutoJoin(UUID player) {
        return personalAutoJoin.get(player);
    }

    void setPersonalAutoJoin(UUID player, UUID group) {
        personalAutoJoin.put(player, group);
    }

    void removePersonalAutoJoin(UUID player) {
        personalAutoJoin.remove(player);
    }

    UUID globalAutoJoin() {
        return globalAutoJoin;
    }

    void setGlobalAutoJoin(UUID group) {
        globalAutoJoin = group;
    }

    boolean forceGlobalAutoJoin() {
        return forceGlobalAutoJoin;
    }

    void setForceGlobalAutoJoin(boolean value) {
        forceGlobalAutoJoin = value;
    }

    // ---- Loading and saving ----

    void load() {
        File dataFolder = plugin.getDataFolder();
        YamlConfiguration persistent = YamlConfiguration.loadConfiguration(new File(dataFolder, "persistent-groups.yml"));
        ConfigurationSection section = persistent.getConfigurationSection("groups");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    ConfigurationSection g = section.getConfigurationSection(key);
                    if (g == null) continue;
                    groups.put(id, new SavedGroup(id, g.getString("name", ""), g.getString("password"),
                            g.getString("type", "NORMAL"), g.getBoolean("hidden")));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Ignoring invalid persistent group id: " + key);
                }
            }
        }

        YamlConfiguration autoJoin = YamlConfiguration.loadConfiguration(new File(dataFolder, "auto-join-groups.yml"));
        ConfigurationSection personal = autoJoin.getConfigurationSection("players");
        if (personal != null) {
            for (String player : personal.getKeys(false)) {
                try {
                    String group = personal.getString(player);
                    if (group == null) throw new IllegalArgumentException("missing group");
                    personalAutoJoin.put(UUID.fromString(player), UUID.fromString(group));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Ignoring invalid auto-join entry for " + player);
                }
            }
        }
        globalAutoJoin = parseUuid(autoJoin.getString("global_group"));
        forceGlobalAutoJoin = autoJoin.getBoolean("force_global", false);
        loaded = true;
    }

    /**
     * Saves soon, on a background thread. Several changes in a row (for example a burst of commands)
     * are written together, and the server thread never waits for the disk.
     */
    void requestSave() {
        if (!loaded) return;
        if (!plugin.isEnabled()) {
            saveNow();
            return;
        }
        if (saveQueued.compareAndSet(false, true)) {
            try {
                plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                    saveQueued.set(false);
                    saveNow();
                }, 20L);
            } catch (RuntimeException e) {
                saveQueued.set(false);
                saveNow();
            }
        }
    }

    /** Writes everything to disk right now. Used on shutdown and by the background save. */
    synchronized void saveNow() {
        if (!loaded) return;

        YamlConfiguration persistent = new YamlConfiguration();
        groups.values().stream()
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

        File dataFolder = plugin.getDataFolder();
        atomicSave(persistent, new File(dataFolder, "persistent-groups.yml"));
        atomicSave(autoJoin, new File(dataFolder, "auto-join-groups.yml"));
    }

    private void atomicSave(YamlConfiguration config, File target) {
        File temp = new File(plugin.getDataFolder(), target.getName() + ".tmp");
        try {
            plugin.getDataFolder().mkdirs();
            config.save(temp);
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save " + target.getName() + ": " + e.getMessage());
            temp.delete();
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
