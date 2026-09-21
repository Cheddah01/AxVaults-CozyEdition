package com.artillexstudios.axvaults.guis;

import com.artillexstudios.axapi.libs.boostedyaml.block.implementation.Section;
import com.artillexstudios.axapi.reflection.ClassUtils;
import com.artillexstudios.axapi.utils.Cooldown;
import com.artillexstudios.axapi.utils.ItemBuilder;
import com.artillexstudios.axapi.utils.StringUtils;
import com.artillexstudios.axvaults.AxVaults;
import com.artillexstudios.axvaults.utils.PermissionUtils;
import com.artillexstudios.axvaults.utils.ThreadUtils;
import com.artillexstudios.axvaults.vaults.Vault;
import com.artillexstudios.axvaults.vaults.VaultPlayer;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.artillexstudios.axvaults.AxVaults.CONFIG;
import static com.artillexstudios.axvaults.AxVaults.MESSAGES;
import static com.artillexstudios.axvaults.AxVaults.MESSAGEUTILS;

public class VaultSelector {
    private static final Cooldown<Player> cooldown = Cooldown.createSynchronized();
    private final Player player;
    private final VaultPlayer vaultPlayer;

    public VaultSelector(Player player, VaultPlayer vaultPlayer) {
        this.player = player;
        this.vaultPlayer = vaultPlayer;
    }

    public void open() {
        open(1);
    }

    public void open(int page) {
        if (AxVaults.isStopping()) return;
        int rows = CONFIG.getInt("vault-selector-rows", 6);
        int pageSize = rows * 9 - 9;

        String title = MESSAGES.getString("guis.selector.title");
        if (ClassUtils.INSTANCE.classExists("me.clip.placeholderapi.PlaceholderAPI")) {
            title = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, title);
        }

        final PaginatedGui gui = Gui.paginated()
                .title(StringUtils.format(title))
                .rows(rows)
                .pageSize(pageSize)
                .disableAllInteractions()
                .create();

        final boolean promote = CONFIG.getBoolean("unlock-more-vaults", true);
        final int promotionNumber = promote ? getPromotionNumber() : -1;
        final int[] loadedThrough = {pageSize * (page + 1)};
        for (int i = 0; i < loadedThrough[0]; i++) {
            getItemOfVault(player, i + 1, gui, promote, promotionNumber).thenAccept(guiItem -> {
                if (guiItem == null || AxVaults.isStopping()) return;
                ThreadUtils.runSync(player, () -> {
                    if (AxVaults.isStopping()) return;
                    gui.addItem(guiItem);
                    gui.update();
                });
            });
        }

        final Section prev;
        if ((prev = MESSAGES.getSection("gui-items.previous-page")) != null) {
            final GuiItem item1 = new GuiItem(ItemBuilder.create(prev).get());
            item1.setAction(event -> {
                if (getOrAddCooldown((Player) event.getWhoClicked())) return;
                gui.previous();
            });
            gui.setItem(rows, 3, item1);
        }

        final Section next;
        if ((next = MESSAGES.getSection("gui-items.next-page")) != null) {
            final GuiItem item2 = new GuiItem(ItemBuilder.create(next).get());
            item2.setAction(event -> {
                if (getOrAddCooldown((Player) event.getWhoClicked())) return;
                gui.next();

                int loadUntil = (gui.getCurrentPageNum() + 1) * pageSize;
                for (int num = loadedThrough[0] + 1; num <= loadUntil; num++) {
                    getItemOfVault(player, num, gui, promote, promotionNumber).thenAccept(guiItem -> {
                        if (guiItem == null || AxVaults.isStopping()) return;
                        ThreadUtils.runSync(player, () -> {
                            if (AxVaults.isStopping()) return;
                            gui.addItem(guiItem);
                            gui.update();
                        });
                    });
                }
                loadedThrough[0] = Math.max(loadedThrough[0], loadUntil);
            });
            gui.setItem(rows, 7, item2);
        }

        final Section close;
        if ((close = MESSAGES.getSection("gui-items.close")) != null) {
            final GuiItem item3 = new GuiItem(ItemBuilder.create(close).get());
            item3.setAction(event -> {
                if (getOrAddCooldown((Player) event.getWhoClicked())) return;
                event.getWhoClicked().closeInventory();
            });
            gui.setItem(rows, 5, item3);
        }

        ThreadUtils.runSync(player, () -> {
            if (AxVaults.isStopping()) return;
            gui.open(player, page);
        });
    }

    // Include separately granted vaults and saved vaults; the permission check below
    // ensures revoked permissions do not move the promotion past inaccessible vaults.
    int getPromotionNumber() {
        Set<Integer> candidates = new HashSet<>(vaultPlayer.getVaultMap().keySet());
        candidates.add(1);
        player.getEffectivePermissions().forEach(permission -> {
            if (!permission.getValue() || !permission.getPermission().startsWith("axvaults.vault.")) return;
            try {
                int number = Integer.parseInt(permission.getPermission().substring("axvaults.vault.".length()));
                if (number > 0) candidates.add(number);
            } catch (NumberFormatException ignored) {
                // Wildcards are handled by the access check beyond the highest candidate.
            }
        });
        int limit = CONFIG.getInt("max-vault-amount", -1);
        if (limit >= 0) candidates.add(limit);
        int highest = 0;
        for (int number : candidates) {
            if (number > highest && (limit < 0 || number <= limit)
                    && PermissionUtils.hasPermission(player, number)) highest = number;
        }
        if (highest == Integer.MAX_VALUE || (limit >= 0 && highest >= limit)) return -1;
        // Operators and wildcard holders can have unbounded access: no upgrade prompt.
        return PermissionUtils.hasPermission(player, highest + 1) ? -1 : highest + 1;
    }

    private CompletableFuture<GuiItem> getItemOfVault(@NotNull Player player, int num, @NotNull PaginatedGui gui,
                                                       boolean promote, int promotionNumber) {
        int maxVaults = CONFIG.getInt("max-vault-amount");
        if (maxVaults != -1 && num > maxVaults) {
            return CompletableFuture.completedFuture(null);
        }

        final HashMap<String, String> replacements = new HashMap<>();
        replacements.put("%num%", "" + num);

        Vault vault = vaultPlayer.getVault(num);
        CompletableFuture<GuiItem> cf = new CompletableFuture<>();
        AxVaults.getThreadedQueue().submit(() -> {
            if (vault != null) {
                replacements.put("%used%", "" + vault.getSlotsFilled());
                replacements.put("%max%", "" + vault.getStorage().getSize());

                final ItemBuilder builder = ItemBuilder.create(MESSAGES.getSection("guis.selector.item-owned"));
                builder.setLore(MESSAGES.getStringList("guis.selector.item-owned.lore"), replacements);
                builder.setName(MESSAGES.getString("guis.selector.item-owned.name"), replacements);

                final ItemStack it = builder.get();
                if (it.hasItemMeta()) {
                    final ItemMeta meta = it.getItemMeta();
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                    it.setItemMeta(meta);
                }

                it.setType(vault.getIcon());
                switch (CONFIG.getInt("selector-item-amount-mode", 1)) {
                    case 1 -> it.setAmount(num % 64 == 0 ? 64 : num % 64);
                    case 3 -> it.setAmount(Math.max(1, vault.getSlotsFilled()));
                }

                final GuiItem guiItem = new GuiItem(it);
                guiItem.setAction(event -> {
                    if (getOrAddCooldown((Player) event.getWhoClicked())) return;
                    if (event.isShiftClick()) {
                        if (!player.hasPermission("axvaults.itempicker")) {
                            MESSAGEUTILS.sendLang(event.getWhoClicked(), "no-permission");
                            return;
                        }
                        new ItemPicker(player, vaultPlayer).open(vault, gui.getCurrentPageNum(), 1);
                        return;
                    }

                    MESSAGEUTILS.sendLang(event.getWhoClicked(), "vault.opened", replacements);
                    vault.open(player);
                });
                cf.complete(guiItem);
            } else {
                if (promote) {
                    Section section = MESSAGES.getSection("guis.selector.item-unlock-more");
                    cf.complete(num == promotionNumber && section != null
                            ? new GuiItem(ItemBuilder.create(section).get()) : null);
                    return;
                }
                if (!CONFIG.getBoolean("show-locked-vaults", true)) {
                    cf.complete(null);
                    return;
                }

                final ItemBuilder builder = ItemBuilder.create(MESSAGES.getSection("guis.selector.item-locked"));
                builder.setLore(MESSAGES.getStringList("guis.selector.item-locked.lore"), replacements);
                builder.setName(MESSAGES.getString("guis.selector.item-locked.name"), replacements);

                final ItemStack it = builder.get();
                if (CONFIG.getInt("selector-item-amount-mode", 1) == 1) {
                    it.setAmount(num % 64 == 0 ? 64 : num % 64);
                }

                cf.complete(new GuiItem(it));
            }
        });
        return cf;
    }

    public static void clearCooldowns() {
        cooldown.clear();
    }

    private static boolean getOrAddCooldown(Player player) {
        long cooldownMillis = CONFIG.getLong("gui-refresh-cooldown-milliseconds", 100);
        if (cooldownMillis > 0L) {
            if (cooldown.hasCooldown(player)) return true;
            cooldown.addCooldown(player, cooldownMillis);
        }
        return false;
    }
}
