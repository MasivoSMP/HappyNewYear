package com.xSavior_of_God.HappyNewYear.tasks;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.xSavior_of_God.HappyNewYear.HappyNewYear;
import com.xSavior_of_God.HappyNewYear.utils.Utils;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class AlwaysNightTask {
    private static final DateTimeFormatter REAL_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final Map<World, ScheduledTask> realTimeTasks = new HashMap<>();
    private final Map<World, ScheduledTask> alwaysNightTasks = new HashMap<>();

    List<World> worlds;

    public AlwaysNightTask() {
        worlds = new ArrayList<>(Bukkit.getServer().getWorlds());
        if (HappyNewYear.wm.getBlacklist()) {
            worlds.removeIf(w -> HappyNewYear.wm.getWorldsName().contains(w.getName()));
        } else {
            worlds.removeIf(w -> !HappyNewYear.wm.getWorldsName().contains(w.getName()));
        }
        // Remove all worlds that are not NORMAL (like NETHER, THE_END, etc.)
        worlds.removeIf(w -> !w.getEnvironment().equals(World.Environment.NORMAL));

        if (HappyNewYear.wm.getInRealLifeEnabled()) {
            ZoneId zone = ZoneId.of(HappyNewYear.wm.getTimezone());
            for (World w : worlds) {
                int chunkX = w.getSpawnLocation().getBlockX() >> 4;
                int chunkZ = w.getSpawnLocation().getBlockZ() >> 4;
                ScheduledTask task = HappyNewYear.scheduler.runRegionRepeating(w, chunkX, chunkZ, 20L, 1L, scheduledTask -> {
                    long time = Utils.parse24(LocalTime.now(zone).format(REAL_TIME_FORMAT)) + 1;
                    if (w.getTime() != time) {
                        w.setTime(time);
                    }
                });
                realTimeTasks.put(w, task);
            }
        } else if (HappyNewYear.wm.getAlwaysNightEnabled()) {
            for (World w : worlds) {
                int chunkX = w.getSpawnLocation().getBlockX() >> 4;
                int chunkZ = w.getSpawnLocation().getBlockZ() >> 4;
                ScheduledTask task = HappyNewYear.scheduler.runRegionRepeating(w, chunkX, chunkZ, 20L, 1L, scheduledTask -> {
                    if (w.getTime() != 18000L) {
                        w.setTime(18000L);
                    }
                });
                alwaysNightTasks.put(w, task);
            }
        }

    }

    public void StopTask() {
        for (ScheduledTask task : realTimeTasks.values()) {
            task.cancel();
        }
        for (ScheduledTask task : alwaysNightTasks.values()) {
            task.cancel();
        }
        if (!alwaysNightTasks.isEmpty()) {
            for (World w : worlds) {
                int chunkX = w.getSpawnLocation().getBlockX() >> 4;
                int chunkZ = w.getSpawnLocation().getBlockZ() >> 4;
                HappyNewYear.scheduler.runRegionLater(w, chunkX, chunkZ, 0L,
                        task -> w.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, true));
            }
        }
    }
}
