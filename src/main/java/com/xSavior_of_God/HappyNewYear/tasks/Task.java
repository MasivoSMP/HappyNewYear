package com.xSavior_of_God.HappyNewYear.tasks;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import com.xSavior_of_God.HappyNewYear.HappyNewYear;
import com.xSavior_of_God.HappyNewYear.api.events.OnFireworkEvent;
import com.xSavior_of_God.HappyNewYear.scheduler.SchedulerAdapter;
import com.xSavior_of_God.HappyNewYear.utils.Utils;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Builder;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

public class Task {
    private static final DateTimeFormatter REAL_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final long INITIAL_DELAY_TICKS = 5 * 20L;
    private static final long TICK_MILLIS = 50L;

    private final SchedulerAdapter scheduler;
    private final boolean hourlyMode;
    private final boolean realisticMode;
    private final ZoneId hourlyZone;
    private final ZoneId worldZone;
    private final int hourlyDurationTicks;
    private final Map<UUID, ScheduledTask> perPlayerRepeats = new ConcurrentHashMap<>();
    private final Map<ChunkKey, AtomicInteger> chunkCounters = new ConcurrentHashMap<>();
    private final ScheduledTask globalTickTask;
    private volatile int startHour = -1;
    private volatile int durationTicks = 0;
    private volatile long nextCycleMillis;

    public Task(String spawnAnimationType, int hourlyDuration, String hourlyTimezone) {
        if (spawnAnimationType == null || spawnAnimationType.trim().isEmpty()) {
            throw new IllegalArgumentException("spawnAnimationType cannot be null or empty");
        }
        if (hourlyDuration <= 0) {
            throw new IllegalArgumentException("hourlyDuration must be positive");
        }
        try {
            ZoneId.of(hourlyTimezone); // Validate timezone
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid timezone: " + hourlyTimezone);
        }
        this.scheduler = HappyNewYear.scheduler;
        this.hourlyMode = spawnAnimationType.contains("HOURLY");
        this.realisticMode = spawnAnimationType.contains("REALISTIC");
        this.hourlyZone = hourlyMode ? ZoneId.of(hourlyTimezone) : null;
        this.worldZone = ZoneId.of(HappyNewYear.wm.getTimezone());
        this.hourlyDurationTicks = hourlyDuration;
        this.nextCycleMillis = System.currentTimeMillis() + (INITIAL_DELAY_TICKS * TICK_MILLIS);
        this.globalTickTask = scheduler.runGlobalRepeating(INITIAL_DELAY_TICKS, HappyNewYear.timer, task -> handleGlobalTick());
    }

    public void startForOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            startPlayer(player);
        }
    }

    public void startPlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (perPlayerRepeats.containsKey(playerId)) {
            return;
        }
        long initialDelay = computeInitialDelayTicks();
        ScheduledTask scheduled = scheduler.runPerPlayerRepeating(player, initialDelay, HappyNewYear.timer, task -> runBurstForPlayer(player));
        perPlayerRepeats.put(playerId, scheduled);
    }

    public void cancelPlayer(Player player) {
        if (player == null) {
            return;
        }
        cancelPlayer(player.getUniqueId());
    }

    public void cancelPlayer(UUID playerId) {
        ScheduledTask scheduled = perPlayerRepeats.remove(playerId);
        if (scheduled != null) {
            scheduled.cancel();
        }
        scheduler.cancelPlayer(playerId);
    }

    private void runBurstForPlayer(Player player) {
        if (HappyNewYear.forceStop) {
            return;
        }
        if (!player.isOnline()) {
            cancelPlayer(player);
            return;
        }
        if (!isWorldAllowed(player.getWorld())) {
            return;
        }
        if (HappyNewYear.wm.getOnNightEnabled() && !isNightAllowed(player)) {
            return;
        }
        if (hourlyMode && durationTicks <= 0) {
            return;
        }
        if (!passesChunkLimit(player)) {
            return;
        }

        OnFireworkEvent event = new OnFireworkEvent(player);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        if (realisticMode) {
            for (int c = 0; c < HappyNewYear.amountPerPlayer; c++) {
                long delay = ThreadLocalRandom.current().nextInt(1, HappyNewYear.timer + 1);
                scheduler.runPerPlayerLater(player, delay, task -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    Location location = randomLocation(player.getLocation());
                    scheduleSpawn(location, 0L);
                });
            }
        } else {
            for (int c = 0; c < HappyNewYear.amountPerPlayer; c++) {
                Location location = randomLocation(player.getLocation());
                scheduleSpawn(location, 0L);
            }
        }
    }

    private void spawnFireworks(final Location LOC) {
        final String fireworkHook = HappyNewYear.fireworkHooks.get(ThreadLocalRandom.current().nextInt(0, HappyNewYear.fireworkHooks.size()));
        switch(fireworkHook) {
            case "IMAGEFIREWORKSPRO":

                if (HappyNewYear.imageFireworksRebornHook != null) {
                    try {
                        HappyNewYear.imageFireworksRebornHook.spawnFirework(LOC);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                break;
            default:
                spawnVanillaFireworks(LOC, HappyNewYear.fireworkEffectTypes.get(ThreadLocalRandom.current().nextInt(0, HappyNewYear.fireworkEffectTypes.size())));
                break;
        }
    }

    private void spawnVanillaFireworks(final Location LOC, final String TYPE) {
        if (LOC.getWorld() == null) {
            Utils.log(Level.WARNING, "Something went wrong while spawning fireworks. World is null!");
            return;
        }

        Firework firework = spawnFireworkEntity(LOC);
        final FireworkMeta meta = firework.getFireworkMeta();
        final FireworkEffect.Builder builder = FireworkEffect.builder();
        builder.with(FireworkEffect.Type.valueOf((TYPE.equalsIgnoreCase("RANDOM")
                ? FireworkEffect.Type.values()[ThreadLocalRandom.current().nextInt(0, FireworkEffect.Type.values().length)]
                .name()
                : TYPE)));
        setColor(builder);
        meta.addEffect(builder.build());
        firework.setFireworkMeta(meta);
        scheduler.runEntityLater(firework, 1L, task -> firework.detonate());
    }

    private Firework spawnFireworkEntity(Location loc) {
        try {
            return (Firework) loc.getWorld().spawnEntity(loc, EntityType.valueOf("FIREWORK_ROCKET"));
        } catch (Exception ignored) {
            return (Firework) loc.getWorld().spawnEntity(loc, EntityType.valueOf("FIREWORK"));
        }
    }

    private void setColor(final Builder BUILDER) {
        int random = ThreadLocalRandom.current().nextInt(1, 10 + 1);
        for (int i = 0; i < random; ++i) {
            final Color color = Color.fromBGR(ThreadLocalRandom.current().nextInt(1, 255 + 1),
                    ThreadLocalRandom.current().nextInt(1, 255 + 1), ThreadLocalRandom.current().nextInt(1, 255 + 1));
            BUILDER.withColor(color);
        }
    }

    private Location randomLocation(final Location LOC) {
        int Horizontal = ThreadLocalRandom.current().nextInt(HappyNewYear.randomSpawnPosition_Horizontal * -1,
                HappyNewYear.randomSpawnPosition_Horizontal + 1);
        int Horizontal2 = ThreadLocalRandom.current().nextInt(HappyNewYear.randomSpawnPosition_Horizontal * -1,
                HappyNewYear.randomSpawnPosition_Horizontal + 1);
        int Vertical = ThreadLocalRandom.current().nextInt(HappyNewYear.randomSpawnPosition_Vertical * -1,
                HappyNewYear.randomSpawnPosition_Vertical + 1);
        LOC.setYaw(ThreadLocalRandom.current().nextInt(0, 360));
        LOC.setPitch(ThreadLocalRandom.current().nextInt(0, 360));
        return LOC.add(Horizontal, Vertical + HappyNewYear.explosionHeight, Horizontal2);
    }

    public void StopTask() {
        if (globalTickTask != null) {
            globalTickTask.cancel();
        }
        for (UUID playerId : perPlayerRepeats.keySet()) {
            cancelPlayer(playerId);
        }
        perPlayerRepeats.clear();
        chunkCounters.clear();
    }

    private void handleGlobalTick() {
        chunkCounters.clear();
        nextCycleMillis = System.currentTimeMillis() + (HappyNewYear.timer * TICK_MILLIS);
        if (!hourlyMode) {
            return;
        }
        int currentHour = LocalTime.now(hourlyZone).getHour();
        if (durationTicks == 0 && startHour != currentHour) {
            startHour = currentHour;
            durationTicks = hourlyDurationTicks;
        }
        if (durationTicks > 0) {
            durationTicks = Math.max(0, durationTicks - HappyNewYear.timer);
        }
    }

    private long computeInitialDelayTicks() {
        long delayMillis = nextCycleMillis - System.currentTimeMillis();
        long delayTicks = delayMillis / TICK_MILLIS;
        return Math.max(1L, delayTicks);
    }

    private boolean isWorldAllowed(World world) {
        boolean inList = HappyNewYear.wm.getWorldsName().contains(world.getName());
        return HappyNewYear.wm.getBlacklist() ? !inList : inList;
    }

    private boolean isNightAllowed(Player player) {
        boolean between = false;
        if (HappyNewYear.wm.getMonth() == -1 || LocalDate.now(worldZone).getMonthValue() == HappyNewYear.wm.getMonth()) {
            if (HappyNewYear.wm.getInRealLifeEnabled()) {
                between = Utils.stringTimeIsBetween(HappyNewYear.wm.getOnNightStarts(), HappyNewYear.wm.getOnNightEnds(),
                        LocalTime.now(worldZone).format(REAL_TIME_FORMAT));
            } else {
                between = Utils.stringTimeIsBetween(HappyNewYear.wm.getOnNightStarts(), HappyNewYear.wm.getOnNightEnds(),
                        Utils.format(player.getWorld().getTime()));
            }
        }
        return between;
    }

    private boolean passesChunkLimit(Player player) {
        if (HappyNewYear.limit <= 0) {
            return true;
        }
        int chunkX = player.getLocation().getChunk().getX();
        int chunkZ = player.getLocation().getChunk().getZ();
        UUID worldId = player.getWorld().getUID();
        ChunkKey key = new ChunkKey(worldId, chunkX, chunkZ);
        AtomicInteger counter = chunkCounters.computeIfAbsent(key, k -> new AtomicInteger());
        return counter.incrementAndGet() <= (HappyNewYear.limit + 1);
    }

    private void scheduleSpawn(Location location, long delayTicks) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        scheduler.runRegionLater(location.getWorld(), chunkX, chunkZ, delayTicks, task -> spawnFireworks(location));
    }

    private static final class ChunkKey {
        private final UUID worldId;
        private final int x;
        private final int z;

        private ChunkKey(UUID worldId, int x, int z) {
            this.worldId = worldId;
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ChunkKey chunkKey = (ChunkKey) o;
            return x == chunkKey.x && z == chunkKey.z && worldId.equals(chunkKey.worldId);
        }

        @Override
        public int hashCode() {
            int result = worldId.hashCode();
            result = 31 * result + x;
            result = 31 * result + z;
            return result;
        }
    }

}
