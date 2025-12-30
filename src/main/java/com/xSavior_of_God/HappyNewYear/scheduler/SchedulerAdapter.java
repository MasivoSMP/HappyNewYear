package com.xSavior_of_God.HappyNewYear.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class SchedulerAdapter {
    private static final Runnable NOOP = () -> {
    };

    private final Plugin plugin;
    private final boolean folia;
    private final ConcurrentMap<UUID, Set<ScheduledTask>> perPlayerTasks = new ConcurrentHashMap<>();
    private final Set<ScheduledTask> globalTasks = ConcurrentHashMap.newKeySet();
    private final Set<ScheduledTask> regionTasks = ConcurrentHashMap.newKeySet();

    public SchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
        this.folia = detectFolia();
    }

    public boolean isFolia() {
        return folia;
    }

    public ScheduledTask runPerPlayerRepeating(Player player, long initialDelayTicks, long periodTicks, Consumer<ScheduledTask> task) {
        UUID playerId = player.getUniqueId();
        Runnable retired = () -> cancelPlayer(playerId);
        ScheduledTask scheduled = player.getScheduler().runAtFixedRate(plugin, task, retired, initialDelayTicks, periodTicks);
        trackPlayerTask(playerId, scheduled);
        return scheduled;
    }

    public ScheduledTask runPerPlayerLater(Player player, long delayTicks, Consumer<ScheduledTask> task) {
        UUID playerId = player.getUniqueId();
        AtomicReference<ScheduledTask> ref = new AtomicReference<>();
        Runnable retired = () -> {
            ScheduledTask scheduled = ref.get();
            if (scheduled != null) {
                untrackPlayerTask(playerId, scheduled);
            }
        };
        Consumer<ScheduledTask> wrapped = scheduledTask -> {
            try {
                task.accept(scheduledTask);
            } finally {
                untrackPlayerTask(playerId, scheduledTask);
            }
        };
        ScheduledTask scheduled = player.getScheduler().runDelayed(plugin, wrapped, retired, delayTicks);
        ref.set(scheduled);
        trackPlayerTask(playerId, scheduled);
        return scheduled;
    }

    public ScheduledTask runEntityLater(Entity entity, long delayTicks, Consumer<ScheduledTask> task) {
        AtomicReference<ScheduledTask> ref = new AtomicReference<>();
        Runnable retired = () -> {
            ScheduledTask scheduled = ref.get();
            if (scheduled != null) {
                regionTasks.remove(scheduled);
            }
        };
        Consumer<ScheduledTask> wrapped = scheduledTask -> {
            try {
                task.accept(scheduledTask);
            } finally {
                regionTasks.remove(scheduledTask);
            }
        };
        ScheduledTask scheduled = entity.getScheduler().runDelayed(plugin, wrapped, retired, delayTicks);
        ref.set(scheduled);
        regionTasks.add(scheduled);
        return scheduled;
    }

    public ScheduledTask runRegionRepeating(World world, int chunkX, int chunkZ, long initialDelayTicks, long periodTicks, Consumer<ScheduledTask> task) {
        ScheduledTask scheduled = Bukkit.getRegionScheduler().runAtFixedRate(plugin, world, chunkX, chunkZ, task, initialDelayTicks, periodTicks);
        regionTasks.add(scheduled);
        return scheduled;
    }

    public ScheduledTask runRegionLater(World world, int chunkX, int chunkZ, long delayTicks, Consumer<ScheduledTask> task) {
        Consumer<ScheduledTask> wrapped = scheduledTask -> {
            try {
                task.accept(scheduledTask);
            } finally {
                regionTasks.remove(scheduledTask);
            }
        };
        ScheduledTask scheduled;
        if (delayTicks <= 0L) {
            scheduled = Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, wrapped);
        } else {
            scheduled = Bukkit.getRegionScheduler().runDelayed(plugin, world, chunkX, chunkZ, wrapped, delayTicks);
        }
        regionTasks.add(scheduled);
        return scheduled;
    }

    public ScheduledTask runGlobalRepeating(long initialDelayTicks, long periodTicks, Consumer<ScheduledTask> task) {
        ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task, initialDelayTicks, periodTicks);
        globalTasks.add(scheduled);
        return scheduled;
    }

    public ScheduledTask runGlobalLater(long delayTicks, Consumer<ScheduledTask> task) {
        Consumer<ScheduledTask> wrapped = scheduledTask -> {
            try {
                task.accept(scheduledTask);
            } finally {
                globalTasks.remove(scheduledTask);
            }
        };
        ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, wrapped, delayTicks);
        globalTasks.add(scheduled);
        return scheduled;
    }

    public void cancelPlayer(UUID playerId) {
        Set<ScheduledTask> tasks = perPlayerTasks.remove(playerId);
        if (tasks == null) {
            return;
        }
        for (ScheduledTask task : tasks) {
            task.cancel();
        }
    }

    public void cancelAll() {
        for (Set<ScheduledTask> tasks : perPlayerTasks.values()) {
            for (ScheduledTask task : tasks) {
                task.cancel();
            }
        }
        perPlayerTasks.clear();
        for (ScheduledTask task : globalTasks) {
            task.cancel();
        }
        globalTasks.clear();
        for (ScheduledTask task : regionTasks) {
            task.cancel();
        }
        regionTasks.clear();
    }

    private void trackPlayerTask(UUID playerId, ScheduledTask task) {
        perPlayerTasks.computeIfAbsent(playerId, key -> ConcurrentHashMap.newKeySet()).add(task);
    }

    private void untrackPlayerTask(UUID playerId, ScheduledTask task) {
        Set<ScheduledTask> tasks = perPlayerTasks.get(playerId);
        if (tasks == null) {
            return;
        }
        tasks.remove(task);
        if (tasks.isEmpty()) {
            perPlayerTasks.remove(playerId, tasks);
        }
    }

    private boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
