# Voice Group Manager

Voice Group Manager adds server-side management for [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) groups: instant groups for nearby players, persistent groups that survive restarts, and automatic group joining.

## Requirements

- A **Paper** or **Purpur** server running Minecraft **1.21.1 through 26.3**.
- **Java 21** or newer for Minecraft 1.21.x servers, **Java 25** for Minecraft 26.x servers (the Java your server already requires).
- The **Bukkit** version of Simple Voice Chat **2.6.24 or newer**, installed on the server, in a build that supports your Minecraft version.
- Spigot and Folia are not supported.

Players do not install Voice Group Manager. Players who want to use voice chat still need a compatible Simple Voice Chat client; clients using SVC-supported mod loaders can connect. Players without the SVC client can join Minecraft, but cannot use voice chat or voice-group features.

## Features

- Create temporary groups with nearby voice-connected players.
- Save persistent groups and restore them after server restarts.
- Set personal or server-wide auto-join groups.
- Edit a persistent group's name, type, visibility or password without recreating it.
- Move another voice-connected player into your current group.
- See at a glance which voice groups are in use with `/voicegroupmanager info` and `groups`.
- Optionally force the group type used when players create groups through the SVC GUI.
- Show players a join summary of visible voice groups, with click-to-join buttons, when they enter the server.
- Tell players when they were moved into a group, and never pull vanished players into one.
- Fully editable chat messages and prefix, reloadable without a restart. Supports `&` colour codes or MiniMessage.

## Commands

| Command | What it does |
|---|---|
| `/instantgroup [range]` | Add nearby voice-connected players who are not already in a group to your current group. If you are not in a group, create an open temporary group. The range is a radius in blocks and defaults to 128; the largest allowed range is set by `max_instant_group_range`. Vanished players are skipped. Operators only by default. |
| `/persistentgroup list` | List saved persistent groups. Click a line to copy that group's ID. |
| `/persistentgroup add <name> [normal\|open\|isolated] [true\|false] [password]` | Create a persistent group. The optional `true`/`false` sets whether the group is hidden. Quote names with spaces, for example `/persistentgroup add "Staff Chat" open true`. Names must be unique. A quoted word is never treated as a type or the hidden flag, so `"true"` can be used as a password. |
| `/persistentgroup edit <name\|id> <name\|type\|hidden\|password> <value>` | Change one property of a saved group. Use `none` as the value to clear the password. Online members stay in the group. |
| `/persistentgroup remove <name\|id>` | Remove a saved group, if Simple Voice Chat allows it. Any auto-join settings pointing at it are cleared. |
| `/autojoingroup set <name> [password]` | Set your personal auto-join group. |
| `/autojoingroup remove` | Clear your personal auto-join setting. |
| `/autojoingroup global set <name>` | Set the server-wide auto-join group. The group must not have a password. |
| `/autojoingroup global remove` | Clear the server-wide auto-join group. |
| `/autojoingroup global force <true\|false>` | Choose whether the server-wide setting overrides personal settings. |
| `/forcejoingroup <player>` | Move an online player with an SVC connection into your current voice group. |
| `/voicegroupmanager reload` (alias `/vgm reload`) | Reload `config.yml` and `messages.yml`. |
| `/voicegroupmanager info` | Show the plugin version, voice chat status, saved/running persistent groups, the global auto-join group and the forced group type. |
| `/voicegroupmanager groups` | List the voice groups that online players are currently in, including hidden ones. Click a line to copy the group ID. |

The group types are Simple Voice Chat's `normal`, `open`, and `isolated` types. In `config.yml`, `force_group_type` can be `OFF`, `NORMAL`, `OPEN`, or `ISOLATED`; it applies to groups created through the SVC GUI, not groups explicitly created with `/persistentgroup add`.

## Permissions and configuration

Permission nodes:

- `voicegroupmanager.instantgroup`
- `voicegroupmanager.persistentgroup`
- `voicegroupmanager.autojoingroup`
- `voicegroupmanager.autojoingroup.global`
- `voicegroupmanager.forcejoingroup`
- `voicegroupmanager.reload`
- `voicegroupmanager.info`
- `voicegroupmanager.admin` — grants all of the nodes above, whatever the defaults in `config.yml` say.

The default permission level of each command node is configurable in `config.yml`: `EVERYONE`, `OPS`, or `NOONE`. `/instantgroup` is `OPS` by default because it moves nearby players into a group. A permission plugin can also grant individual nodes. The same file controls the instant-group range, its maximum and name, group summaries, whether moved players are notified, the forced group type and the message format. After an update, new settings are added to your `config.yml` automatically with their defaults; your own values are never changed.

All text, including the chat prefix, can be changed in `messages.yml`; any key you remove falls back to the built-in default. By default messages use `&` colour codes. Set `message_format: MINIMESSAGE` in `config.yml` to use [MiniMessage](https://docs.advntr.dev/minimessage/format.html) tags such as `<green>` or `<gradient:red:blue>` instead; `&` codes keep working in that mode.

Persistent-group passwords are stored in plain text so the plugin can restore groups and use passwords for auto-join. Restrict access to the plugin data folder accordingly. Passwords typed into commands can also appear in your server console and command logs.

## Building and testing

The plugin is one jar for every supported version. It is compiled for Java 21 against the Paper 1.21.1 API, so it cannot use anything that is missing on the oldest supported version.

```
./gradlew build                          # jar in build/libs
./gradlew runServer -PmcVersion=1.21.1   # start a Paper test server with Simple Voice Chat + this plugin
./gradlew runServer -PmcVersion=26.3
```

Each version gets its own folder under `run/`. On the first start, accept the [Minecraft EULA](https://aka.ms/MinecraftEULA) in that folder's `eula.txt`.

## License & Credits

* Ported to Paper by **SilkyPanda** with permission from the original author.
* Original Fabric mod: [Simple Voice Chat Enhanced Groups](https://github.com/henkelmax/enhanced-groups) by **Max Henkel (henkelmax)**.
* Licensed under the **GNU General Public License v3.0** (GPLv3). See the `LICENSE` file for details.