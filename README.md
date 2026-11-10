# Voice Group Manager

Voice Group Manager adds server-side management for [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) groups: instant groups for nearby players, persistent groups that survive restarts, and automatic group joining.

## Requirements

- A **Paper** or **Purpur** server running Minecraft **1.21.1 through 26.3**.
- The **Bukkit** version of Simple Voice Chat **2.6.24 or newer**, installed on the server.
- Spigot and Folia are not supported.

Players do not install Voice Group Manager. Players who want to use voice chat still need a compatible Simple Voice Chat client; clients using SVC-supported mod loaders can connect. Players without the SVC client can join Minecraft, but cannot use voice chat or voice-group features.

## Features

- Create temporary groups with nearby voice-connected players.
- Save persistent groups and restore them after server restarts.
- Set personal or server-wide auto-join groups.
- Edit a persistent group's name, type, visibility or password without recreating it.
- Move a voice-connected player into any running voice group, from in-game or the console.
- See at a glance which voice groups are in use with `/voicegroupmanager info` and `groups`.
- Optionally force the group type used when players create groups through the SVC GUI.
- Native Brigadier commands with live hints, typed arguments and tab completion.
- Show players a join summary of visible voice groups, with click-to-join buttons, when they enter the server.
- Tell players when they were moved into a group, and never pull vanished players into one.
- Fully editable chat messages and prefix, reloadable without a restart. Supports `&` colour codes or MiniMessage.

## Commands

Group names that contain anything other than letters, numbers and `_ - . +` (spaces included) must be put in double quotes, for example `"Staff Chat"` or `"Team!"`.

| Command | What it does |
|---|---|
| `/instantgroup [range]` | Add nearby voice-connected players who are not already in a group to your current group. If you are not in a group, create an open temporary group. The range is a radius in blocks and defaults to 128; the largest allowed range is set by `max_instant_group_range`. Vanished players are skipped. Operators only by default. |
| `/persistentgroup list` | List saved persistent groups. Click a line to copy that group's ID. |
| `/persistentgroup add <name> <normal\|open\|isolated> <true\|false> [password]` | Create a persistent group. The type and the `true`/`false` hidden flag are required, in that order; the password is optional and can be any text, including spaces. For example `/persistentgroup add "Staff Chat" open false` or `/persistentgroup add Staff isolated true hunter2`. Names must be unique. |
| `/persistentgroup edit <name\|id> <name\|type\|hidden\|password> <value>` | Change one property of a saved group. Use `none` as the value to clear the password. Online members stay in the group. |
| `/persistentgroup remove <name\|id>` | Remove a saved group, if Simple Voice Chat allows it. Any auto-join settings pointing at it are cleared. |
| `/autojoingroup set <name> [password]` | Set your personal auto-join group. |
| `/autojoingroup remove` | Clear your personal auto-join setting. |
| `/autojoingroup global set <name>` | Set the server-wide auto-join group. The group must not have a password. |
| `/autojoingroup global remove` | Clear the server-wide auto-join group. |
| `/autojoingroup global force <true\|false>` | Choose whether the server-wide setting overrides personal settings. |
| `/forcejoingroup <player> <group name or ID>` | Move an online player with an SVC connection into a running voice group, by saved-group name, group ID, or the name of a group that is currently in use. Works from the console too. The player's password check is skipped. |
| `/voicegroupmanager reload` (alias `/vgm reload`) | Reload `config.yml` and `messages.yml`. |
| `/voicegroupmanager info` | Show the plugin version, voice chat status, saved/running persistent groups, the global auto-join group and the forced group type. |
| `/voicegroupmanager groups` | List the voice groups that online players are currently in, including hidden ones. Click a line to copy the group ID. |

The group types are Simple Voice Chat's `normal`, `open`, and `isolated` types. In `config.yml`, `force_group_type` can be `OFF`, `NORMAL`, `OPEN`, or `ISOLATED`; it applies to groups created through the SVC GUI, not groups explicitly created with `/persistentgroup add`.

### How auto-join works

Auto-join puts a player into a persistent group automatically each time they connect to Simple Voice Chat (that is, when their voice chat connects after joining the server).

- **Personal:** `/autojoingroup set <group> [password]` sets your own group. If the group has a password, give it once when you set it. `/autojoingroup remove` clears it.
- **Global:** `/autojoingroup global set <group>` sets a group for everyone. It must not have a password. `/autojoingroup global remove` clears it.
- **Force:** `/autojoingroup global force true` makes the global group override personal settings; with `false` (the default) a personal setting wins.

When a player connects, the plugin picks the first running group from: the global group if force is on, then their personal group, then the global group. A group that isn't running is skipped. Only persistent groups can be used, players who don't use Simple Voice Chat are not affected, and players are told when they are moved unless `notify_moved_players` is `false`.

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

The default permission level of each command node is configurable in `config.yml`: `EVERYONE`, `OPS`, or `NOONE`. A permission plugin can also grant individual nodes. The same file controls the instant-group range, its maximum and name, group summaries, whether moved players are notified, the forced group type and the message format.

All text, including the chat prefix, can be changed in `messages.yml`; any key you remove falls back to the built-in default. By default messages use `&` colour codes. Set `message_format: MINIMESSAGE` in `config.yml` to use [MiniMessage](https://docs.advntr.dev/minimessage/format.html) tags such as `<green>` or `<gradient:red:blue>` instead; `&` codes keep working in that mode.

Persistent-group passwords are stored in plain text so the plugin can restore groups and use passwords for auto-join. Restrict access to the plugin data folder accordingly. Passwords typed into commands can also appear in your server console and command logs.


## License & Credits

* Ported to Paper by **SilkyPanda** with permission from the original author.
* Original Fabric mod: [Simple Voice Chat Enhanced Groups](https://github.com/henkelmax/enhanced-groups) by **Max Henkel (henkelmax)**.
* Licensed under the **GNU General Public License v3.0** (GPLv3). See the `LICENSE` file for details.
