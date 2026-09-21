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
