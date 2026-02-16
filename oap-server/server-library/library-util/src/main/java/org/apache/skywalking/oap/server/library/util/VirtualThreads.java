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

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Unified executor factory for both virtual threads (JDK 25+) and platform threads.
 *
 * <p>Virtual threads (JEP 444) are available since JDK 21, but JDK 21-23 has a critical
 * thread pinning bug where {@code synchronized} blocks prevent virtual threads from
 * unmounting from carrier threads (see JEP 491). This was fixed in JDK 24, but JDK 24
 * is non-LTS. JDK 25 LTS is the first long-term support release with the fix.
 *
 * <p>This utility requires <b>JDK 25+</b> to enable virtual threads, ensuring both
 * the pinning fix and LTS support are present. All created threads (virtual or platform)
 * are named with the provided prefix for monitoring and debugging.
 */
@Slf4j
public final class VirtualThreads {

    /**
     * The minimum JDK version required for virtual thread support.
     * JDK 25 is the first LTS with the synchronized pinning fix (JEP 491).
     */
    static final int MINIMUM_JDK_VERSION = 25;

    private static final boolean SUPPORTED;

    /*
     * Cached reflection handles for JDK 25+ virtual thread APIs:
     *   Thread.ofVirtual()                           -> Thread.Builder.OfVirtual
     *   Thread.Builder#name(String prefix, long start) -> Thread.Builder
     *   Thread.Builder#factory()                     -> ThreadFactory
     *   Executors.newThreadPerTaskExecutor(ThreadFactory) -> ExecutorService
     */
    private static final Method OF_VIRTUAL;
    private static final Method BUILDER_NAME;
    private static final Method BUILDER_FACTORY;
    private static final Method NEW_THREAD_PER_TASK_EXECUTOR;

    /**
     * System environment variable to disable virtual threads on JDK 25+.
     * Set {@code SW_VIRTUAL_THREADS_ENABLED=false} to force platform threads.
     */
    static final String ENV_VIRTUAL_THREADS_ENABLED = "SW_VIRTUAL_THREADS_ENABLED";

    static {
        final int jdkVersion = Runtime.version().feature();
        boolean supported = false;
        Method ofVirtual = null;
        Method builderName = null;
        Method builderFactory = null;
        Method newThreadPerTaskExecutor = null;

        final String envValue = System.getenv(ENV_VIRTUAL_THREADS_ENABLED);
        final boolean disabledByEnv = "false".equalsIgnoreCase(envValue);

        if (disabledByEnv) {
            log.info("Virtual threads disabled by environment variable {}={}",
                     ENV_VIRTUAL_THREADS_ENABLED, envValue);
        } else if (jdkVersion >= MINIMUM_JDK_VERSION) {
            try {
                ofVirtual = Thread.class.getMethod("ofVirtual");
                final Class<?> builderClass = Class.forName("java.lang.Thread$Builder");
                builderName = builderClass.getMethod("name", String.class, long.class);
                builderFactory = builderClass.getMethod("factory");
                newThreadPerTaskExecutor = Executors.class.getMethod(
                    "newThreadPerTaskExecutor", ThreadFactory.class);
                supported = true;
                log.info("Virtual threads available (JDK {})", jdkVersion);
            } catch (final ReflectiveOperationException e) {
                log.warn("JDK {} meets version requirement but virtual thread API "
                             + "not found, virtual threads disabled", jdkVersion, e);
            }
        } else {
            log.info("Virtual threads require JDK {}+, current JDK is {}",
                     MINIMUM_JDK_VERSION, jdkVersion);
        }

        SUPPORTED = supported;
        OF_VIRTUAL = ofVirtual;
        BUILDER_NAME = builderName;
        BUILDER_FACTORY = builderFactory;
        NEW_THREAD_PER_TASK_EXECUTOR = newThreadPerTaskExecutor;
    }

    private VirtualThreads() {
    }

    /**
     * @return true if the current JDK version is 25+ and virtual thread API is available.
     */
    public static boolean isSupported() {
        return SUPPORTED;
    }

    /**
     * Create a named executor service with virtual threads enabled by default.
     * On JDK 25+, creates a virtual-thread-per-task executor with threads named
     * {@code {namePrefix}-0}, {@code {namePrefix}-1}, etc.
     * On older JDKs, delegates to the provided {@code platformExecutorSupplier}.
     *
     * @param namePrefix               prefix for virtual thread names
     * @param platformExecutorSupplier  supplies the platform-thread executor as fallback
     * @return virtual thread executor on JDK 25+, or the supplier's executor otherwise
     */
    public static ExecutorService createExecutor(final String namePrefix,
                                                 final Supplier<ExecutorService> platformExecutorSupplier) {
        return createExecutor(namePrefix, true, platformExecutorSupplier);
    }

    /**
     * Create a named executor service. When {@code enableVirtualThreads} is true and JDK 25+,
     * creates a virtual-thread-per-task executor with threads named
     * {@code {namePrefix}-0}, {@code {namePrefix}-1}, etc.
     * Otherwise, delegates to the provided {@code platformExecutorSupplier}.
     *
     * @param namePrefix               prefix for virtual thread names
     * @param enableVirtualThreads     whether to use virtual threads (requires JDK 25+)
     * @param platformExecutorSupplier supplies the platform-thread executor as fallback
     * @return virtual thread executor or the supplier's executor
     */
    public static ExecutorService createExecutor(final String namePrefix,
                                                 final boolean enableVirtualThreads,
                                                 final Supplier<ExecutorService> platformExecutorSupplier) {
        if (enableVirtualThreads && SUPPORTED) {
            try {
                return createVirtualThreadExecutor(namePrefix);
            } catch (final ReflectiveOperationException e) {
                log.warn("Failed to create virtual thread executor [{}], "
                             + "falling back to platform threads", namePrefix, e);
            }
        }
        return platformExecutorSupplier.get();
    }

    /**
     * Create a named scheduled executor service with virtual threads enabled by default.
     * On JDK 25+, creates a virtual-thread-backed {@link ScheduledExecutorService}.
     * On older JDKs, delegates to the provided {@code platformExecutorSupplier}.
     *
     * <p>This is designed for frameworks (e.g. Armeria) that require a
     * {@link ScheduledExecutorService} for their blocking task executor.
     * All methods — including scheduling — are fully backed by virtual threads.
     * Scheduling is implemented by sleeping in a virtual thread.
     *
     * @param namePrefix               prefix for virtual thread names
     * @param platformExecutorSupplier  supplies the platform-thread executor as fallback
     * @return virtual thread scheduled executor on JDK 25+, or the supplier's executor otherwise
     */
    public static ScheduledExecutorService createScheduledExecutor(
            final String namePrefix,
            final Supplier<ScheduledExecutorService> platformExecutorSupplier) {
        if (SUPPORTED) {
            try {
                final ExecutorService vtExecutor = createVirtualThreadExecutor(namePrefix);
                return new VirtualThreadScheduledExecutor(vtExecutor);
            } catch (final ReflectiveOperationException e) {
                log.warn("Failed to create virtual thread scheduled executor [{}], "
                             + "falling back to platform threads", namePrefix, e);
            }
        }
        return platformExecutorSupplier.get();
    }

    private static ExecutorService createVirtualThreadExecutor(
            final String namePrefix) throws ReflectiveOperationException {
        // Thread.ofVirtual().name("vt:" + namePrefix + "-", 0).factory()
        final Object builder = OF_VIRTUAL.invoke(null);
        final Object namedBuilder = BUILDER_NAME.invoke(builder, "vt:" + namePrefix + "-", 0L);
        final ThreadFactory factory = (ThreadFactory) BUILDER_FACTORY.invoke(namedBuilder);
        // Executors.newThreadPerTaskExecutor(factory)
        final ExecutorService executor =
            (ExecutorService) NEW_THREAD_PER_TASK_EXECUTOR.invoke(null, factory);
        log.info("Created virtual-thread-per-task executor [{}]", namePrefix);
        return executor;
    }
}
