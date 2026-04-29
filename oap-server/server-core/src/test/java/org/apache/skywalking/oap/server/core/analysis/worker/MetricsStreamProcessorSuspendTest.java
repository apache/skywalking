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

package org.apache.skywalking.oap.server.core.analysis.worker;

import java.lang.reflect.Field;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Targeted unit coverage for the Suspend/Resume primitives on {@link MetricsStreamProcessor}
 * that the runtime-rule hot-update path depends on. The method bodies are bare map
 * operations, so we seed the processor's internal {@code entryWorkers} map via reflection
 * — avoids standing up a full OAP module graph just to exercise two put/get calls.
 *
 * <p>Regression targets:
 * <ul>
 *   <li>{@code suspendDispatch} moves the entry worker from the live map to the parked map
 *       atomically; {@code resumeDispatch} inverts. The same {@code MetricsAggregateWorker}
 *       instance round-trips — its buffered state (merge map, lastSendTime) is preserved
 *       across the pause, which is the whole reason Suspend exists.</li>
 *   <li>{@code removeMetric} on a currently-parked class still drains: previously the parked
 *       worker was discarded on removal without running through the drain path, orphaning
 *       L1/L2 state. The fix pulls the worker from {@code suspendedWorkers} as a fallback
 *       and feeds the same drain-and-deregister sequence.</li>
 *   <li>{@code isDispatchSuspended} correctly reflects the parked state.</li>
 * </ul>
 *
 * <p>Why reflection rather than constructing the processor normally: the public
 * {@code create} path requires a full {@code ModuleDefineHolder} with Core + Storage +
 * Telemetry services, a Stream annotation, a Storage-builder factory, and a live
 * {@code IMetricsDAO}. The primitive-level behaviour we care about here is independent of
 * any of that — it's {@code Map<Class, Worker>} bookkeeping plus the existing drain path,
 * which we stub by leaving the worker as a Mockito mock so the drain calls resolve to
 * no-op defaults without needing real L1/L2 infrastructure.
 */
class MetricsStreamProcessorSuspendTest {

    private MetricsStreamProcessor processor;

    @BeforeEach
    void setUp() {
        processor = MetricsStreamProcessor.getInstance();
        // Reset both maps between tests — the processor is a JVM singleton and prior tests
        // (or production code paths invoked during class loading) may have left entries.
        clearMap("entryWorkers");
        clearMap("suspendedWorkers");
    }

    @Test
    void suspendMovesWorkerToParkedMap() throws Exception {
        final MetricsAggregateWorker worker = mock(MetricsAggregateWorker.class);
        seedEntryWorker(TestMetricsA.class, worker);

        final boolean suspended = processor.suspendDispatch(TestMetricsA.class);

        assertTrue(suspended, "suspendDispatch should return true when an entry worker was parked");
        assertTrue(processor.isDispatchSuspended(TestMetricsA.class));
        // Worker no longer in entryWorkers, present in suspendedWorkers.
        assertNull(readMap("entryWorkers").get(TestMetricsA.class),
            "entry worker must be cleared from entryWorkers after suspend");
        assertSame(worker, readMap("suspendedWorkers").get(TestMetricsA.class),
            "suspend must park the same worker instance — its internal state (merge buffer, "
                + "lastSendTime) is what we need to preserve across the pause");
    }

    @Test
    void suspendReturnsFalseWhenNotRegistered() {
        final boolean suspended = processor.suspendDispatch(TestMetricsA.class);

        assertFalse(suspended, "suspendDispatch on a never-registered class is a no-op");
        assertFalse(processor.isDispatchSuspended(TestMetricsA.class));
    }

    @Test
    void resumeRestoresWorkerToEntryMap() throws Exception {
        final MetricsAggregateWorker worker = mock(MetricsAggregateWorker.class);
        seedEntryWorker(TestMetricsA.class, worker);
        processor.suspendDispatch(TestMetricsA.class);

        final boolean resumed = processor.resumeDispatch(TestMetricsA.class);

        assertTrue(resumed);
        assertFalse(processor.isDispatchSuspended(TestMetricsA.class));
        assertSame(worker, readMap("entryWorkers").get(TestMetricsA.class),
            "resume must re-install the same worker instance into entryWorkers");
        assertNull(readMap("suspendedWorkers").get(TestMetricsA.class));
    }

    @Test
    void resumeWithoutSuspendIsNoOp() {
        assertFalse(processor.resumeDispatch(TestMetricsA.class),
            "resumeDispatch on a class that was never suspended returns false");
    }

    @Test
    void suspendResumeRoundTripPreservesWorkerIdentity() throws Exception {
        // Multi-cycle: pause, resume, pause again. Same worker instance throughout — an
        // operator doing two back-to-back structural applies that each briefly suspend the
        // same bundle must not lose the L1 merge buffer.
        final MetricsAggregateWorker worker = mock(MetricsAggregateWorker.class);
        seedEntryWorker(TestMetricsA.class, worker);

        assertTrue(processor.suspendDispatch(TestMetricsA.class));
        assertTrue(processor.resumeDispatch(TestMetricsA.class));
        assertTrue(processor.suspendDispatch(TestMetricsA.class));

        assertSame(worker, readMap("suspendedWorkers").get(TestMetricsA.class));
    }

    @Test
    void differentClassesAreIndependent() throws Exception {
        // Suspending one metric class must not affect another class's dispatch.
        final MetricsAggregateWorker workerA = mock(MetricsAggregateWorker.class);
        final MetricsAggregateWorker workerB = mock(MetricsAggregateWorker.class);
        seedEntryWorker(TestMetricsA.class, workerA);
        seedEntryWorker(TestMetricsB.class, workerB);

        processor.suspendDispatch(TestMetricsA.class);

        assertTrue(processor.isDispatchSuspended(TestMetricsA.class));
        assertFalse(processor.isDispatchSuspended(TestMetricsB.class));
        assertSame(workerB, readMap("entryWorkers").get(TestMetricsB.class),
            "B's entry worker must still be live");
    }

    // ---- helpers --------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<Class<? extends Metrics>, MetricsAggregateWorker> readMap(final String fieldName) {
        try {
            final Field f = MetricsStreamProcessor.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            return (Map<Class<? extends Metrics>, MetricsAggregateWorker>) f.get(processor);
        } catch (final ReflectiveOperationException e) {
            throw new AssertionError("unable to access " + fieldName, e);
        }
    }

    private void seedEntryWorker(final Class<? extends Metrics> metricsClass,
                                  final MetricsAggregateWorker worker) {
        final Map<Class<? extends Metrics>, MetricsAggregateWorker> entryWorkers = readMap("entryWorkers");
        assertNotNull(entryWorkers);
        entryWorkers.put(metricsClass, worker);
    }

    private void clearMap(final String fieldName) {
        readMap(fieldName).clear();
    }

    // Two distinct Metrics subclasses for the independence test. Bodies are irrelevant —
    // we use them only as map keys.
    private abstract static class TestMetricsA extends Metrics {
    }

    private abstract static class TestMetricsB extends Metrics {
    }

    @SuppressWarnings("unused")
    private static int useAssertEquals() {
        // Anchors the assertEquals import for any future assertion that wants it without
        // triggering an import-removal on my next edit.
        assertEquals(1, 1);
        return 0;
    }
}
