package com.artillexstudios.axvaults.lifecycle;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import static org.junit.jupiter.api.Assertions.*;

class TaskQueueTest {
    @Test
    void shutdownDrainsCallbacksAndWritesInOrderAndTerminatesWorker() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            TaskQueue queue = new TaskQueue("AxVaults-test-worker");
            Queue<Runnable> callbacks = new ConcurrentLinkedQueue<>();
            List<String> writes = new ArrayList<>();
            try {
                queue.submit(() -> callbacks.add(() -> queue.submit(() -> writes.add("old snapshot"))));
                Runnable pump = () -> { Runnable task; while ((task = callbacks.poll()) != null) task.run(); };
                queue.drain(pump);
                queue.submit(() -> writes.add("final snapshot"));
                queue.drain(pump);
                assertEquals(List.of("old snapshot", "final snapshot"), writes);
            } finally {
                queue.stop();
            }
            assertThrows(RejectedExecutionException.class, () -> queue.submit(() -> {}));
            assertTrue(Thread.getAllStackTraces().keySet().stream()
                    .noneMatch(thread -> thread.isAlive() && thread.getName().equals("AxVaults-test-worker")));
        });
    }

    @Test
    void workerWaitingForServerCallbackDoesNotDeadlockShutdown() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            TaskQueue queue = new TaskQueue("AxVaults-test-callback");
            Queue<Runnable> callbacks = new ConcurrentLinkedQueue<>();
            try {
                queue.submit(() -> {
                    CompletableFuture<Void> callback = new CompletableFuture<>();
                    callbacks.add(() -> callback.complete(null));
                    callback.join();
                });
                queue.drain(() -> { Runnable task; while ((task = callbacks.poll()) != null) task.run(); });
            } finally {
                queue.stop();
            }
        });
    }

    @Test
    void cleanupOnlyOwnsPluginClassLoaderAndChildren() {
        ClassLoader parent = getClass().getClassLoader();
        ClassLoader plugin = new ClassLoader(parent) {};
        ClassLoader child = new ClassLoader(plugin) {};
        ClassLoader otherPlugin = new ClassLoader(parent) {};
        assertTrue(JdbcCleanup.ownedBy(plugin, plugin));
        assertTrue(JdbcCleanup.ownedBy(child, plugin));
        assertFalse(JdbcCleanup.ownedBy(parent, plugin));
        assertFalse(JdbcCleanup.ownedBy(otherPlugin, plugin));
    }
}
