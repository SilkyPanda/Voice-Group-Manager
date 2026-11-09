package dev.voicegroupmanager;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Server-side voice group management for Simple Voice Chat on Bukkit/Purpur servers. */
public final class VoiceGroupManagerPlugin extends JavaPlugin {
    static final List<String> GROUP_TYPES = List.of("normal", "open", "isolated");

    /** Bumped when config.yml gains settings; see {@link #migrateConfig()}. */
    private static final int CONFIG_VERSION = 2;

    private GroupStore store;
    private Messages messages;
    private SvcHook hook;
    private volatile String forcedType = "OFF";
    private volatile boolean notifyMoved = true;

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
        store = new GroupStore(this);
        messages = new Messages(this);
        hook = new SvcHook(this);
        loadSettings();
        store.load();
        getServer().getPluginManager().registerEvents(new JoinSummary(this), this);

        // Commands are Brigadier commands, registered when the server asks plugins for them.
        CommandTree commands = new CommandTree(store, new CommandActions(this));
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> commands.register(event.registrar()));

        service.registerPlugin(hook);
        getLogger().info("Voice Group Manager enabled; voice features are available to players connected to Simple Voice Chat.");
    }

    @Override
    public void onDisable() {
        if (store != null) store.saveNow();
        if (hook != null) hook.clear();
    }

    // ---------------------------------------------------------------------------------------------
    // Shared state
    // ---------------------------------------------------------------------------------------------

    GroupStore store() {
        return store;
    }

    Messages messages() {
        return messages;
    }

    /** The running Simple Voice Chat server API, or null while voice chat is not started. */
    VoicechatServerApi voicechat() {
        return hook == null ? null : hook.api();
    }

    /** OFF, NORMAL, OPEN or ISOLATED, from force_group_type in config.yml. */
    String forcedType() {
        return forcedType;
    }

    /** Sends a message to an online player from any thread. */
    void tell(UUID player, String key, String... replacements) {
        Runnable send = () -> {
            Player online = Bukkit.getPlayer(player);
            if (online != null) messages.send(online, key, replacements);
        };
        if (Bukkit.isPrimaryThread()) {
            send.run();
        } else if (isEnabled()) {
            getServer().getScheduler().runTask(this, send);
        }
    }

    /** Like {@link #tell}, but only when notify_moved_players is enabled. */
    void notifyMoved(UUID player, String key, String... replacements) {
        if (notifyMoved) tell(player, key, replacements);
    }

    // ---------------------------------------------------------------------------------------------
    // Settings
    // ---------------------------------------------------------------------------------------------

    /** (Re)reads config.yml and messages.yml and re-applies everything derived from them. */
    void loadSettings() {
        reloadConfig();
        migrateConfig();
        messages.load();

        String type = getConfig().getString("force_group_type", "OFF").trim().toUpperCase(Locale.ROOT);
        if (!type.equals("OFF") && !GROUP_TYPES.contains(type.toLowerCase(Locale.ROOT))) {
            getLogger().warning("Invalid force_group_type '" + type + "' in config.yml; use OFF, NORMAL, OPEN or ISOLATED. Using OFF.");
            type = "OFF";
        }
        forcedType = type;
        notifyMoved = getConfig().getBoolean("notify_moved_players", true);
        Permissions.applyDefaults(this);
    }

    /**
     * Brings an existing config.yml up to date after a plugin update: settings that are missing are added
     * with their default value (together with their comments), and comments missing from older files are
     * filled in. Values the server owner already set are never changed.
     */
    private void migrateConfig() {
        File file = new File(getDataFolder(), "config.yml");
        try (InputStream resource = getResource("config.yml")) {
            if (resource == null || !file.exists()) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8));
            YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(file);
            FileConfiguration config = getConfig();

            int added = 0;
            boolean changed = false;
            for (String path : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(path) || path.equals("config_version")) continue;
                if (!onDisk.isSet(path)) {
                    config.set(path, defaults.get(path));
                    added++;
                    changed = true;
                }
            }
            if (onDisk.getInt("config_version", 1) < CONFIG_VERSION) {
                config.set("config_version", CONFIG_VERSION);
                changed = true;
            }
            for (String path : defaults.getKeys(true)) {
                if (config.isSet(path) && config.getComments(path).isEmpty()) {
                    List<String> comments = defaults.getComments(path);
                    if (!comments.isEmpty()) {
                        config.setComments(path, comments);
                        changed = true;
                    }
                }
            }
            if (changed) saveConfig();
            if (added > 0) {
                getLogger().info("config.yml was updated: added " + added + " new setting(s) with their default values.");
            }
        } catch (IOException | RuntimeException e) {
            getLogger().warning("Could not update config.yml: " + e.getMessage());
        }
    }
}
