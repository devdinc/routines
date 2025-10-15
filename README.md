# Routines

[![GitHub release](https://img.shields.io/github/v/release/devdinc/routines?label=version)](https://github.com/devdinc/routines/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![JitPack](https://img.shields.io/jitpack/v/github/devdinc/routines)](https://jitpack.io/#devdinc/routines)
[![Java](https://img.shields.io/badge/Java-21+-blue)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)

A lightweight, fluent Java library for scheduling and chaining asynchronous tasks.

---

## Usage

### Simple Hello World (Delayed Execution)
Print "Hello World" after a short delay.

~~~java
Routine.now()
    .run(() -> System.out.println("Step 1: Starting now"))
    .then()
    .after(Duration.ofSeconds(2))
    .supply(() -> "Hello")
    .then()
    .apply(s -> s + " World")
    .then()
    .accept(System.out::println)
    .join(); // Waits for completion
~~~


---

### Async Polling (Repeated Task Until Condition)
Execute a task every 2 minutes asynchronously until a condition is met.
- Runs the task every 2 minutes asynchronously.
- Stops when the task returns a non-null result.

~~~java
Routine.at(LocalDateTime.now())
    .every(Duration.ofMinutes(2))
    .async()
    .supply(() -> {
        Double random = new Random().nextDouble();
        if (random > 0.9) return "Success"; // Ends the routine
        else return null; // Continues polling
    })
    .then()
    .accept(result -> System.out.println("Polling result: " + result));
~~~

---

### Cron Job 
Schedule a task using a standard cron expression.

~~~java
Routine.cron("* * * * *") // Runs every minute
    .run(() -> System.out.println("Cron task executed at: " + LocalDateTime.now()));
~~~
---

## Requirements

- Virtual thread support (Java 21+)

---

## Installation

### Gradle
~~~
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.devdinc:routines:1.0.0'
}
~~~
