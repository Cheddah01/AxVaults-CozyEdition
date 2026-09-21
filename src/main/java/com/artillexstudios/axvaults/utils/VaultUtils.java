package com.artillexstudios.axvaults.utils;

import com.artillexstudios.axapi.serializers.Serializers;
import com.artillexstudios.axvaults.AxVaults;
import com.artillexstudios.axvaults.vaults.Vault;

import java.util.concurrent.CompletableFuture;

import static com.artillexstudios.axvaults.AxVaults.CONFIG;

public class VaultUtils {
    private static boolean asyncItemSerializer;
    private static boolean deleteEmptyVaults;

    public static void reload() {
        asyncItemSerializer = CONFIG.getBoolean("async-item-serializer", false);
        deleteEmptyVaults = CONFIG.getBoolean("delete-empty-vaults", true);
    }

    public static CompletableFuture<Void> save(Vault vault) {
        CompletableFuture<Object> serialized = new CompletableFuture<>();
        CompletableFuture<Void> saved = serialized.thenCompose(result -> {
            CompletableFuture<Void> write = new CompletableFuture<>();
            try {
                ThreadUtils.runAsync(() -> {
                    try {
                        AxVaults.getDatabase().saveVault(vault, result);
                        write.complete(null);
                    } catch (Throwable ex) {
                        write.completeExceptionally(ex);
                    }
                });
            } catch (RuntimeException ex) {
                write.completeExceptionally(ex);
            }
            return write;
        });
        // Register the continuation before serialization so the write is queued
        // in snapshot order, even when serialization completes immediately.
        try {
            serialize(vault, serialized);
        } catch (RuntimeException ex) {
            serialized.completeExceptionally(ex);
        }
        return saved.whenComplete((ignored, error) -> {
            if (error != null) java.util.logging.Logger.getLogger("AxVaults").log(
                    java.util.logging.Level.SEVERE,
                    "Failed to save vault " + vault.getId() + " for " + vault.getUUID(), error);
        });
    }

    public static void serialize(Vault vault, CompletableFuture<Object> future) {
        Runnable runnable = () -> {
            try {
                if (deleteEmptyVaults && vault.getStorage().isEmpty()) {
                    future.complete(true); // delete
                    return;
                }

                future.complete(Serializers.ITEM_ARRAY.serialize(vault.getStorage().getContents())); // success
            } catch (Exception ex) {
                future.completeExceptionally(ex);
            }
        };

        if (asyncItemSerializer && !AxVaults.isStopping()) ThreadUtils.runAsync(runnable);
        else ThreadUtils.runSync(runnable);
    }

    public static boolean isAsyncItemSerializer() {
        return asyncItemSerializer;
    }

    public static boolean isDeleteEmptyVaults() {
        return deleteEmptyVaults;
    }
}
