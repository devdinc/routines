# Routines
[![GitHub release](https://img.shields.io/github/v/tag/devdinc/routines?label=version)](https://github.com/devdinc/routines/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![](https://jitpack.io/v/devdinc/routines.svg)](https://jitpack.io/#jitpack/routines)
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
    implementation 'com.github.devdinc:routines:<version>'
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
    <artifactId>routines</artifactId>
    <version>{version}</version>
</dependency>
~~~

---

## Features
* **Fluent API**: Chain tasks easily with `supply`, `apply`, and `accept`.
* **Class-Based Tasks**: Define reusable, complex logic by extending `Task<I, O>`.
* **Advanced Scheduling**: Support for Cron jobs, fixed delays, and periodic intervals.
* **Virtual Thread Support**: Designed for high scalability using modern Java concurrency.
* **Error Management**: Built-in strategies for exception handling.

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
| `Strategy.STOP_ALL` | **Stop All**: Cancels the current execution and any future repetitions. |
| `Strategy.CONTINUE` | **Continue**: Routine continues to the next scheduled execution. |

There is already implemented configurations for exception handling, CARRY, LOG_AND_CONTINUE, and LOG_AND_STOP.

Example of overriding the default behavior:
~~~java
@Override
public ExceptionHandleRecord onUncaughtException(Task<?, ?> task, Exception ex) {
    // If an error occurs, cancel the routine immediately and return null as the final result
    return new ExceptionHandleRecord(Strategy.STOP_ALL, null);
}
~~~

---

## Execution Control
| Method | Description |
| :--- | :--- |
| `task.join()` | Blocks the calling thread until the task completes and returns its final result. |
| `task.join(Duration timeout)` | Blocks with a specified timeout. Throws `IllegalStateException` on timeout. |
| `task.cancel()` | Cancels any scheduled repetitions and releases threads waiting on `join()`. |
| `task.onComplete(Consumer)` | Non-blocking callback executed when the task and its chain complete. |

---

## Future Development
We plan to introduce composition methods similar to `CompletableFuture` (e.g., `thenCompose`, `handle`) to further enhance the flexibility and expressiveness of routine chaining.