# Voice Group Manager

An unofficial **Paper & Purpur** port of [Simple Voice Chat Enhanced Groups](https://github.com/henkelmax/enhanced-groups) — bringing server-side management for [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) groups to Servers: persistent groups, automatic group joining, instant nearby groups, and admin tools.

## Requirements

- **Server:** **Paper** or **Purpur** running Minecraft **1.21.1 through 26.3** *(Spigot and Folia are not supported)*
- **Dependency:** The **Bukkit** version of [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)

## Features

- **Persistent Groups:** Save groups across server restarts and edit their name, type, visibility, or password in place without recreating them or kicking current members.
- **Personal & Global Auto-Join:** Let players choose their own auto-join group, or set a server-wide default (with an option to force-override personal choices).
- **Instant Nearby Groups:** Group nearby voice-connected players in one command (`/instantgroup`) — ideal for events or impromptu conversations.
- **Force Join:** Move any voice-connected player into any running group from in-game or the console (`/forcejoingroup`).
- **Join Summary:** Greet joining players with a list of active, visible voice groups and one-click `[Join]` buttons.
- **Forced Group Types:** Optionally enforce `NORMAL`, `OPEN`, or `ISOLATED` on groups.
- **Live Admin Overview:** Inspect active groups via `/voicegroupmanager info` and `/voicegroupmanager groups`.

## Commands

| Command | What it does |
|---|---|
| `/instantgroup [range]` | Add nearby voice-connected players who are not already in a group to your current group, or create an open temporary group if you are not in one. Radius in blocks defaults to `128` (capped by `max_instant_group_range`). Vanished players are skipped. Ops only by default. |
| `/persistentgroup list` | List saved persistent groups. |
| `/persistentgroup add <name> <normal\|open\|isolated> <true\|false> [password]` | Create a persistent group. The password is optional and may contain spaces (e.g. `/persistentgroup add "Staff Chat" open false` or `/persistentgroup add Staff isolated true hunter2`). Names must be unique. |
| `/persistentgroup edit <name\|id> <name\|type\|hidden\|password> <value>` | Edit one property of a saved group without losing online members. Use `none` as the value to remove a password. |
| `/persistentgroup remove <name\|id>` | Remove a saved group (if Simple Voice Chat allows it) and clear any auto-join settings pointing to it. |
| `/autojoingroup set <name> [password]` | Set your personal auto-join group. |
| `/autojoingroup remove` | Clear your personal auto-join setting. |
| `/autojoingroup global set <name>` | Set the server-wide auto-join group. The target group must not have a password. |
| `/autojoingroup global remove` | Clear the server-wide auto-join group. |
| `/autojoingroup global force <true\|false>` | Choose whether the server-wide auto-join group overrides personal settings. |
| `/forcejoingroup <player> <group name or ID>` | Move an online, voice-connected player into a running voice group by saved name, group ID, or active group name. Works from the console and skips password checks. |
| `/voicegroupmanager reload` *(alias `/vgm reload`)* | Reload `config.yml` and `messages.yml`. |
| `/voicegroupmanager info` | Display plugin version, voice chat status, saved/running persistent groups, global auto-join group, and forced group type. |
| `/voicegroupmanager groups` | List all voice groups currently in use by online players, including hidden groups. Click a line to copy its group ID. |

The group types match Simple Voice Chat's `normal`, `open`, and `isolated` modes. In `config.yml`, `force_group_type` can be set to `OFF`, `NORMAL`, `OPEN`, or `ISOLATED`; it applies to groups created through the SVC client GUI and does not override `/persistentgroup add`.

### How Auto-Join Works

Auto-join automatically moves a player into a persistent group each time their Simple Voice Chat client connects after joining the server.

- **Personal:** `/autojoingroup set <group> [password]` sets your personal group (if password-protected, enter the password once when setting it). `/autojoingroup remove` clears it.
- **Global:** `/autojoingroup global set <group>` sets a server-wide default group (cannot have a password). `/autojoingroup global remove` clears it.
- **Priority Order:** When a player connects, the plugin picks the first running group from:
  1. Global group *(if `/autojoingroup global force true` is enabled)*
  2. Personal group
  3. Global group *(fallback)*

Groups that are not currently running are skipped. Players without the Simple Voice Chat mod are unaffected, and moved players are notified unless `notify_moved_players` is `false`.

## Permissions & Configuration

### Permission Nodes

- `voicegroupmanager.instantgroup` — Use `/instantgroup`
- `voicegroupmanager.persistentgroup` — Use `/persistentgroup`
- `voicegroupmanager.autojoingroup` — Use `/autojoingroup` for yourself
- `voicegroupmanager.autojoingroup.global` — Use `/autojoingroup global`
- `voicegroupmanager.forcejoingroup` — Use `/forcejoingroup`
- `voicegroupmanager.reload` — Use `/voicegroupmanager reload`
- `voicegroupmanager.info` — Use `/voicegroupmanager info` and `groups`
- `voicegroupmanager.admin` — Grants all permissions above, regardless of `config.yml` defaults

### Configuration (`config.yml` & `messages.yml`)

- **Permission Defaults:** Configure default access for command nodes in `config.yml` as `EVERYONE`, `OPS`, or `NOONE` (permission plugins like LuckPerms can still override individual nodes).
- **Custom Messages:** All text and the chat prefix can be edited in `messages.yml` (deleted keys fall back to built-in defaults). Legacy `&` colour codes are used by default; set `message_format: MINIMESSAGE` in `config.yml` to use [MiniMessage](https://docs.advntr.dev/minimessage/format.html) tags such as `<green>` or `<gradient:red:blue>` (`&` codes continue working in MiniMessage mode too).

> **Security note:** Persistent-group passwords are stored in plain text inside `persistent-groups.yml` so the plugin can restore groups on startup and validate auto-join settings. Restrict access to the plugin data folder accordingly, and keep in mind that passwords typed into commands may appear in your server console and command logs.

## License & Credits

- Ported to Paper by **SilkyPanda** with permission from the original author.
- Original Fabric mod: [Simple Voice Chat Enhanced Groups](https://github.com/henkelmax/enhanced-groups) by **Max Henkel (henkelmax)**.
- Licensed under the **GNU General Public License v3.0** (`GPLv3`). See the `LICENSE` file for details.
