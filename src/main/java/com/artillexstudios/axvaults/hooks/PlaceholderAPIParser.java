package com.artillexstudios.axvaults.hooks;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;

import java.util.List;

public class PlaceholderAPIParser implements Placeholders {

    public static void unregisterOwnedExpansions() {
        var manager = me.clip.placeholderapi.PlaceholderAPIPlugin.getInstance().getLocalExpansionManager();
        var owner = PlaceholderAPIParser.class.getClassLoader();
        for (var expansion : new java.util.ArrayList<>(manager.getExpansions())) {
            if (expansion.getClass().getClassLoader() == owner) expansion.unregister();
        }
    }

    @Override
    public String setPlaceholders(OfflinePlayer player, String txt) {
        return PlaceholderAPI.setPlaceholders(player, txt);
    }

    @Override
    public List<String> setPlaceholders(OfflinePlayer player, List<String> txt) {
        return PlaceholderAPI.setPlaceholders(player, txt);
    }
}
