package dev.voicegroupmanager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs code on the thread that owns an entity. On Paper and Purpur that is the main thread; on Folia it is
 * the region thread the entity is in. Paper's scheduler API behaves the same on all of them, so there is
 * one code path and no Folia detection.
 */
final class Scheduling {
    private Scheduling() { }

    static void entity(VoiceGroupManagerPlugin plugin, Entity entity, Runnable task) {
        entity(plugin, entity, task, null);
    }

    /**
     * Runs {@code task} right away if the current thread already owns the entity, otherwise on the entity's
     * own thread. {@code retired} (may be null) runs instead if the entity is removed first, for example
     * because the player left. Exactly one of the two runs, unless the plugin is disabled.
     */
    static void entity(VoiceGroupManagerPlugin plugin, Entity entity, Runnable task, Runnable retired) {
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            task.run();
            return;
        }
        if (!plugin.isEnabled()) return;
        AtomicBoolean done = new AtomicBoolean();
        Runnable onRetired = () -> {
            if (retired != null && done.compareAndSet(false, true)) retired.run();
        };
        try {
            var scheduled = entity.getScheduler().run(plugin, t -> {
                if (done.compareAndSet(false, true)) task.run();
            }, onRetired);
            if (scheduled == null) onRetired.run(); // the entity was already removed
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Could not schedule a task: " + e.getMessage());
        }
    }
}
