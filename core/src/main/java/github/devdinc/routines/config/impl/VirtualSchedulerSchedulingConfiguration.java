package github.devdinc.routines.config.impl;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.LockSupport;

import github.devdinc.routines.config.SchedulingConfiguration;
import github.devdinc.routines.impl.GenericCancellable;
import github.devdinc.routines.util.ReflectiveCancellable;
import github.devdinc.routines.util.Scheduler;

/**
 * A generic implementation of {@link SchedulingConfiguration}.
 */
public class VirtualSchedulerSchedulingConfiguration implements SchedulingConfiguration.ALL, java.io.Serializable {

    @Override
    public Duration after() {
        return Duration.ZERO;
    }

    @Override
    public Duration every() {
        return Duration.ZERO;
    }

    public sealed interface ExecutorContext permits ExecutorContext.Async, ExecutorContext.Inline, ExecutorContext.Virtual, ExecutorContext.Provider  {
        Executor getContext();
        public static final ExecutorContext ASYNC = new Async();
        public static final ExecutorContext VIRTUAL = new Virtual();
        public static final ExecutorContext INLINE = new Inline();


        public static final class Async implements ExecutorContext {
            @Override
            public Executor getContext() {
                return ForkJoinPool.commonPool();
            }
        }

        public static final class Inline implements ExecutorContext {
            @Override
            public Executor getContext() {
                return Runnable::run;
            }
        }

        public static final class Virtual implements ExecutorContext {
            @Override
            public Executor getContext() {
                return Executors.newVirtualThreadPerTaskExecutor();
            }
        }

        public static final class Provider implements ExecutorContext {
            private final Executor executor;

            public Provider(Executor executor) {
                this.executor = executor;
            }
            
            @Override
            public Executor getContext() {
                return executor;
            }
        }
    }
    

    @Override
    public ExecutorContext context() {
        return ExecutorContext.INLINE;
    }

    @Override
    public Scheduler scheduler() {
        return (task, after, every) -> {
            GenericCancellable token = new GenericCancellable();

            Thread.startVirtualThread(() -> {
                ExecutorContext context = context();
                context = context == null ? ExecutorContext.INLINE : context;
                
                if (after.isPositive())
                    LockSupport.parkNanos(after.toNanos());
                if (token.isCancelled())
                    return;
                if (!every.isPositive()) {
                    context.getContext().execute(task);
                    return;
                }

                while (!token.isCancelled()) {
                    context.getContext().execute(task);
                    LockSupport.parkNanos(every.toNanos());
                }
            });

            return new ReflectiveCancellable(token);
        };
    }
}
