package dev.voicegroupmanager;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.util.Locale;
import java.util.Map;

final class Permissions {
    static final String INSTANT = "voicegroupmanager.instantgroup";
    static final String PERSISTENT = "voicegroupmanager.persistentgroup";
    static final String AUTOJOIN = "voicegroupmanager.autojoingroup";
    static final String AUTOJOIN_GLOBAL = "voicegroupmanager.autojoingroup.global";
    static final String FORCEJOIN = "voicegroupmanager.forcejoingroup";
    static final String RELOAD = "voicegroupmanager.reload";
    static final String INFO = "voicegroupmanager.info";

    private Permissions() { }

    /** Applies the default access level of each command node from the permission_defaults section of config.yml. */
    static void applyDefaults(VoiceGroupManagerPlugin plugin) {
        Map<String, String> nodes = Map.of(
                "instantgroup", INSTANT,
                "persistentgroup", PERSISTENT,
                "autojoingroup", AUTOJOIN,
                "autojoingroup_global", AUTOJOIN_GLOBAL,
                "forcejoingroup", FORCEJOIN);
        nodes.forEach((key, node) -> {
            Permission permission = plugin.getServer().getPluginManager().getPermission(node);
            if (permission == null) return;
            String setting = plugin.getConfig().getString("permission_defaults." + key, "OPS").trim().toUpperCase(Locale.ROOT);
            permission.setDefault(switch (setting) {
                case "EVERYONE" -> PermissionDefault.TRUE;
                case "NOONE" -> PermissionDefault.FALSE;
                case "OPS" -> PermissionDefault.OP;
                default -> {
                    plugin.getLogger().warning("Invalid permission_defaults." + key + " '" + setting
                            + "' in config.yml; use EVERYONE, OPS or NOONE. Using OPS.");
                    yield PermissionDefault.OP;
                }
            });
        });
    }
}
