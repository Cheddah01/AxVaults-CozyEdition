package com.artillexstudios.axvaults.lifecycle;

import com.artillexstudios.axvaults.database.Database;
import com.artillexstudios.axvaults.database.impl.H2;
import com.artillexstudios.axvaults.database.impl.SQLite;
import com.artillexstudios.axvaults.vaults.Vault;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseReloadTest {
    @TempDir Path temp;

    @Test
    void h2ReleasesFileAndPreservesVaultAcrossRepeatedReloads() throws Exception {
        Class.forName("org.h2.Driver");
        exercise(H2.class, "jdbc:h2:" + temp.resolve("vaults"));
    }

    @Test
    void sqliteReleasesFileAndPreservesVaultAcrossRepeatedReloads() throws Exception {
        Class.forName("org.sqlite.JDBC");
        exercise(SQLite.class, "jdbc:sqlite:" + temp.resolve("vaults.db"));
    }

    private void exercise(Class<? extends Database> type, String url) throws Exception {
        UUID owner = UUID.randomUUID();
        Vault vault = mock(Vault.class);
        when(vault.getUUID()).thenReturn(owner);
        when(vault.getId()).thenReturn(3);
        when(vault.getRealIcon()).thenReturn(Material.DIAMOND);
        for (int cycle = 1; cycle <= 3; cycle++) {
            Database database = type.getConstructor().newInstance();
            var connection = DriverManager.getConnection(url);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS axvaults_data (id INT, uuid VARCHAR(36), storage BLOB, icon VARCHAR(128))");
            }
            var field = type.getDeclaredField("conn");
            field.setAccessible(true);
            field.set(database, connection);
            byte[] contents = {(byte) cycle, 2, 3};
            database.saveVault(vault, contents);
            database.disable();
            database.disable(); // Partial startup / repeated disable must be harmless.
            assertTrue(connection.isClosed());
            try (var reopened = DriverManager.getConnection(url);
                 var statement = reopened.createStatement();
                 var result = statement.executeQuery("SELECT * FROM axvaults_data")) {
                assertTrue(result.next());
                assertEquals(owner.toString(), result.getString("uuid"));
                assertEquals(3, result.getInt("id"));
                assertEquals("DIAMOND", result.getString("icon"));
                assertArrayEquals(contents, result.getBytes("storage"));
                assertFalse(result.next());
            }
        }
    }
}
