package com.artillexstudios.axvaults.lifecycle;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/** Serial datastore worker whose accepted work can be drained before closing JDBC. */
public final class TaskQueue {
    private final ExecutorService executor;
    private final AtomicInteger pending = new AtomicInteger();

    public TaskQueue(String name) {
        executor = Executors.newSingleThreadExecutor(r -> new Thread(r, name));
    }

    public void submit(Runnable task) {
        pending.incrementAndGet();
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    pending.decrementAndGet();
                }
            });
        } catch (RuntimeException ex) {
            pending.decrementAndGet();
            throw ex;
        }
    }

    // Called on the server thread. Pump callbacks so workers never wait for a
    // server tick that cannot happen while onDisable is running.
    public void drain(Runnable pumpMainThread) {
        while (true) {
            pumpMainThread.run();
            if (pending.get() == 0) {
                pumpMainThread.run();
                if (pending.get() == 0) return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
    }

    public void stop() {
        executor.shutdown();
        awaitTermination(executor);
    }

    public static void awaitTermination(ExecutorService executor) {
        boolean interrupted = false;
        try {
            while (!executor.isTerminated()) {
                try {
                    executor.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
