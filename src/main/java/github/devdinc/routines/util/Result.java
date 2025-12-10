package github.devdinc.routines.util;

import java.util.Objects;
import java.util.function.Function;

/**
 * A class that represents a result of a computation that can either be
 * successful (Ok) or an error (Err).
 * This is a functional programming construct that helps in handling errors in a
 * more elegant way than using exceptions.
 * It is particularly useful in repeated tasks, as it allows to stop the
 * execution on an Ok result.
 *
 * @param <R> the type of the successful result
 * @param <E> the type of the error
 */
public abstract class Result<R, E> {

    private Result() {
    }

    /**
     * Creates a new Ok result.
     *
     * @param value the successful result
     * @param <R>   the type of the successful result
     * @param <E>   the type of the error
     * @return a new Ok result
     */
    public static <R, E> Result<R, E> ok(R value) {
        return new Ok<>(value);
    }

    /**
     * Creates a new Err result.
     *
     * @param error the error
     * @param <R>   the type of the successful result
     * @param <E>   the type of the error
     * @return a new Err result
     */
    public static <R, E> Result<R, E> err(E error) {
        return new Err<>(error);
    }

    /**
     * @return true if the result is Ok, false otherwise
     */
    public abstract boolean isOk();

    /**
     * @return true if the result is Err, false otherwise
     */
    public abstract boolean isErr();

    /**
     * @return the successful result if the result is Ok, otherwise throws an
     *         exception
     */
    public abstract R unwrap();

    /**
     * @return the error if the result is Err, otherwise throws an exception
     */
    public abstract E unwrapErr();

    /**
     * Maps the successful result to a new value.
     *
     * @param mapper the function to map the successful result
     * @param <R2>   the type of the new successful result
     * @return a new Result with the mapped successful result, or the original error
     */
    public abstract <R2> Result<R2, E> map(Function<? super R, ? extends R2> mapper);

    /**
     * Maps the error to a new value.
     *
     * @param mapper the function to map the error
     * @param <E2>   the type of the new error
     * @return a new Result with the mapped error, or the original successful result
     */
    public abstract <E2> Result<R, E2> mapErr(Function<? super E, ? extends E2> mapper);

    /**
     * Chains a new computation to the successful result.
     *
     * @param mapper the function to chain the new computation
     * @param <R2>   the type of the new successful result
     * @return a new Result from the chained computation, or the original error
     */
    public abstract <R2> Result<R2, E> andThen(
            Function<? super R, Result<R2, E>> mapper);

    /**
     * Matches the result with two functions, one for the successful result and one
     * for the error.
     *
     * @param onOk  the function to handle the successful result
     * @param onErr the function to handle the error
     * @param <T>   the type of the returned value
     * @return the value returned by the matching function
     */
    public abstract <T> T match(
            Function<? super R, ? extends T> onOk,
            Function<? super E, ? extends T> onErr);

    /**
     * A class that represents a successful result.
     *
     * @param <R> the type of the successful result
     * @param <E> the type of the error
     */
    public static final class Ok<R, E> extends Result<R, E> {
        private final R value;

        private Ok(R value) {
            this.value = value;
        }

        @Override
        public boolean isOk() {
            return true;
        }

        @Override
        public boolean isErr() {
            return false;
        }

        @Override
        public R unwrap() {
            return value;
        }

        @Override
        public E unwrapErr() {
            throw new IllegalStateException("unwrapErr() called on Ok");
        }

        @Override
        public <R2> Result<R2, E> map(Function<? super R, ? extends R2> mapper) {
            return new Ok<>(mapper.apply(value));
        }

        @Override
        public <E2> Result<R, E2> mapErr(Function<? super E, ? extends E2> mapper) {
            return new Ok<>(value);
        }

        @Override
        public <R2> Result<R2, E> andThen(
                Function<? super R, Result<R2, E>> mapper) {
            return Objects.requireNonNull(mapper.apply(value));
        }

        @Override
        public <T> T match(Function<? super R, ? extends T> onOk,
                Function<? super E, ? extends T> onErr) {
            return onOk.apply(value);
        }

        @Override
        public String toString() {
            return "Ok(" + value + ")";
        }
    }

    /**
     * A class that represents an error result.
     *
     * @param <R> the type of the successful result
     * @param <E> the type of the error
     */
    public static final class Err<R, E> extends Result<R, E> {
        private final E error;

        private Err(E error) {
            this.error = error;
        }

        @Override
        public boolean isOk() {
            return false;
        }

        @Override
        public boolean isErr() {
            return true;
        }

        @Override
        public R unwrap() {
            throw new IllegalStateException("unwrap() called on Err: " + error);
        }

        @Override
        public E unwrapErr() {
            return error;
        }

        @Override
        public <R2> Result<R2, E> map(Function<? super R, ? extends R2> mapper) {
            return new Err<>(error);
        }

        @Override
        public <E2> Result<R, E2> mapErr(Function<? super E, ? extends E2> mapper) {
            return new Err<>(mapper.apply(error));
        }

        @Override
        public <R2> Result<R2, E> andThen(
                Function<? super R, Result<R2, E>> mapper) {
            return new Err<>(error);
        }

        @Override
        public <T> T match(Function<? super R, ? extends T> onOk,
                Function<? super E, ? extends T> onErr) {
            return onErr.apply(error);
        }

        @Override
        public String toString() {
            return "Err(" + error + ")";
        }
    }
}
