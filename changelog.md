## Voice Group Manager 1.1.0

Same plugin, new name: it is now **Voice Group Manager**. This release also fixes several bugs and makes the message system actually configurable.

### ⚠️ Upgrade notes
- The data folder is now `plugins/VoiceGroupManager/` and the JAR is `VoiceGroupManager-<version>.jar`. If you used an earlier build under a previous name, move `config.yml`, `messages.yml`, `persistent-groups.yml` and `auto-join-groups.yml` into the new folder.
- Permission nodes now start with `voicegroupmanager.` (for example `voicegroupmanager.instantgroup`). Update them in your permissions plugin.
- `/instantgroup` range is now a true radius (a sphere around the player) instead of a cube, so it no longer reaches players up to ~1.7× further away diagonally.

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

### Under the hood
- Persistent groups use one ID both in-session and after restarts, removing an internal ID-mapping table.
- Removed an internal set that could slowly accumulate entries.
- Saving is now thread-safe and cleans up its temporary file on failure.
- The Simple Voice Chat dependency is checked first on startup; one command handler is shared instead of one per command.
- Replaced deprecated legacy `ChatColor` usage with Adventure.
- Added a `.gitignore`.
