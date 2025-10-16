package com.devdinc.routines;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

// currently folia schedulers are not implemented in mock bukkit so RoutineTest Class is useless.
public class RoutineTest {

	private Plugin plugin;

	@BeforeEach
	void setUp() {
		MockBukkit.mock(); // starts a mock server
		plugin = MockBukkit.createMockPlugin(); // creates a mock plugin
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock(); // shuts down the mock server
	}

	@Test
	void testRoutineWithPluginRuns() {
		AtomicBoolean executed = new AtomicBoolean(false);

		Routine.now(plugin)
				.sync()
				.run(() -> executed.set(true))
				.join(); // should run immediately

		assertTrue(executed.get(), "Routine should execute using the provided Plugin context");
	}

	@Test
	void testRoutineThenWithPlugin() {
		AtomicBoolean executed = new AtomicBoolean(false);

		Routine.now(plugin)
				.sync()
				.supply(() -> "Hello")
				.then()
				.accept(result -> executed.set(result.equals("Hello")))
				.join();

		assertTrue(executed.get(), "Routine then() should receive the correct previous result using Plugin context");
	}
}
