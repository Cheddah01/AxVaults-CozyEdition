package com.artillexstudios.axvaults.utils;

import com.artillexstudios.axapi.config.Config;
import com.artillexstudios.axvaults.AxVaults;
import com.artillexstudios.axvaults.database.Database;
import com.artillexstudios.axvaults.lifecycle.TaskQueue;
import com.artillexstudios.axvaults.vaults.Vault;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.*;
import java.util.concurrent.CompletionException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaultSaveTest {
    private TaskQueue queue;
    private Database database;
    private Vault vault;
    private Inventory inventory;

    @BeforeEach
    void setup() throws Exception {
        AxVaults.CONFIG = mock(Config.class);
        when(AxVaults.CONFIG.getBoolean("async-item-serializer", false)).thenReturn(true);
        when(AxVaults.CONFIG.getBoolean("delete-empty-vaults", true)).thenReturn(true);
        VaultUtils.reload();
        queue = new TaskQueue("AxVaults-save-test");
        database = mock(Database.class);
        setField("threadedQueue", queue);
        setField("database", database);
        setField("stopping", false);
        vault = mock(Vault.class);
        inventory = mock(Inventory.class);
        when(vault.getStorage()).thenReturn(inventory);
        when(inventory.isEmpty()).thenReturn(true);
    }

    @AfterEach
    void cleanup() throws Exception {
        queue.stop();
        setField("threadedQueue", null);
        setField("database", null);
    }

    private void setField(String name, Object value) throws Exception {
        var field = AxVaults.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    @Test
    void databaseFailureCompletesSaveExceptionallyInsteadOfHanging() {
        doThrow(new IllegalStateException("disk full")).when(database).saveVault(vault, true);
        var save = VaultUtils.save(vault);
        queue.drain(() -> {});
        assertThrows(CompletionException.class, save::join);
    }

    @Test
    void serializationFailureNeverWritesOrDeletesExistingData() {
        when(inventory.isEmpty()).thenThrow(new IllegalStateException("snapshot failed"));
        var save = VaultUtils.save(vault);
        queue.drain(() -> {});
        assertThrows(CompletionException.class, save::join);
        verifyNoInteractions(database);
    }

    @Test
    void successfulSaveCompletesAfterDatabaseWrite() {
        var save = VaultUtils.save(vault);
        queue.drain(() -> {});
        assertDoesNotThrow(save::join);
        verify(database).saveVault(vault, true);
    }
}
