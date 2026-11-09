## Voice Group Manager 1.3.0

Edit persistent groups in place, see what is going on with `/vgm info`, and a round of fixes that make `/instantgroup` safer.

### ⚠️ Upgrade notes
- **`/instantgroup` is now operators-only by default.** Your existing `config.yml` keeps its old value, so if you want the new default set `permission_defaults.instantgroup: OPS` and run `/vgm reload`. You can still give the permission to anyone you like.
- New settings are added to your `config.yml` and `messages.yml` automatically; nothing you changed is touched.

### New
- `/persistentgroup edit <group> <name|type|hidden|password> <value>` changes a saved group without removing and re-adding it. Players in the group stay in it.
- `/voicegroupmanager info` shows the version, voice chat status and your group setup. `/voicegroupmanager groups` lists the voice groups currently in use.
- Click a group in `/persistentgroup list` or `/vgm groups` to copy its ID. The join summary now has hover tips.
- Players are told when they are moved into a group by `/instantgroup`, `/forcejoingroup` or auto-join (turn off with `notify_moved_players: false`).
- `message_format: MINIMESSAGE` in `config.yml` lets you use MiniMessage colours and gradients in `messages.yml`. `&` codes still work.
- New permission `voicegroupmanager.info` (operators by default).

### Fixed
- `/instantgroup` has a maximum range (`max_instant_group_range`, default 256) instead of accepting any number.
- `/instantgroup` and the join summary no longer reveal vanished players.
- Auto-join now falls back to the global group when a player's personal group isn't available.
- When a forced group type blocks a player's group, they get a message instead of nothing.
- A group password of `true` or `false` can now be used by putting it in quotes.

### Improved
- Saving groups no longer pauses the server, even on slow disks.

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