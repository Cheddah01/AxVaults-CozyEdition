**Bug Reports and Feature Requests:** https://github.com/Artillex-Studios/Issues

**Support:** https://dc.artillex-studios.com/

![axvaults-banner](https://github.com/Artillex-Studios/AxVaults/assets/52270269/5a8197f8-623c-418d-95cf-4fb3e32c1daf)

## Cozy Edition: vault upgrade prompt

The selector opened with `/vaults` or `/pv` shows one **Unlock More Vaults**
item after the player's last unlocked vault. Three unlocked vaults put the
prompt in position four. Other locked vault items are hidden. The prompt
moves onto the next page when necessary and is hidden when the player has
access to every vault allowed by `max-vault-amount` or has unlimited access.

Customize its material, name, glow, and lore under
`guis.selector.item-unlock-more` in `messages.yml`. It is informational and
does not run a command when clicked. Set `unlock-more-vaults: false` in
`config.yml` to restore the original locked-vault display. Reload with
`/axvaultsadmin reload` after editing these settings. Existing configuration
values and player vault data are preserved.

## Updating with PlugManX (Paper)

Version 2.16.2 closes vaults and selector/icon menus, drains pending saves,
terminates datastore/autosave workers, closes database connections, and removes
owned commands, placeholder expansions, JDBC drivers, and metrics during unload.
The plugin name and data folder remain **AxVaults**.

Install this first lifecycle update with a server restart: the old installed
version still controls its own shutdown. For later updates:

1. Run `/plugman unload AxVaults` and wait for it to finish without save errors.
2. Replace the old plugin JAR with the new build; keep only one AxVaults JAR.
   Keep the existing `plugins/AxVaults` data folder.
3. Run `/plugman load AxVaults`, then check `/pv`.

These are the [PlugManX unload/load commands](https://github.com/Test-Account666/PlugManX#commands).
Shutdown waits for database work to finish rather than discarding queued writes;
an unresponsive database can therefore delay unloading. Resolve any reported
save error before continuing an update.

Validation: automated tests exercise queued callbacks and save ordering, failed
writes, and three H2/SQLite close/reopen cycles preserving stored bytes and icons.
A live Paper/PlugManX cycle and MySQL integration still need server testing.
On a test server, leave items in an open vault, unload/load twice, and verify the
items and icon remain, menus close, `/pv` works once per click, and the console
has no database-lock, stale-task, or duplicate-command errors. Also check a
selector and icon picker left open during unload.
