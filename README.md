# Routines
[![GitHub release](https://img.shields.io/github/v/tag/devdinc/routines?label=version)](https://github.com/devdinc/routines/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![](https://jitpack.io/v/devdinc/routines.svg)](https://jitpack.io/#devdinc/routines)
[![Java](https://img.shields.io/badge/Java-21+-blue)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)

A lightweight, fluent Java library for scheduling, chaining, and class-based asynchronous tasks. Powered by Java 21+ virtual threads for high-concurrency performance.

---

## Requirements
- **Java 21+** (Requires Virtual Threads support [To be honest you could probably replace VirtualSchedulerSchedulingConfiguration in a fork, and it would probably work fine with lower versions]).

---

## Installation

### Gradle
~~~groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.devdinc:routines-core:<version>'
}
~~~

### Maven
~~~xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.devdinc</groupId>
    <artifactId>routines-core</artifactId>
    <version>{version}</version>
</dependency>
~~~

---

## 1. Class-Based Routines
For reusable logic, extend the abstract `Task<I, O>` class. This allows you to encapsulate state, configuration, and specific execution logic.

~~~java
import github.devdinc.routines.Task;
// ... other imports

public class MyRepeatingTask extends Task<String, Integer> {

    @Override
    protected Integer apply(String input) {
        // Core execution logic: receives String, returns Integer
        // For repeating Tasks:
        // When apply returns a value that is:
        // Non-null, and
        // Not an instance of Result.Error<?, Exception>,
        // the task is considered successfully completed, and Routines will:
        // Finalize the current execution, and
        // Automatically schedule the next execution based on the chain definition.
        return input.length();
    }

    @Override
    public Duration every() {
        return Duration.ofSeconds(10); // Runs periodically
    }

    @Override
    protected Task<Integer, ?> next(Integer result) {
        // Optional: Chain the result to another task
        return new AnotherTask(result);
    }
}

// Execution via RoutineService is the preferred way to schedule a Task
RoutineService rs = new RoutineService();
rs.schedule(new MyRepeatingTask("Initial Input"));

// To wait for a result from a Task instance:
// Task<?, ?> task = new MyTask();
// rs.schedule(task);
// Object result = task.join(); // Blocks until completion
~~~

---

## 2. Fluent API Routines
Define chains of execution on the fly using `RoutineService.fluent()`.

~~~java
RoutineService rs = new RoutineService();

rs.fluent(Instant.now())
    .after(Duration.ofSeconds(2))
    .supply(() -> "Hello") // Start with a Supplier (input is Void)
    .then() // Readability method: No functional change
    .apply(s -> s + " Async World") // Function: transforms input (String) to new output (String)
    .accept(System.out::println); // Consumer: takes input (String), returns Void
~~~

---

## 3. Cron Jobs
Schedule tasks using standard unix-style cron expressions by extending `CronTask`.

~~~java
import github.devdinc.routines.CronTask;
import github.devdinc.routines.cron.UnixCronParser;

Cron cron = UnixCronParser.parse("*/5 * * * *"); // Every 5 minutes

new CronTask(cron) {
    @Override
    protected void run() {
        System.out.println("Cron trigger fired at: " + LocalDateTime.now());
    }
};
// CronTask schedules itself upon instantiation.
~~~

---

## 4. Error Handling
Tasks can control their behavior upon encountering an `Exception` by overriding `onUncaughtException`. The built-in strategies are:

| Strategy | Description |
| :--- | :--- |
| `Strategy.STOP_ALL` | **Stop All**: Cancels the current execution and any future repetitions, doesn't chain into next task. |
| `Strategy.CONTINUE` | **Continue**: Routine continues to the next scheduled execution. |

There is already implemented configurations for exception handling: Carry, LogAndContinue, and LogAndStop.

Example of overriding the default behavior:
~~~java
@Override
public ExceptionHandleRecord onUncaughtException(Task<?, ?> task, Exception ex) {
    // If an error occurs, cancel the routine immediately and return null as the final result
    return new ExceptionHandleRecord(Strategy.STOP_ALL, null);
}
~~~

---

## Context (Advanced)
An opaque object whose meaning is defined exclusively by the scheduler implementation.
The `SchedulingConfiguration` interface does not prescribe:
* what context represents
* how it is interpreted
* whether it is an `Executor`, a thread model, or something else

Only the **scheduler** decides how to interpret it.

### How This Scheduler Defines `context`

In `VirtualSchedulerSchedulingConfiguration` (default in core), the scheduler chooses to interpret `context()` as an `ExecutorContext`.

```java
ExecutorContext context = context();
context = context == null ? ExecutorContext.INLINE : context;
context.getContext().execute(task);
```

So, in this implementation only:

* `context` is expected to be an `ExecutorContext`
* `ExecutorContext` resolves to a Java `Executor`
* That `Executor` determines how the task is executed

This is an implementation decision, not an API guarantee.

---

#### How Each Context Is Handled By This Scheduler

| Context Value | Scheduler Interpretation    | Result                      |
| ------------- | --------------------------- | --------------------------- |
| `null`        | Default to `INLINE`         | Task runs inline            |
| `INLINE`      | `Runnable::run`             | Same thread                 |
| `ASYNC`       | `ForkJoinPool.commonPool()` | Pooled execution            |
| `VIRTUAL`     | Virtual-thread executor     | One virtual thread per task |
| `PROVIDER`    | User-supplied executor      | Fully custom                |

Again: these meanings exist only because this scheduler assigns them.

---

## Execution Control
| Method | Description |
| :--- | :--- |
| `task.join()` | Blocks the calling thread until the task completes and returns its final result. |
| `task.join(Duration timeout)` | Blocks with a specified timeout. Throws `IllegalStateException` on timeout. |
| `task.cancel()` | Cancels any scheduled repetitions and releases threads waiting on `join()`. |
| `task.onComplete(Consumer)` | Non-blocking callback executed when the task is complete. |

---

## Future Development
We plan to introduce composition methods similar to `CompletableFuture` (e.g., `thenCompose`, `handle`) to further enhance the flexibility and expressiveness of routine chaining.