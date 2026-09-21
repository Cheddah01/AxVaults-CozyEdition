package com.artillexstudios.axvaults.commands.subcommands;

import com.artillexstudios.axvaults.converters.PlayerVaultsXConverter;
import org.bukkit.command.CommandSender;

import java.util.concurrent.CompletableFuture;

import static com.artillexstudios.axvaults.AxVaults.MESSAGEUTILS;

public enum Converter {
    INSTANCE;

    private CompletableFuture<Void> running;

    public synchronized void execute(CommandSender sender) {
        if (com.artillexstudios.axvaults.AxVaults.isStopping() || (running != null && !running.isDone())) return;
        running = CompletableFuture.runAsync(() -> {
            new PlayerVaultsXConverter().run();
        });
        MESSAGEUTILS.sendLang(sender, "converter.started");
    }

    public void stop() {
        CompletableFuture<Void> task = running;
        if (task == null) return;
        while (!task.isDone()) {
            com.artillexstudios.axvaults.utils.ThreadUtils.drainSync();
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
        }
        running = null;
    }
}
