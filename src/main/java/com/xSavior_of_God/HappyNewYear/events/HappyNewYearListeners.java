package com.xSavior_of_God.HappyNewYear.events;

import com.xSavior_of_God.HappyNewYear.HappyNewYear;
import com.xSavior_of_God.HappyNewYear.utils.Utils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class HappyNewYearListeners implements Listener {

    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent event) {
        // Notify the player that the plugin is disabled
        if (!HappyNewYear.enabled && (event.getPlayer().isOp() || event.getPlayer().hasPermission("happynewyear.reload"))) {
            Utils.sendMessage(event.getPlayer(), "&e[HappyNewYear] &4&lHEY! &cDon't forget to enable the plugin in the config.yml if you want to use it!");
        }
        if (HappyNewYear.enabled && HappyNewYear.instance.getFireworkTask() != null) {
            HappyNewYear.instance.getFireworkTask().startPlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        if (HappyNewYear.instance.getFireworkTask() != null) {
            HappyNewYear.instance.getFireworkTask().cancelPlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerKickEvent(PlayerKickEvent event) {
        if (HappyNewYear.instance.getFireworkTask() != null) {
            HappyNewYear.instance.getFireworkTask().cancelPlayer(event.getPlayer());
        }
    }
}
