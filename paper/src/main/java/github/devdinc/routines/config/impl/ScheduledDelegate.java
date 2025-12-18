package github.devdinc.routines.config.impl;

import github.devdinc.routines.impl.GenericCancellable;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class ScheduledDelegate {
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