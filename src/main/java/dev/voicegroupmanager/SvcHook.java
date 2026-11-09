package dev.voicegroupmanager;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Connects the plugin to Simple Voice Chat: restores groups, auto-joins players and forces group types. */
final class SvcHook implements VoicechatPlugin {
    private final VoiceGroupManagerPlugin plugin;
    private final AtomicBoolean passwordReadWarned = new AtomicBoolean();
    private volatile VoicechatServerApi api;

    SvcHook(VoiceGroupManagerPlugin plugin) {
        this.plugin = plugin;
    }

    /** The running voice chat server, or null while voice chat is not started. */
    VoicechatServerApi api() {
        return api;
    }

    void clear() {
        api = null;
    }

    static Group.Type groupType(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "OPEN" -> Group.Type.OPEN;
            case "ISOLATED" -> Group.Type.ISOLATED;
            default -> Group.Type.NORMAL;
        };
    }

    /** Builds the live voice group for a saved one. Its voice-chat ID is always the saved ID. */
    static Group build(VoicechatServerApi api, SavedGroup saved) {
        return api.groupBuilder()
                .setPersistent(true)
                .setName(saved.name())
                .setPassword(saved.password())
                .setType(groupType(saved.type()))
                .setHidden(saved.hidden())
                .setId(saved.id())
                .build();
    }

    @Override
    public String getPluginId() {
        return "voicegroupmanager";
    }

    @Override
    public void initialize(VoicechatApi api) { }

    @Override
    public void registerEvents(EventRegistration events) {
        events.registerEvent(VoicechatServerStartedEvent.class, this::started);
        events.registerEvent(VoicechatServerStoppedEvent.class, event -> api = null);
        events.registerEvent(PlayerConnectedEvent.class, this::playerConnected);
        events.registerEvent(CreateGroupEvent.class, this::createGroup);
    }

    private void started(VoicechatServerStartedEvent event) {
        VoicechatServerApi started = event.getVoicechat();
        api = started;
        int restored = 0;
        for (SavedGroup saved : plugin.store().all()) {
            try {
                build(started, saved);
                restored++;
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Could not restore persistent group '" + saved.name() + "': " + e.getMessage());
            }
        }
        plugin.getLogger().info("Restored " + restored + " persistent voice group(s).");
    }

    private void playerConnected(PlayerConnectedEvent event) {
        UUID player = event.getConnection().getPlayer().getUuid();
        Group group = autoJoinGroup(event.getVoicechat(), player);
        if (group == null) return;
        event.getConnection().setGroup(group);
        plugin.notifyMoved(player, "moved_autojoin", "group", group.getName());
    }

    /**
     * The group a connecting player should join: the forced global group, else their personal group, else the
     * global group. A group that is not currently running is skipped, so the next option is tried instead.
     */
    private Group autoJoinGroup(VoicechatServerApi voicechat, UUID player) {
        GroupStore store = plugin.store();
        UUID global = store.globalAutoJoin();
        if (store.forceGlobalAutoJoin() && global != null) {
            Group forced = voicechat.getGroup(global);
            if (forced != null) return forced;
        }
        UUID personal = store.personalAutoJoin(player);
        if (personal != null) {
            Group own = voicechat.getGroup(personal);
            if (own != null) return own;
        }
        return global == null ? null : voicechat.getGroup(global);
    }

    /** Replaces groups created through the Simple Voice Chat GUI with ones of the forced type. */
    private void createGroup(CreateGroupEvent event) {
        String forced = plugin.forcedType();
        // A null connection means the group was created by a plugin (including this one).
        if ("OFF".equals(forced) || event.getConnection() == null) return;
        Group original = event.getGroup();
        Group.Type desired = groupType(forced);
        if (original == null || original.getType().equals(desired)) return;

        UUID player = event.getConnection().getPlayer().getUuid();
        String password = readPassword(original);
        event.cancel();
        if (original.hasPassword() && password == null) {
            plugin.getLogger().warning("Cancelled group type replacement because the group password could not be read safely.");
            plugin.tell(player, "group_create_blocked");
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
            plugin.getLogger().warning("Could not replace group type: " + e.getMessage());
            plugin.tell(player, "group_create_blocked");
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
                plugin.getLogger().warning("Unable to read a voice group password for a forced type change"
                        + " (this Simple Voice Chat version may not be supported): " + e);
            }
        }
        return null;
    }
}
