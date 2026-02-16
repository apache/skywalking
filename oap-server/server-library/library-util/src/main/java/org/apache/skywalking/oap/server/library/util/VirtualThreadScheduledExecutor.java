/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.library.util;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;

/**
 * A {@link ScheduledExecutorService} fully backed by virtual threads.
 *
 * <p>All methods — including {@code schedule()}, {@code scheduleAtFixedRate()},
 * and {@code scheduleWithFixedDelay()} — delegate to virtual threads.
 * Scheduling is implemented by sleeping in a virtual thread (which does not
 * block OS threads), eliminating the need for a platform timer thread.
 *
 * <p>This adapter bridges the gap between virtual thread executors (which return
 * {@link ExecutorService}) and frameworks like Armeria that require a
 * {@link ScheduledExecutorService} for their blocking task executor.
 */
@Slf4j
final class VirtualThreadScheduledExecutor implements ScheduledExecutorService {

    private final ExecutorService vtExecutor;

    VirtualThreadScheduledExecutor(final ExecutorService vtExecutor) {
        this.vtExecutor = vtExecutor;
    }

    // --- Core execution: delegate to virtual threads ---

    @Override
    public void execute(final Runnable command) {
        vtExecutor.execute(command);
    }

    @Override
    public Future<?> submit(final Runnable task) {
        return vtExecutor.submit(task);
    }

    @Override
    public <T> Future<T> submit(final Runnable task, final T result) {
        return vtExecutor.submit(task, result);
    }

    @Override
    public <T> Future<T> submit(final Callable<T> task) {
        return vtExecutor.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return vtExecutor.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks,
                                          final long timeout, final TimeUnit unit)
            throws InterruptedException {
        return vtExecutor.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return vtExecutor.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks,
                            final long timeout, final TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return vtExecutor.invokeAny(tasks, timeout, unit);
    }

    // --- Scheduling: sleep in virtual thread, then execute ---

    @Override
    public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
        final long triggerNanos = System.nanoTime() + unit.toNanos(delay);
        final VirtualScheduledFuture<Void> sf = new VirtualScheduledFuture<>(triggerNanos);
        sf.setFuture(vtExecutor.submit(() -> {
            sleepUntil(triggerNanos);
            command.run();
            return null;
        }));
        return sf;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(final Callable<V> callable, final long delay,
                                            final TimeUnit unit) {
        final long triggerNanos = System.nanoTime() + unit.toNanos(delay);
        final VirtualScheduledFuture<V> sf = new VirtualScheduledFuture<>(triggerNanos);
        sf.setFuture(vtExecutor.submit(() -> {
            sleepUntil(triggerNanos);
            return callable.call();
        }));
        return sf;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command, final long initialDelay,
                                                   final long period, final TimeUnit unit) {
        final long periodNanos = unit.toNanos(period);
        final long firstTrigger = System.nanoTime() + unit.toNanos(initialDelay);
        final VirtualScheduledFuture<Void> sf = new VirtualScheduledFuture<>(firstTrigger);
        sf.setFuture(vtExecutor.submit(() -> {
            long nextTrigger = firstTrigger;
            sleepUntil(nextTrigger);
            while (!Thread.currentThread().isInterrupted()) {
                command.run();
                nextTrigger += periodNanos;
                sf.updateTriggerNanos(nextTrigger);
                sleepUntil(nextTrigger);
            }
            return null;
        }));
        return sf;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command, final long initialDelay,
                                                      final long delay, final TimeUnit unit) {
        final long delayNanos = unit.toNanos(delay);
        final long firstTrigger = System.nanoTime() + unit.toNanos(initialDelay);
        final VirtualScheduledFuture<Void> sf = new VirtualScheduledFuture<>(firstTrigger);
        sf.setFuture(vtExecutor.submit(() -> {
            sleepUntil(firstTrigger);
            while (!Thread.currentThread().isInterrupted()) {
                command.run();
                final long nextTrigger = System.nanoTime() + delayNanos;
                sf.updateTriggerNanos(nextTrigger);
                sleepUntil(nextTrigger);
            }
            return null;
        }));
        return sf;
    }

    private static void sleepUntil(final long triggerNanos) throws InterruptedException {
        long remaining = triggerNanos - System.nanoTime();
        while (remaining > 0) {
            TimeUnit.NANOSECONDS.sleep(remaining);
            remaining = triggerNanos - System.nanoTime();
        }
    }

    // --- Lifecycle ---

    @Override
    public void shutdown() {
        vtExecutor.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return vtExecutor.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return vtExecutor.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return vtExecutor.isTerminated();
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        return vtExecutor.awaitTermination(timeout, unit);
    }

    /**
     * A {@link ScheduledFuture} backed by a virtual thread {@link Future}.
     */
    static final class VirtualScheduledFuture<V> implements ScheduledFuture<V> {
        private volatile Future<V> delegate;
        private volatile long triggerNanos;

        VirtualScheduledFuture(final long triggerNanos) {
            this.triggerNanos = triggerNanos;
        }

        void setFuture(final Future<V> delegate) {
            this.delegate = delegate;
        }

        void updateTriggerNanos(final long triggerNanos) {
            this.triggerNanos = triggerNanos;
        }

        @Override
        public long getDelay(final TimeUnit unit) {
            return unit.convert(triggerNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(final Delayed other) {
            if (other == this) {
                return 0;
            }
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }

        @Override
        public boolean cancel(final boolean mayInterruptIfRunning) {
            return delegate.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        @Override
        public boolean isDone() {
            return delegate.isDone();
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            return delegate.get();
        }

        @Override
        public V get(final long timeout, final TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.get(timeout, unit);
        }
    }
}
