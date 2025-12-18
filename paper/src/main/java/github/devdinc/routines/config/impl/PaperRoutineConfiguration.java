package github.devdinc.routines.config.impl;

import org.bukkit.plugin.Plugin;

import github.devdinc.routines.config.RoutineConfiguration;
import github.devdinc.routines.config.impl.ehc.LogAndContinue;

public class PaperRoutineConfiguration {
    public static RoutineConfiguration paperRoutineConfiguration(Plugin plugin) {
        return new RoutineConfiguration(new LogAndContinue(), new PaperSchedulerConfiguration(plugin));
    }
}
