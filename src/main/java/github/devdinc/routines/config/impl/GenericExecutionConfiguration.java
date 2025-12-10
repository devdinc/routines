package github.devdinc.routines.config.impl;

import java.util.concurrent.Executor;

import github.devdinc.routines.config.ExecutionConfiguration;

/**
 * A generic implementation of {@link ExecutionConfiguration}.
 */
public class GenericExecutionConfiguration implements ExecutionConfiguration {

    @Override
    public Executor executor() {
        return Runnable::run;
    }
}
