# Voice Group Manager

Voice Group Manager adds server-side management for [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) groups: instant groups for nearby players, persistent groups that survive restarts, and automatic group joining.

## Requirements

- Minecraft **26.3** server.
- The **Bukkit** version of Simple Voice Chat **2.6.24 or newer**, installed on the server.
- Spigot is not a supported target.

Players do not install Voice Group Manager. Players who want to use voice chat still need a compatible Simple Voice Chat client; clients using SVC-supported mod loaders can connect. Players without the SVC client can join Minecraft, but cannot use voice chat or voice-group features.

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

## License & Credits

* Ported to Paper by **SilkyPanda** with permission from the original author.
* Original Fabric mod: [Simple Voice Chat Enhanced Groups](https://github.com/henkelmax/enhanced-groups) by **Max Henkel (henkelmax)**.
* Licensed under the **GNU General Public License v3.0** (GPLv3). See the `LICENSE` file for details.
