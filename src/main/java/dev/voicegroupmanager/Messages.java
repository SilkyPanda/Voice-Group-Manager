package dev.voicegroupmanager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/** Loads messages.yml and turns its templates into chat components. */
final class Messages {
    static final String DEFAULT_PREFIX = "&8[&aVoice Group Manager&8] &r";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "<reset><black>"), Map.entry('1', "<reset><dark_blue>"),
            Map.entry('2', "<reset><dark_green>"), Map.entry('3', "<reset><dark_aqua>"),
            Map.entry('4', "<reset><dark_red>"), Map.entry('5', "<reset><dark_purple>"),
            Map.entry('6', "<reset><gold>"), Map.entry('7', "<reset><gray>"),
            Map.entry('8', "<reset><dark_gray>"), Map.entry('9', "<reset><blue>"),
            Map.entry('a', "<reset><green>"), Map.entry('b', "<reset><aqua>"),
            Map.entry('c', "<reset><red>"), Map.entry('d', "<reset><light_purple>"),
            Map.entry('e', "<reset><yellow>"), Map.entry('f', "<reset><white>"),
            Map.entry('k', "<obfuscated>"), Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"), Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"), Map.entry('r', "<reset>"));

    private final VoiceGroupManagerPlugin plugin;
    private volatile YamlConfiguration messages;
    private volatile boolean miniMessage;

    Messages(VoiceGroupManagerPlugin plugin) {
        this.plugin = plugin;
    }

    /** (Re)reads the message format from config.yml and the texts from messages.yml. */
    void load() {
        String format = plugin.getConfig().getString("message_format", "LEGACY").trim().toUpperCase(Locale.ROOT);
        if (!format.equals("LEGACY") && !format.equals("MINIMESSAGE")) {
            plugin.getLogger().warning("Invalid message_format '" + format + "' in config.yml; use LEGACY or MINIMESSAGE. Using LEGACY.");
            format = "LEGACY";
        }
        miniMessage = format.equals("MINIMESSAGE");

        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        try (InputStream resource = plugin.getResource("messages.yml")) {
            if (resource != null) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(resource, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read bundled messages.yml defaults: " + e.getMessage());
        }
        messages = loaded;
    }

    String template(String key) {
        YamlConfiguration source = messages;
        String value = source == null ? null : source.getString(key);
        return value != null ? value : "&c[missing message: " + key + "]";
    }

    /**
     * Renders a template. Placeholders such as {group} are replaced with plain text, so player-supplied
     * values (for example group names) can never inject colour codes or MiniMessage tags.
     */
    Component render(String template, String... replacements) {
        Component component = miniMessage
                ? MINI.deserialize(legacyToMiniMessage(template))
                : LEGACY.deserialize(template);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            String token = "{" + replacements[i] + "}";
            Component value = Component.text(replacements[i + 1]);
            component = component.replaceText(builder -> builder.matchLiteral(token).replacement(value));
        }
        return component;
    }

    /** The prefix followed by the message for {@code key}. */
    Component component(String key, String... replacements) {
        YamlConfiguration source = messages;
        String prefix = source == null ? DEFAULT_PREFIX : source.getString("prefix", DEFAULT_PREFIX);
        return render(prefix + template(key), replacements);
    }

    void send(CommandSender sender, String key, String... replacements) {
        sender.sendMessage(component(key, replacements));
    }

    /**
     * Converts '&' colour codes (and &#rrggbb) to MiniMessage tags, so one messages.yml can mix both styles
     * when message_format is MINIMESSAGE.
     */
    static String legacyToMiniMessage(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == '#' && isHex(text, i + 2, 6)) {
                    out.append("<reset><#").append(text, i + 2, i + 8).append('>');
                    i += 7;
                    continue;
                }
                String tag = LEGACY_TAGS.get(Character.toLowerCase(next));
                if (tag != null) {
                    out.append(tag);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean isHex(String text, int from, int length) {
        if (from + length > text.length()) return false;
        for (int i = from; i < from + length; i++) {
            if (Character.digit(text.charAt(i), 16) < 0) return false;
        }
        return true;
    }
}
