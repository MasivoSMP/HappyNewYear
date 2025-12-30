package com.xSavior_of_God.HappyNewYear.hooks.imagefireworksreborn;

import com.xSavior_of_God.HappyNewYear.utils.Utils;
import me.lukyn76.imagefireworkspro.core.ImageFirework;
import me.lukyn76.imagefireworkspro.util.ConfigManager;
import org.bukkit.Location;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * lukyn76's ImageFireworksPro
 *
 * @Source https://github.com/heyxmirko/ImageFireworksPro
 * @Version 1.1.6
 */
public class ImageFireworksRebornHook {
    private List<String> fireworksTypes = new ArrayList<>();
    private Object renderer;
    private Method renderMethod;
    private Method legacyExplodeMethod;

    public ImageFireworksRebornHook(List<String> fireworksTypes) {
        if(fireworksTypes.stream().anyMatch("RANDOM"::equalsIgnoreCase)) {
            this.fireworksTypes.addAll(this.allFireworks());
        } else {
            this.fireworksTypes = fireworksTypes;
        }
        initRenderer();
    }

    public void spawnFirework(Location location) throws IOException {
        final String firework = fireworksTypes.get(ThreadLocalRandom.current().nextInt(fireworksTypes.size()));
        ImageFirework imageFirework = ConfigManager.getImageFirework(firework);
        if(imageFirework == null) {
            Utils.log(Level.WARNING, "&e[HappyNewYear] &cImageFirework " + firework + " not found!");
            return;
        }
        if (renderer != null && renderMethod != null) {
            try {
                renderMethod.invoke(renderer, imageFirework, location);
            } catch (Exception e) {
                Utils.log(Level.WARNING, "&e[HappyNewYear] &cFailed to render ImageFirework: " + e.getMessage());
            }
            return;
        }
        if (legacyExplodeMethod != null) {
            try {
                legacyExplodeMethod.invoke(imageFirework, location, (double) location.getYaw());
            } catch (Exception e) {
                Utils.log(Level.WARNING, "&e[HappyNewYear] &cFailed to explode ImageFirework: " + e.getMessage());
            }
            return;
        }
        Utils.log(Level.WARNING, "&e[HappyNewYear] &cNo compatible ImageFireworksPro API found.");
    }

    public Collection<? extends String> allFireworks() {
        return ConfigManager.getAvailableImageFireworks();
    }

    private void initRenderer() {
        try {
            Class<?> pluginClass = Class.forName("me.lukyn76.imagefireworkspro.ImageFireworksPro");
            Method getInstance = pluginClass.getMethod("getInstance");
            Object plugin = getInstance.invoke(null);
            if (plugin == null) {
                return;
            }

            Method getScheduler = pluginClass.getMethod("getSchedulerAdapter");
            Method getPlanCache = pluginClass.getMethod("getImagePlanCache");
            Method getEffectManager = pluginClass.getMethod("getEffectManager");

            Object scheduler = getScheduler.invoke(plugin);
            Object planCache = getPlanCache.invoke(plugin);
            Object effectManager = getEffectManager.invoke(plugin);
            if (scheduler == null || planCache == null || effectManager == null) {
                return;
            }

            Class<?> rendererClass = Class.forName("me.lukyn76.imagefireworkspro.runtime.ImageFireworkRenderer");
            Class<?> schedulerClass = Class.forName("me.lukyn76.imagefireworkspro.scheduler.SchedulerAdapter");
            Class<?> planCacheClass = Class.forName("me.lukyn76.imagefireworkspro.image.ImagePlanCache");
            Class<?> effectManagerClass = Class.forName("me.lukyn76.imagefireworkspro.runtime.ImageEffectManager");
            Constructor<?> constructor = rendererClass.getConstructor(pluginClass, schedulerClass, planCacheClass, effectManagerClass);
            this.renderer = constructor.newInstance(plugin, scheduler, planCache, effectManager);
            this.renderMethod = rendererClass.getMethod("render", ImageFirework.class, Location.class);
            return;
        } catch (Exception ignored) {
        }

        try {
            legacyExplodeMethod = ImageFirework.class.getMethod("explode", Location.class, double.class);
        } catch (Exception ignored) {
        }
    }
}
