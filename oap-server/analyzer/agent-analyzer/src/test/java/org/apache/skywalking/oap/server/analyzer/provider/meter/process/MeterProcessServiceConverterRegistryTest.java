/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.oap.server.analyzer.provider.meter.process;

import org.apache.skywalking.oap.meter.analyzer.v2.MetricConvert;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Contract test for the {@link org.apache.skywalking.oap.meter.analyzer.v2.MalConverterRegistry}
 * half of {@link MeterProcessService} — the surface runtime-rule drives when it hot-updates a
 * {@code meter-analyzer-config} rule. Mirrors the otel receiver's equivalent test so both MAL
 * registries are held to the same contract.
 *
 * <p>Boot-time compilation is covered by {@code MeterProcessorTest}; here we only exercise the
 * mutation surface, which is what an operator's {@code /addOrUpdate} and {@code /inactivate}
 * reach.
 */
class MeterProcessServiceConverterRegistryTest {

    private static final String KEY = "meter-analyzer-config:java-agent";

    @Test
    void addOrReplaceThenRemoveRoundTrips() {
        final MeterProcessService service = newService();
        final MetricConvert first = mock(MetricConvert.class);
        final MetricConvert second = mock(MetricConvert.class);

        service.addOrReplaceConverter(KEY, first);
        assertEquals(1, service.converts().size());
        assertTrue(service.converts().contains(first));

        // Re-binding the same key is the FILTER_ONLY hot path — it must replace in place
        // rather than accumulate a second entry for the same rule file.
        service.addOrReplaceConverter(KEY, second);
        assertEquals(1, service.converts().size());
        assertTrue(service.converts().contains(second));

        service.removeConverter(KEY);
        assertTrue(service.converts().isEmpty());
    }

    @Test
    void removeConverterOnAbsentKeyIsIdempotent() {
        // /delete or /inactivate against a rule this node already tore down must not raise —
        // a missing key means "already converged", not a failure.
        final MeterProcessService service = newService();

        assertDoesNotThrow(() -> service.removeConverter("meter-analyzer-config:nonexistent"));
        assertTrue(service.converts().isEmpty());
    }

    @Test
    void convertsIsEmptyBeforeStart() {
        // MeterProcessor reads converts() on every batch and must tolerate the pre-start
        // window (ingest can arrive before the boot-time rules finish compiling).
        assertTrue(newService().converts().isEmpty());
    }

    private static MeterProcessService newService() {
        return new MeterProcessService(mock(ModuleManager.class));
    }
}
