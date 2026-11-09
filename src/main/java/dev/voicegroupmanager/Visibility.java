package dev.voicegroupmanager;

import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

/** Helps keep vanished players from being revealed by group features. */
final class Visibility {
    private Visibility() { }

    /** True if {@code target} is hidden from {@code viewer} or flagged as vanished by a vanish plugin. */
    static boolean isHidden(Player viewer, Player target) {
        if (viewer.equals(target)) return false;
        if (!viewer.canSee(target)) return true;
        for (MetadataValue value : target.getMetadata("vanished")) {
            if (value.asBoolean()) return true;
        }
        return false;
    }
}
