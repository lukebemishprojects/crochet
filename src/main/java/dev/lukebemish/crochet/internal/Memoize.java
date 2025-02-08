package dev.lukebemish.crochet.internal;

import org.gradle.api.Action;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public sealed abstract class Memoize<T> implements Supplier<T> {
    public abstract Memoize<T> configure(Action<T> action);

    public static <T> Memoize<T> of(Supplier<T> supplier) {
        return new Impl<>(supplier);
    }

    public final void fix() {
        get();
    }

    private sealed interface Status<T> {
        T get();

        record Uninitialized<T>(List<Action<T>> actions) implements Status<T> {
            @Override
            public T get() {
                throw new IllegalStateException("Value not initialized");
            }
        }
        record Initialized<T>(T value) implements Status<T> {
            @Override
            public T get() {
                return value;
            }

        }
        record Exception<T>(RuntimeException exception) implements Status<T> {
            @Override
            public T get() {
                throw exception;
            }
        }
    }

    private static final class Impl<T> extends Memoize<T> {
        private final AtomicReference<Status<T>> value = new AtomicReference<>(new Status.Uninitialized<>(new ArrayList<>()));
        private @Nullable Supplier<T> supplier;

        Impl(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public Memoize<T> configure(Action<T> action) {
            value.updateAndGet(status -> switch (status) {
                case Status.Exception<T> v -> v;
                case Status.Initialized<T> v -> {
                    action.execute(v.value());
                    yield v;
                }
                case Status.Uninitialized<T> v -> {
                    v.actions().add(action);
                    yield v;
                }
            });
            return this;
        }

        @Override
        public T get() {
            return value.updateAndGet(status -> switch (status) {
                case Status.Uninitialized<T> uninitialized -> {
                    try {
                        try {
                            var value = Objects.requireNonNull(supplier).get();
                            uninitialized.actions.forEach(action -> action.execute(value));
                            yield new Status.Initialized<>(value);
                        } catch (Throwable e) {
                            RuntimeException exception;
                            if (e instanceof RuntimeException runtimeException) {
                                exception = runtimeException;
                            } else {
                                exception = new RuntimeException(e);
                            }
                            yield new Status.Exception<>(exception);
                        }
                    } finally {
                        supplier = null;
                    }
                }
                case Status.Initialized<T> initialized -> initialized;
                case Status.Exception<T> exception -> exception;
            }).get();
        }
    }
}
