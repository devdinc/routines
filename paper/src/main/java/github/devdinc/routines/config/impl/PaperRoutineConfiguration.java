package github.devdinc.routines.config.impl;

import java.time.Duration;

import org.bukkit.plugin.Plugin;

import github.devdinc.routines.config.RoutineConfiguration;
import github.devdinc.routines.config.SchedulingConfiguration;
import github.devdinc.routines.config.impl.ehc.LogAndContinue;
import github.devdinc.routines.util.Scheduler;

public class PaperRoutineConfiguration {
    public static RoutineConfiguration paperRoutineConfiguration(Plugin plugin) {
        return new RoutineConfiguration(new LogAndContinue(),
                new SchedulingConfiguration.ALL() {

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
                        return new PaperScheduler(plugin);
                    }

                    @Override
                    public Object context() {
                        return null;
                    }

                });
    }
}
