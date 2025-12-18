package github.devdinc.routines.config.impl;

import java.time.Duration;
import java.util.concurrent.Executor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import github.devdinc.routines.PaperContext;
import github.devdinc.routines.config.SchedulingConfiguration;
import github.devdinc.routines.impl.GenericCancellable;
import github.devdinc.routines.util.ReflectiveCancellable;
import github.devdinc.routines.util.Scheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.TimeUnit;

/**
 * Unified Paper scheduler configuration, similar to VirtualSchedulerSchedulingConfiguration.
 */
public class PaperSchedulerConfiguration implements SchedulingConfiguration.ALL {

    private final Plugin plugin;

    public PaperSchedulerConfiguration(Plugin plugin) {
        
        this.plugin = plugin;
    }

    @Override
    public Duration after() {
        return Duration.ZERO;
    }

    @Override
    public Duration every() {
        return Duration.ZERO;
    }

    @Override
    public Scheduler scheduler() {
        return (task, after, every, ctx) -> {
            PaperContext context = (PaperContext) ctx;
            if (context == null) throw new IllegalArgumentException("PaperContext cannot be null");
            GenericCancellable token = new GenericCancellable();
            Object anchor = context.anchor();
            Runnable retire = context.retire();

            long amillis = after.toMillis();
            long emillis = every.toMillis();
            long delayTicks = Math.max(1, amillis / 50);
            long periodTicks = emillis / 50;

            ScheduledTask scheduled = null;

            try {
                if (anchor instanceof Entity entity) {
                    if (periodTicks > 0) {
                        scheduled = entity.getScheduler().runAtFixedRate(plugin,
                                st -> { if (!token.isCancelled()) task.run(); },
                                retire, delayTicks, periodTicks);
                    } else {
                        scheduled = entity.getScheduler().runDelayed(plugin,
                                st -> { if (!token.isCancelled()) task.run(); },
                                retire, delayTicks);
                    }
                } else if (anchor instanceof Location loc) {
                    if (periodTicks > 0) {
                        scheduled = Bukkit.getRegionScheduler().runAtFixedRate(plugin, loc,
                                st -> { if (!token.isCancelled()) task.run(); },
                                delayTicks, periodTicks);
                    } else {
                        scheduled = Bukkit.getRegionScheduler().runDelayed(plugin, loc,
                                st -> { if (!token.isCancelled()) task.run(); },
                                delayTicks);
                    }
                } else if (anchor instanceof Executor executor) {
                    executor.execute(() -> {
                        try {
                            if (amillis > 0) Thread.sleep(amillis);
                            if (periodTicks > 0) {
                                while (!token.isCancelled()) {
                                    task.run();
                                    Thread.sleep(periodTicks * 50);
                                }
                            } else {
                                task.run();
                            }
                        } catch (InterruptedException ignored) {}
                    });
                } else if ("async".equals(anchor)) {
                    if (periodTicks > 0) {
                        scheduled = Bukkit.getAsyncScheduler().runAtFixedRate(plugin,
                                st -> { if (!token.isCancelled()) task.run(); },
                                amillis, emillis, TimeUnit.MILLISECONDS);
                    } else {
                        scheduled = Bukkit.getAsyncScheduler().runDelayed(plugin,
                                st -> { if (!token.isCancelled()) task.run(); },
                                amillis, TimeUnit.MILLISECONDS);
                    }
                } else if ("global".equals(anchor)) {
                    if (periodTicks > 0) {
                        scheduled = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,
                                st -> { if (!token.isCancelled()) task.run(); },
                                delayTicks, periodTicks);
                    } else {
                        scheduled = Bukkit.getGlobalRegionScheduler().runDelayed(plugin,
                                st -> { if (!token.isCancelled()) task.run(); },
                                delayTicks);
                    }
                } else {
                    throw new IllegalArgumentException("Unsupported anchor type: " + anchor);
                }
            } catch (Throwable ignored) {}

            return new ReflectiveCancellable(new ScheduledDelegate(scheduled, token));
        };
    }

    static class ScheduledDelegate {
        private final ScheduledTask task;
        private final GenericCancellable cancellable;

        ScheduledDelegate(ScheduledTask task, GenericCancellable cancellable) {
            this.task = task;
            this.cancellable = cancellable;
        }

        public void cancel() {
            cancellable.cancel();
            if (task != null) task.cancel();
        }

        public boolean isCancelled() {
            return cancellable.isCancelled() || task == null || task.isCancelled();
        }
    }

    @Override
    public PaperContext context() {
        return null;
    }
}
