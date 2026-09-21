package com.artillexstudios.axvaults.utils;

import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axvaults.AxVaults;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadUtils {
    private static final Logger log = LoggerFactory.getLogger(ThreadUtils.class);

    public static void checkMain(String message) {
        if (!Scheduler.get().isGlobalTickThread()) {
            log.error("Thread {} failed main thread check for {}!", Thread.currentThread().getName(), message, new Throwable());
            throw new RuntimeException();
        }
    }

    public static void checkNotMain(String message) {
        if (Scheduler.get().isGlobalTickThread()) {
            log.error("Thread {} failed main thread check for {}!", Thread.currentThread().getName(), message, new Throwable());
            throw new RuntimeException();
        }
    }

    private static final java.util.Queue<Runnable> pendingSync = new java.util.concurrent.ConcurrentLinkedQueue<>();

    public static void runAsync(Runnable runnable) {
        AxVaults.getThreadedQueue().submit(runnable);
    }

    public static void runSync(Runnable runnable) {
        dispatchSync(null, runnable);
    }

    public static void runSync(Player player, Runnable runnable) {
        dispatchSync(player, runnable);
    }

    private static void dispatchSync(Player player, Runnable runnable) {
        if (org.bukkit.Bukkit.isPrimaryThread()
                || (!AxVaults.isStopping() && player != null
                && Scheduler.get().isOwnedByCurrentRegion(player.getLocation()))) {
            runnable.run();
            return;
        }
        java.util.concurrent.atomic.AtomicBoolean claimed = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable once = () -> {
            if (claimed.compareAndSet(false, true)) runnable.run();
        };
        pendingSync.add(once);
        // onDisable drains this queue itself; scheduling against a disabled
        // plugin is illegal and executing Bukkit work on this worker is unsafe.
        if (AxVaults.isStopping()) return;
        Runnable scheduled = () -> {
            try {
                once.run();
            } finally {
                pendingSync.remove(once);
            }
        };
        if (player == null) Scheduler.get().run(scheduled);
        else Scheduler.get().run(player, task -> scheduled.run(), scheduled);
    }

    public static void drainSync() {
        Runnable task;
        while ((task = pendingSync.poll()) != null) {
            try {
                task.run();
            } catch (Exception ex) {
                log.error("Failed to finish a pending server-thread task during shutdown", ex);
            }
        }
    }
}
