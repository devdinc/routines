package github.devdinc.routines.config.impl;

import java.time.Duration;

import org.bukkit.plugin.Plugin;

import github.devdinc.routines.PaperContext;
import github.devdinc.routines.config.RoutineConfiguration;
import github.devdinc.routines.config.SchedulingConfiguration;
import github.devdinc.routines.config.impl.ehc.LogAndContinue;
import github.devdinc.routines.util.Scheduler;

public class PaperRoutineConfiguration {
    public static RoutineConfiguration paperRoutineConfiguration(Plugin plugin) {
        return new RoutineConfiguration(new LogAndContinue(), new PaperSchedulerConfiguration(plugin));
    }
}
