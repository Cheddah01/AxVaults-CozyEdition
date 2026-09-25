# AxVaults — Cozy Edition

## One JAR for Paper 26.2 and 26.3

Version **2.16.3-cozy.1** uses Java 25 and is compiled against Paper
26.2 build 117. The same compiled classes pass the regression suite on Paper
26.3 build 40 alpha. Install the shaded `target/AxVaults-2.16.3-cozy.1.jar`.
The plugin name and data folder remain **AxVaults**; retain your existing files.

This fork is based on [upstream 2.16.0, commit 08b7fee](https://github.com/Artillex-Studios/AxVaults/commit/08b7fee8b4b652b5bd7105920dcf0e9e807bdc54),
which was still upstream master when checked on 2026-09-25. No upstream merge
was needed. The custom upgrade prompt and save/shutdown changes remain intact.
Stock update notifications are disabled even if the old configuration enables
them. Update from this fork so those customizations are retained.

AxAPI 2.2.0-DEV-NEW-VERSION-5 remains unchanged. It selects and downloads the
matching official 26.2 or 26.3 native adapter at startup; both artifacts were
verified available. This is normal library loading, not a replacement of your
fork JAR. First startup needs access to the existing library repositories.

Automated checks cover selector permissions/promotion placement, queued saves,
failed saves, and H2/SQLite storage close/reopen preservation. They do not run
the full server item serializer, native adapter, or MySQL. Before production:

1. Back up `plugins/AxVaults`, stop the server, replace the old AxVaults JAR
   (keep only one), retain the data folder, and restart.
2. Check `/pv` and your Unlock More Vaults prompt. Confirm permissions and
   configured vault counts are unchanged.
3. Store named/enchanted items and a container item with contents; reopen the
   vault, restart, and verify exact contents, metadata, amounts and icons.
4. On 26.3, repeat with a new 26.3 material. Do not move that upgraded vault
   database back to 26.2: a shared plugin JAR does not make new items downgrade-safe.
5. Check startup for adapter/library errors and saving for database errors.

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
