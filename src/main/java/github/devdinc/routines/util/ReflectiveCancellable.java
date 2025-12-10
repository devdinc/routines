package github.devdinc.routines.util;

import java.lang.reflect.Method;

/**
 * A class that provides a reflective way to cancel an operation.
 * It wraps a delegate object and uses reflection to call its `cancel()` and
 * `isCancelled()` methods.
 */
public final class ReflectiveCancellable {

    private final Object delegate;
    private final Method cancelMethod;
    private final Method isCancelledMethod;

    /**
     * Creates a new `ReflectiveCancellable` instance.
     * It finds the `cancel()` and `isCancelled()` methods on the delegate object.
     *
     * @param delegate The object to delegate the cancellation to.
     * @throws IllegalArgumentException if the delegate does not have `cancel()` or
     *                                  `isCancelled()` methods.
     */
    public ReflectiveCancellable(Object delegate) {
        this.delegate = delegate;
        try {
            cancelMethod = delegate.getClass().getMethod("cancel");
            isCancelledMethod = delegate.getClass().getMethod("isCancelled");
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Delegate does not have cancel() or isCancelled() methods", e);
        }
    }

    /**
     * Creates a new `ReflectiveCancellable` instance with the given delegate and
     * methods.
     *
     * @param delegate          The object to delegate the cancellation to.
     * @param cancelMethod      The `cancel()` method to be called on the delegate.
     * @param isCancelledMethod The `isCancelled()` method to be called on the
     *                          delegate.
     */
    public ReflectiveCancellable(Object delegate, Method cancelMethod, Method isCancelledMethod) {
        this.delegate = delegate;
        this.cancelMethod = cancelMethod;
        this.isCancelledMethod = isCancelledMethod;
    }

    /**
     * Cancels the operation by calling the `cancel()` method on the delegate.
     */
    public void cancel() {
        try {
            cancelMethod.invoke(delegate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Checks if the operation is cancelled by calling the `isCancelled()` method on
     * the delegate.
     *
     * @return `true` if the operation is cancelled, `false` otherwise.
     */
    public boolean isCancelled() {
        try {
            Object val = isCancelledMethod.invoke(delegate);
            return val instanceof Boolean && (Boolean) val;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
