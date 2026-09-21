package com.artillexstudios.axvaults.guis;

import com.artillexstudios.axapi.config.Config;
import com.artillexstudios.axvaults.AxVaults;
import com.artillexstudios.axvaults.vaults.Vault;
import com.artillexstudios.axvaults.vaults.VaultPlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class VaultSelectorTest {
    private Player player;
    private VaultPlayer vaultPlayer;
    private VaultSelector selector;

    @BeforeEach
    void setUp() {
        AxVaults.CONFIG = mock(Config.class);
        when(AxVaults.CONFIG.getInt("max-vault-amount", -1)).thenReturn(-1);
        player = mock(Player.class);
        vaultPlayer = new VaultPlayer(UUID.randomUUID());
        selector = new VaultSelector(player, vaultPlayer);
    }

    private void grant(int... numbers) {
        Set<PermissionAttachmentInfo> permissions = IntStream.of(numbers)
                .mapToObj(n -> new PermissionAttachmentInfo(player, "axvaults.vault." + n, null, true))
                .collect(Collectors.toSet());
        when(player.getEffectivePermissions()).thenReturn(permissions);
        for (int n : numbers) when(player.hasPermission("axvaults.vault." + n)).thenReturn(true);
    }

    @Test
    void threeSeparateVaultsPutPromptFourth() {
        grant(1, 2, 3);
        assertEquals(4, selector.getPromotionNumber());
    }

    @Test
    void highestPermissionModePutsPromptAfterAllowance() {
        when(AxVaults.CONFIG.getInt("permission-mode", 0)).thenReturn(1);
        grant(3);
        assertEquals(4, selector.getPromotionNumber());
    }

    @Test
    void noVaultsPutPromptFirst() {
        assertEquals(1, selector.getPromotionNumber());
    }

    @Test
    void fullPageMovesPromptToNextPage() {
        grant(IntStream.rangeClosed(1, 45).toArray());
        assertEquals(46, selector.getPromotionNumber());
    }

    @Test
    void separateLaterVaultIsPreservedBeforePrompt() {
        grant(1, 3);
        assertEquals(4, selector.getPromotionNumber());
    }

    @Test
    void savedButRevokedVaultDoesNotMovePrompt() {
        grant(1, 2, 3);
        vaultPlayer.getVaultMap().put(10, mock(Vault.class));
        assertEquals(4, selector.getPromotionNumber());
    }

    @Test
    void allVaultsAtServerLimitHidePrompt() {
        grant(1, 2, 3);
        when(AxVaults.CONFIG.getInt("max-vault-amount", -1)).thenReturn(3);
        assertEquals(-1, selector.getPromotionNumber());
    }

    @Test
    void unlimitedAccessHidesPrompt() {
        when(player.hasPermission(anyString())).thenReturn(true);
        assertEquals(-1, selector.getPromotionNumber());
    }

    @Test
    void operatorInHighestPermissionModeHidesPrompt() {
        when(AxVaults.CONFIG.getInt("permission-mode", 0)).thenReturn(1);
        when(player.isOp()).thenReturn(true);
        assertEquals(-1, selector.getPromotionNumber());
    }
}
