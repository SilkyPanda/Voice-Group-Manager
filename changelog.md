## Voice Group Manager 1.3.0

Move players into any group, edit groups in place, and commands that show live hints as you type.

Upgrade notes

* `/instantgroup` is now operators-only by default. Your existing `config.yml` keeps its old value; set `permission_defaults.instantgroup: OPS` to switch.
* `/persistentgroup add` now needs a type and a `true`/`false` hidden flag before the optional password: `/persistentgroup add <name> <normal|open|isolated> <true|false> [password]`.
* `/forcejoingroup` now takes a group: `/forcejoingroup <player> <group name or ID>`. It no longer uses your current group, and it works from the console.
* Group names with symbols (anything besides letters, numbers and `_ - . +`) must be in quotes, like `"Team!"`. Subcommands must be lowercase.
* Players without permission no longer see these commands; Minecraft's "unknown command" replaces "no permission".

New

* Commands show live hints and tab-complete as you type.
* `/persistentgroup edit` changes a group's name, type, visibility or password.
* `/vgm info` and `/vgm groups`. Click a group ID to copy it.
* Players are told when they are moved into a group (`notify_moved_players`).
* MiniMessage colours: set `message_format: MINIMESSAGE`.

Fixed

* `/instantgroup` has a range limit (`max_instant_group_range`) and no longer reveals vanished players.
* Auto-join falls back to the global group when a personal group isn't available.
* Players get a message when a forced group type blocks their group.

Improved

* Saving no longer pauses the server, and new settings are added to your `config.yml` automatically.

## Voice Group Manager 1.2.0

One jar, many versions: Voice Group Manager now supports Minecraft **1.21.1 through 26.3** on Paper and Purpur.

### Upgrade notes
- Drop-in update from 1.1.0; no config changes.
- Minecraft 1.21.x servers need Java 21, 26.x servers need Java 25 (the same Java your server already needs).

### New
- Runs on Minecraft 1.21.1 up to 26.3 with a single download. Previously only 26.3 was supported.

## Voice Group Manager 1.1.0

Same plugin, new name: it is now called **Voice Group Manager**.

### ⚠️ Upgrade notes

### New
- `/voicegroupmanager reload` (alias `/vgm reload`) reloads `config.yml` and `messages.yml` without a restart. Permission: `voicegroupmanager.reload`.
- Every chat message is now configurable in `messages.yml` with `{placeholders}`. Previously only the prefix was read from the file. Keys missing from an older `messages.yml` fall back to the built-in defaults.
- `/persistentgroup add` now refuses duplicate group names, which made lookups ambiguous.
- `/persistentgroup list` is sorted by name and shows whether a group is hidden or password-protected.

### Fixed
- `voicegroupmanager.admin` now really grants every command. Before, the command-level permission check could still block admins when a node's default was set to `NOONE`.
- Saved groups that failed to restore at startup (or were saved while voice chat was offline) could never be removed. They can now be deleted.
- Removing a persistent group now clears personal and global auto-join settings that pointed at it, instead of leaving stale entries.
- Group names can no longer inject `&` colour codes into chat messages or the join summary.
- `/instantgroup` no longer leaves an empty group behind if nobody ends up in it, and falls back to 128 if `default_instant_group_range` in the config is invalid.
- Invalid `force_group_type` and `permission_defaults` values now log a clear warning instead of failing silently.
- `/autojoingroup global set <name>` now accepts names with spaces without quotes.
- Tab completion fixed: correct suggestions at every argument position, names with spaces are quoted automatically, and suggestions are hidden from players without permission.
- `/instantgroup` range is now a true radius (a sphere around the player) instead of a cube, so it no longer reaches players up to ~1.7× further away diagonally.