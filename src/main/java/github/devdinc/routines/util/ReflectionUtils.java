package github.devdinc.routines.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import github.devdinc.routines.Task;

/**
 * A utility class for reflection.
 */
public class ReflectionUtils {
    private ReflectionUtils() {
    }

    /**
     * Checks if a task is a result task.
     *
     * @param clazs The class of the task.
     * @return True if the task is a result task, false otherwise.
     */
    public static boolean isResultTask(@SuppressWarnings("rawtypes") Class<? extends Task> clazs) {
        Type superType = clazs.getGenericSuperclass();
        if (!(superType instanceof ParameterizedType pt))
            return false;

        Type oType = pt.getActualTypeArguments()[1];

        if (oType instanceof Class<?> clazz)
            return Result.class.isAssignableFrom(clazz);

        if (oType instanceof ParameterizedType pto) {
            Type raw = pto.getRawType();
            if (!(raw instanceof Class<?> rawClass))
                return false;
            return Result.class.isAssignableFrom(rawClass);
        }

        return false;
    }

    /**
     * Checks if the second type of a result is an exception.
     *
     * @param clazs The class of the task.
     * @return True if the second type of the result is an exception, false
     *         otherwise.
     */
    public static boolean resultSecondTypeIsException(@SuppressWarnings("rawtypes") Class<? extends Task> clazs) {
        Type superType = clazs.getGenericSuperclass();
        if (!(superType instanceof ParameterizedType pt))
            return false;

        Type oType = pt.getActualTypeArguments()[1];
        if (!(oType instanceof ParameterizedType pto))
            return false;

        Type raw = pto.getRawType();
        if (!(raw instanceof Class<?> rawClass) || !Result.class.isAssignableFrom(rawClass))
            return false;

        Type[] args = pto.getActualTypeArguments();
        if (args.length < 2)
            return false;

        Type second = args[1];
        if (!(second instanceof Class<?> secondClass))
            return false;

        return Throwable.class.isAssignableFrom(secondClass);
    }
}
