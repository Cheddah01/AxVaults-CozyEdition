package com.artillexstudios.axvaults;

import com.artillexstudios.axapi.AxPlugin;
import com.artillexstudios.axapi.config.Config;
import com.artillexstudios.axapi.dependencies.DependencyManagerWrapper;
import com.artillexstudios.axvaults.lifecycle.TaskQueue;
import com.artillexstudios.axvaults.lifecycle.JdbcCleanup;
import com.artillexstudios.axvaults.utils.ThreadUtils;
import com.artillexstudios.axvaults.placed.PlacedVaults;
import dev.triumphteam.gui.guis.BaseGui;
import org.bukkit.event.HandlerList;
import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axapi.libs.boostedyaml.dvs.versioning.BasicVersioning;
import com.artillexstudios.axapi.libs.boostedyaml.settings.dumper.DumperSettings;
import com.artillexstudios.axapi.libs.boostedyaml.settings.general.GeneralSettings;
import com.artillexstudios.axapi.libs.boostedyaml.settings.loader.LoaderSettings;
import com.artillexstudios.axapi.libs.boostedyaml.settings.updater.UpdaterSettings;
import com.artillexstudios.axapi.metrics.AxMetrics;
import com.artillexstudios.axapi.utils.MessageUtils;
import com.artillexstudios.axapi.utils.StringUtils;
import com.artillexstudios.axapi.utils.featureflags.FeatureFlags;
import com.artillexstudios.axapi.utils.logging.LoggerNameFormat;
import com.artillexstudios.axvaults.commands.CommandManager;
import com.artillexstudios.axvaults.database.Database;
import com.artillexstudios.axvaults.database.impl.H2;
import com.artillexstudios.axvaults.database.impl.MySQL;
import com.artillexstudios.axvaults.database.impl.SQLite;
import com.artillexstudios.axvaults.database.messaging.SQLMessaging;
import com.artillexstudios.axvaults.hooks.HookManager;
import com.artillexstudios.axvaults.libraries.Libraries;
import com.artillexstudios.axvaults.listeners.BlacklistListener;
import com.artillexstudios.axvaults.listeners.BlockBreakListener;
import com.artillexstudios.axvaults.listeners.InventoryClickListener;
import com.artillexstudios.axvaults.listeners.InventoryCloseListener;
import com.artillexstudios.axvaults.listeners.PlayerInteractListener;
import com.artillexstudios.axvaults.listeners.PlayerListeners;
import com.artillexstudios.axvaults.schedulers.AutoSaveScheduler;
import com.artillexstudios.axvaults.utils.DebugUtils;
import com.artillexstudios.axvaults.utils.UpdateNotifier;
import com.artillexstudios.axvaults.utils.VaultUtils;
import com.artillexstudios.axvaults.vaults.Vault;
import com.artillexstudios.axvaults.vaults.VaultManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class AxVaults extends AxPlugin {
    private static volatile boolean stopping = false;
    public static Config CONFIG;
    public static Config MESSAGES;
    public static MessageUtils MESSAGEUTILS;
    private static AxPlugin instance;
    private static TaskQueue threadedQueue;
    private static Database database;
    private static AxMetrics metrics;
    private Metrics bstats;
    private UpdateNotifier updateNotifier;

    public static TaskQueue getThreadedQueue() {
        return threadedQueue;
    }

    public static AxPlugin getInstance() {
        return instance;
    }

    public static Database getDatabase() {
        return database;
    }

    public static boolean isStopping() {
        return stopping;
    }

    @Override
    public void dependencies(DependencyManagerWrapper manager) {
        instance = this;
        Libraries.load(instance, manager);
    }

    public void enable() {
        instance = this;
        stopping = false;
        bstats = new Metrics(this, 20541);

        CONFIG = new Config(new File(getDataFolder(), "config.yml"), getResource("config.yml"), GeneralSettings.builder().setUseDefaults(false).build(), LoaderSettings.builder().setAutoUpdate(true).build(), DumperSettings.DEFAULT, UpdaterSettings.builder().setKeepAll(true).setVersioning(new BasicVersioning("version")).build());
        MESSAGES = new Config(new File(getDataFolder(), "messages.yml"), getResource("messages.yml"), GeneralSettings.builder().setUseDefaults(false).build(), LoaderSettings.builder().setAutoUpdate(true).build(), DumperSettings.DEFAULT, UpdaterSettings.builder().setKeepAll(true).setVersioning(new BasicVersioning("version")).build());

        MESSAGEUTILS = new MessageUtils(MESSAGES.getBackingDocument(), "prefix", CONFIG.getBackingDocument());

        threadedQueue = new TaskQueue("AxVaults-Datastore-thread");

        VaultUtils.reload();
        HookManager.setupHooks();
        DebugUtils.init(CONFIG);

        database = switch (CONFIG.getString("database.type").toLowerCase()) {
            case "sqlite" -> new SQLite();
            case "mysql" -> new MySQL();
            default -> new H2();
        };

        database.setup();

        threadedQueue.submit(() -> database.load());

        getServer().getPluginManager().registerEvents(new PlayerListeners(), this);
        getServer().getPluginManager().registerEvents(new BlacklistListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerInteractListener(), this);
        getServer().getPluginManager().registerEvents(new BlockBreakListener(), this);
        getServer().getPluginManager().registerEvents(new InventoryCloseListener(), this);
        getServer().getPluginManager().registerEvents(new InventoryClickListener(), this);

        CommandManager.load();

        AutoSaveScheduler.start();
        SQLMessaging.start();

        metrics = new AxMetrics(this, 3);
        metrics.start();

        Bukkit.getConsoleSender().sendMessage(StringUtils.formatToString("&#55ff00[AxVaults] Loaded plugin!"));

        UpdateNotifier.init(CONFIG, MESSAGES);
        if (CONFIG.getBoolean("update-notifier.enabled", true)) updateNotifier = new UpdateNotifier();
    }

    public void disable() {
        stopping = true;
        cleanup("commands", CommandManager::unload);
        cleanup("autosave scheduler", AutoSaveScheduler::stop);
        cleanup("SQL messaging", SQLMessaging::stop);
        cleanup("converter", com.artillexstudios.axvaults.commands.subcommands.Converter.INSTANCE::stop);
        cleanup("update notifier", () -> { if (updateNotifier != null) updateNotifier.stop(); });
        cleanup("AxMetrics", () -> { if (metrics != null) metrics.cancel(); });
        cleanup("bStats", () -> { if (bstats != null) bstats.shutdown(); });

        // Close both editable vaults and decorative menus before removing GUI listeners.
        for (var player : Bukkit.getOnlinePlayers()) {
            cleanup("open menu for " + player.getName(), () -> {
                var holder = player.getOpenInventory().getTopInventory().getHolder();
                if (holder instanceof BaseGui gui) gui.close(player, false);
                else if (holder instanceof Vault) player.closeInventory();
            });
        }
        cleanup("scheduled tasks", () -> Scheduler.get().cancelAll());

        if (threadedQueue != null) {
            cleanup("vault save pipeline", () -> {
                try {
                    // Finish old snapshots/writes before taking the final snapshot.
                    threadedQueue.drain(ThreadUtils::drainSync);
                    List<CompletableFuture<Void>> saves = new ArrayList<>();
                    for (Vault vault : VaultManager.getVaults()) {
                        saves.add(VaultUtils.save(vault).exceptionally(ex -> {
                            getLogger().log(java.util.logging.Level.SEVERE,
                                    "Could not save vault " + vault.getId() + " for " + vault.getUUID(), ex);
                            return null;
                        }));
                    }
                    threadedQueue.drain(ThreadUtils::drainSync);
                    CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new)).join();
                } finally {
                    threadedQueue.drain(ThreadUtils::drainSync);
                    threadedQueue.stop();
                }
            });
        }
        cleanup("database", () -> { if (database != null) database.disable(); });
        cleanup("placeholder hooks", HookManager::stop);
        cleanup("JDBC drivers", () -> JdbcCleanup.release(getClass().getClassLoader()));
        cleanup("listeners", () -> HandlerList.unregisterAll(this));
        VaultManager.getLoadingPlayers().values().forEach(future -> future.cancel(false));
        VaultManager.getLoadingPlayers().clear();
        VaultManager.getPlayers().clear();
        PlacedVaults.getVaults().clear();
        com.artillexstudios.axvaults.guis.VaultSelector.clearCooldowns();
        com.artillexstudios.axvaults.guis.ItemPicker.clearCooldowns();
        database = null;
        threadedQueue = null;
        metrics = null;
        bstats = null;
        updateNotifier = null;
    }

    private void cleanup(String resource, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Failed to release " + resource, ex);
        }
    }

    public void updateFlags() {
        FeatureFlags.LOGGER_NAME_FORMAT.set(LoggerNameFormat.NAMEABLE);
    }
}
