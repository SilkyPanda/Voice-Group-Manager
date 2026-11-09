package dev.voicegroupmanager;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Tells players who join which voice groups they can join. */
final class JoinSummary implements Listener {
    private final VoiceGroupManagerPlugin plugin;

    JoinSummary(VoiceGroupManagerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("group_summary", true)) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> send(player), 20L);
    }

    private void send(Player joining) {
        VoicechatServerApi api = plugin.voicechat();
        if (api == null || !joining.isOnline()) return;
        Messages messages = plugin.messages();

        Map<UUID, Group> groups = new LinkedHashMap<>();
        int memberCount = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (Visibility.isHidden(joining, online)) continue;
            VoicechatConnection connection = api.getConnectionOf(online.getUniqueId());
            if (connection == null || !connection.isInGroup()) continue;
            Group group = connection.getGroup();
            if (group == null || group.isHidden()) continue;
            memberCount++;
            groups.putIfAbsent(group.getId(), group);
        }
        if (memberCount == 0) return;

        joining.sendMessage(messages.render(messages.template("summary_header"), "count", String.valueOf(memberCount)));
        for (Group group : groups.values()) {
            String flags = group.hasPassword() ? ", password" : "";
            Component row = messages.render(messages.template("summary_entry"), "group", group.getName())
                    .hoverEvent(HoverEvent.showText(messages.render(messages.template("summary_entry_hover"),
                            "type", SvcHook.typeName(group.getType()), "flags", flags)));
            if (!group.hasPassword()) {
                Component join = messages.render(messages.template("summary_join"))
                        .clickEvent(ClickEvent.runCommand("/voicechat join " + group.getId()))
                        .hoverEvent(HoverEvent.showText(messages.render(messages.template("summary_join_hover"),
                                "group", group.getName())));
                row = row.append(Component.text(" ")).append(join);
            }
            joining.sendMessage(row);
        }
    }
}
