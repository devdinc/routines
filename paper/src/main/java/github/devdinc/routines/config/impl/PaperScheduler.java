package github.devdinc.routines.config.impl;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import github.devdinc.routines.PaperContext;
import github.devdinc.routines.impl.GenericCancellable;
import github.devdinc.routines.util.ReflectiveCancellable;
import github.devdinc.routines.util.Scheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Paper/Folia scheduler that uses a stored PaperContext.
 */
public class PaperScheduler implements Scheduler {

    private final Plugin plugin;
    private final PaperContext context; // context is provided in constructor

    public PaperScheduler(Plugin plugin, PaperContext context) {
        this.plugin = plugin;
        this.context = context;
        if (context == null) throw new IllegalArgumentException("PaperContext cannot be null");
    }

    @Override
    public ReflectiveCancellable schedule(Runnable runnable, Duration after, Duration every) {
        Object anchor = context.anchor();
        Runnable retire = context.retire();

        long amillis = after.toMillis();
        long emillis = every.toMillis();
        long delayTicks = Math.max(1, amillis / 50);
        long periodTicks = emillis / 50;

        GenericCancellable fallback = new GenericCancellable();
        ScheduledTask scheduled = null;

        try {
            // =========================
            // ENTITY
            // =========================
            if (anchor instanceof Entity entity) {
                if (periodTicks > 0) {
                    scheduled = entity.getScheduler().runAtFixedRate(plugin,
                            st -> { if (!fallback.isCancelled()) runnable.run(); },
                            retire, delayTicks, periodTicks);
                } else {
                    scheduled = entity.getScheduler().runDelayed(plugin,
                            st -> { if (!fallback.isCancelled()) runnable.run(); },
                            retire, delayTicks);
                }
            }
            // =========================
            // LOCATION / REGION
            // =========================
            else if (anchor instanceof Location loc) {
                if (periodTicks > 0) {
                    scheduled = Bukkit.getRegionScheduler().runAtFixedRate(plugin, loc,
                            st -> { if (!fallback.isCancelled()) runnable.run(); },
                            delayTicks, periodTicks);
                } else {
                    scheduled = Bukkit.getRegionScheduler().runDelayed(plugin, loc,
                            st -> { if (!fallback.isCancelled()) runnable.run(); },
                            delayTicks);
                }
            }
            // =========================
            // EXECUTOR (virtual threads or custom)
            // =========================
            else if (anchor instanceof Executor executor) {
                executor.execute(() -> {
                    try {
                        if (amillis > 0) Thread.sleep(amillis);
                        if (periodTicks > 0) {
                            while (!fallback.isCancelled()) {
                                runnable.run();
                                Thread.sleep(periodTicks * 50);
                            }
                        } else {
                            runnable.run();
                        }
                    } catch (InterruptedException ignored) {}
                });
            }
            // =========================
            // STRING-BASED: "async" or "global"
            // =========================
            else if ("async".equals(anchor)) {
                if (periodTicks > 0) {
                    scheduled = Bukkit.getAsyncScheduler().runAtFixedRate(plugin,
                            st -> { if (!fallback.isCancelled()) runnable.run(); },
                            amillis, emillis, TimeUnit.MILLISECONDS);
                } else {
                    scheduled = Bukkit.getAsyncScheduler().runDelayed(plugin,
                            st -> { if (!fallback.isCancelled()) runnable.run(); },
                            amillis, TimeUnit.MILLISECONDS);
                }
            }
            else if ("global".equals(anchor)) {
                if (periodTicks > 0) {
                    scheduled = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,
                            st -> { if (!fallback.isCancelled()) runnable.run(); },
                            delayTicks, periodTicks);
                } else {
                    scheduled = Bukkit.getGlobalRegionScheduler().runDelayed(plugin,
                            st -> { if (!fallback.isCancelled()) runnable.run(); },
                            delayTicks);
                }
            }
            else {
                throw new IllegalArgumentException("Unsupported anchor type: " + anchor);
            }

        } catch (Throwable ignored) {}
        return new ReflectiveCancellable(new ScheludedDelegate(scheduled, fallback));
    }

    static class ScheludedDelegate {
        private ScheduledTask task; // EntityScheduler can return null when entity is retired
        private GenericCancellable cancellable;

        protected ScheludedDelegate(ScheduledTask task, GenericCancellable cancellable) {
            this.task = task;
            this.cancellable = cancellable;
        }
        
        public void cancel(){
            cancellable.cancel();
            if(task != null)
            task.cancel();
        }

        public boolean isCancelled(){
            return cancellable.isCancelled() || task == null || task.isCancelled();
        }
    }
}
