# Voice Group Manager

Voice Group Manager adds server-side management for [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) groups: instant groups for nearby players, persistent groups that survive restarts, and automatic group joining.

## Requirements

- Minecraft **26.3** server. The plugin is built for **Purpur 26.3 build 2643**.
- **Java 25**.
- The **Bukkit** version of Simple Voice Chat **2.6.24 or newer**, installed on the server.
- Paper 26.3 may be compatible, but has not been tested separately. Spigot is not a supported target.

Players do not install Voice Group Manager. Players who want to use voice chat still need a compatible Simple Voice Chat client; clients using SVC-supported mod loaders can connect. Players without the SVC client can join Minecraft, but cannot use voice chat or voice-group features.

## Download and install

1. Install the **Bukkit server plugin** version of [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat), version **2.6.24 or newer**, for your Minecraft server version. It is a required dependency; the client mod by itself is not enough on the server.
2. Download the Voice Group Manager JAR from this project's Modrinth page or GitHub **Releases**. If no release is available, download the `VoiceGroupManager-...` artifact from the latest successful **Build plugin** run under **Actions** and extract the JAR from the ZIP.
3. Put both plugin JARs in the server's `plugins` folder.
4. Start or restart the server. Voice Group Manager loads after Simple Voice Chat.
5. Edit `plugins/VoiceGroupManager/config.yml` and `messages.yml`, then run `/voicegroupmanager reload` to apply changes.

The plugin keeps its configuration, messages, saved groups, and auto-join data in `plugins/VoiceGroupManager/`.

> **Upgrading from an earlier build under a previous name?** Stop the server, replace the old JAR with the new one, and move `config.yml`, `messages.yml`, `persistent-groups.yml` and `auto-join-groups.yml` into `plugins/VoiceGroupManager/`. Permission nodes now start with `voicegroupmanager.`, so update them in your permissions plugin.

## Features

- Create temporary groups with nearby voice-connected players.
- Save persistent groups and restore them after server restarts.
- Set personal or server-wide auto-join groups.
- Move another voice-connected player into your current group.
- Optionally force the group type used when players create groups through the SVC GUI.
- Show players a join summary for visible voice groups when they enter the server.
- Fully editable chat messages and prefix, reloadable without a restart.

## Commands

| Command | What it does |
|---|---|
| `/instantgroup [range]` | Add nearby voice-connected players who are not already in a group to your current group. If you are not in a group, create an open temporary group. The range is a radius in blocks and defaults to 128. |
| `/persistentgroup list` | List saved persistent groups. |
| `/persistentgroup add <name> [normal\|open\|isolated] [true\|false] [password]` | Create a persistent group. The optional `true`/`false` sets whether the group is hidden. Quote names with spaces, for example `/persistentgroup add "Staff Chat" open true`. Names must be unique. |
| `/persistentgroup remove <name\|id>` | Remove a saved group, if Simple Voice Chat allows it. Any auto-join settings pointing at it are cleared. |
| `/autojoingroup set <name> [password]` | Set your personal auto-join group. |
| `/autojoingroup remove` | Clear your personal auto-join setting. |
| `/autojoingroup global set <name>` | Set the server-wide auto-join group. The group must not have a password. |
| `/autojoingroup global remove` | Clear the server-wide auto-join group. |
| `/autojoingroup global force <true\|false>` | Choose whether the server-wide setting overrides personal settings. |
| `/forcejoingroup <player>` | Move an online player with an SVC connection into your current voice group. |
| `/voicegroupmanager reload` (alias `/vgm reload`) | Reload `config.yml` and `messages.yml`. |

The group types are Simple Voice Chat's `normal`, `open`, and `isolated` types. In `config.yml`, `force_group_type` can be `OFF`, `NORMAL`, `OPEN`, or `ISOLATED`; it applies to groups created through the SVC GUI, not groups explicitly created with `/persistentgroup add`.

## Permissions and configuration

Permission nodes:

- `voicegroupmanager.instantgroup`
- `voicegroupmanager.persistentgroup`
- `voicegroupmanager.autojoingroup`
- `voicegroupmanager.autojoingroup.global`
- `voicegroupmanager.forcejoingroup`
- `voicegroupmanager.reload`
- `voicegroupmanager.admin` — grants all of the nodes above, whatever the defaults in `config.yml` say.

The default permission level of each command node is configurable in `config.yml`: `EVERYONE`, `OPS`, or `NOONE`. A permission plugin can also grant individual nodes. The same file controls the instant-group range and name, group summaries, and forced group type. All text, including the chat prefix, can be changed in `messages.yml`; any key you remove falls back to the built-in default.

Persistent-group passwords are stored in plain text so the plugin can restore groups and use passwords for auto-join. Restrict access to the plugin data folder accordingly.

## Building

You need JDK 25 and Gradle 9.5.1 or newer:

```
gradle clean build
```

The plugin JAR is written to `build/libs/VoiceGroupManager-<version>.jar`. The GitHub Actions workflow builds it the same way on every push.

## Credits

Inspired by an existing Fabric mod for managing Simple Voice Chat groups; thanks to its original author. See the [original project](https://modrinth.com/mod/enhanced-groups). Voice Group Manager is a separate server-side plugin and players do not need that mod.
