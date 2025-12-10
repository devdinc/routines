package github.devdinc.routines.config;

import java.time.Duration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import github.devdinc.routines.impl.GenericCancellable;
import github.devdinc.routines.util.PaperContext;
import github.devdinc.routines.util.ReflectiveCancellable;
import github.devdinc.routines.util.Scheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Since scheduling happens after previousTask completes, it is more likely that
 * entity is retired. CARE
 */
public class PaperScheduler implements Scheduler {

    private final Plugin plugin;

    public PaperScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ReflectiveCancellable schedule(
            Runnable runnable,
            Duration after,
            Duration every,
            Object contx) {

        if (!(contx instanceof PaperContext context))
            throw new IllegalArgumentException("Context must be of type PaperContext");

        PaperContext ctx = (PaperContext) context;

        Object anchor = ctx.anchor(); // Entity | Location | null
        Runnable retire = ctx.retire(); // Runnable | null

        long delayTicks = Math.max(1, after.toMillis() / 50);
        long periodTicks = (every == null || every.isZero())
                ? 0
                : Math.max(1, every.toMillis() / 50);

        GenericCancellable fallback = new GenericCancellable();
        ScheduledTask scheduled = null;

        try {

            // =====================================
            // ENTITY-SCOPED EXECUTION
            // =====================================
            if (anchor instanceof Entity entity) {

                if (periodTicks > 0) {
                    scheduled = entity.getScheduler().runAtFixedRate(
                            plugin,
                            st -> {
                                if (!fallback.isCancelled())
                                    runnable.run();
                            },
                            retire,
                            delayTicks,
                            periodTicks);
                } else {
                    scheduled = entity.getScheduler().runDelayed(
                            plugin,
                            st -> {
                                if (!fallback.isCancelled())
                                    runnable.run();
                            },
                            retire,
                            delayTicks);
                }
            }

            // =====================================
            // LOCATION (REGION) EXECUTION
            // =====================================
            else if (anchor instanceof Location loc) {

                if (periodTicks > 0) {
                    scheduled = Bukkit.getRegionScheduler().runAtFixedRate(
                            plugin,
                            loc,
                            st -> {
                                if (!fallback.isCancelled())
                                    runnable.run();
                            },
                            delayTicks,
                            periodTicks);
                } else {
                    scheduled = Bukkit.getRegionScheduler().runDelayed(
                            plugin,
                            loc,
                            st -> {
                                if (!fallback.isCancelled())
                                    runnable.run();
                            },
                            delayTicks);
                }
            }

            // =====================================
            // GLOBAL EXECUTION
            // =====================================
            else {

                if (periodTicks > 0) {
                    scheduled = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                            plugin,
                            st -> {
                                if (!fallback.isCancelled())
                                    runnable.run();
                            },
                            delayTicks,
                            periodTicks);
                } else {
                    scheduled = Bukkit.getGlobalRegionScheduler().runDelayed(
                            plugin,
                            st -> {
                                if (!fallback.isCancelled())
                                    runnable.run();
                            },
                            delayTicks);
                }
            }

        } catch (Throwable ignored) {
            // Fell back to generic scheduler below
        }

        // If Folia scheduling succeeded
        if (scheduled != null) {
            return new ReflectiveCancellable(scheduled);
        }

        // Fallback generic cancellable
        return new ReflectiveCancellable(fallback);
    }
}