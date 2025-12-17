package github.devdinc.routines;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Execution context for Paper/Folia tasks.
 * Anchor defines where the task will run:
 * - Entity -> entity scheduler
 * - Location -> region scheduler
 * - Executor -> execute on this executor (covers virtual threads)
 * - String "async" -> Paper async scheduler
 * - String "global" -> global region scheduler
 */
public record PaperContext(
        Object anchor,    // Entity | Location | Executor | "async" | "global"
        Runnable retire   // optional retire logic, can be null
) {

    public static PaperContext entity(Entity e, Runnable retire) {
        return new PaperContext(e, retire);
    }

    public static PaperContext location(Location loc) {
        return new PaperContext(loc, null);
    }

    public static PaperContext executor(Executor exec) {
        return new PaperContext(exec, null);
    }

    public static PaperContext async() {
        return new PaperContext("async", null);
    }

    public static PaperContext global() {
        return new PaperContext("global", null);
    }

    public static PaperContext virtual() {
        return new PaperContext(Executors.newVirtualThreadPerTaskExecutor(), null);
    }
}
