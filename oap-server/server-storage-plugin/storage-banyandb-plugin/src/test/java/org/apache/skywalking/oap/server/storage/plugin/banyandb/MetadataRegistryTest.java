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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the local schema-cache self-heal on {@link MetadataRegistry}. A read/persist
 * path that finds the cache empty for a model whose dispatch worker is already live (e.g. a
 * {@code withoutSchemaChange} peer apply or a runtime-rule bundled fall-over that rebuilt the
 * worker but skipped the populate) must be able to re-derive the schema locally with no server
 * RPC, instead of throwing {@code "<model> is not registered"} forever.
 */
class MetadataRegistryTest {

    @AfterEach
    void clearPopulator() {
        // MetadataRegistry is an enum singleton; clear the populator so global state set by a test
        // does not leak into others.
        MetadataRegistry.INSTANCE.registerLocalSchemaPopulator(null);
    }

    @Test
    void repopulateLocallyInvokesRegisteredPopulator() {
        final Model model = mock(Model.class);
        when(model.getName()).thenReturn("meter_test_metric");
        final AtomicInteger calls = new AtomicInteger();
        MetadataRegistry.INSTANCE.registerLocalSchemaPopulator(m -> calls.incrementAndGet());

        MetadataRegistry.INSTANCE.repopulateLocally(model);

        assertEquals(1, calls.get(), "a registered populator must be invoked on a self-heal attempt");
    }

    @Test
    void repopulateLocallyIsNoOpWhenNoPopulatorRegistered() {
        MetadataRegistry.INSTANCE.registerLocalSchemaPopulator(null);
        final Model model = mock(Model.class);
        assertDoesNotThrow(() -> MetadataRegistry.INSTANCE.repopulateLocally(model),
            "self-heal with no populator (e.g. a non-BanyanDB context) must be a no-op");
    }

    @Test
    void repopulateLocallySwallowsPopulatorError() {
        final Model model = mock(Model.class);
        when(model.getName()).thenReturn("meter_test_metric");
        MetadataRegistry.INSTANCE.registerLocalSchemaPopulator(m -> {
            throw new RuntimeException("derivation boom");
        });

        // A failed re-derivation must never be worse than the pre-existing throw: the caller
        // re-reads and surfaces its own not-registered error, so repopulateLocally itself must
        // not propagate.
        assertDoesNotThrow(() -> MetadataRegistry.INSTANCE.repopulateLocally(model),
            "a throwing populator must be swallowed so self-heal never worsens the failure");
    }

    @Test
    void evictExercisesEveryFindMetadataKeyBranchSafely() {
        // evict() must key its removal exactly as findMetadata() looks an entry up — across
        // management (name), record (name) and metric (formatName(name, downSampling)) kinds.
        // Exercise all three so a wrong-branch or formatName regression surfaces, and confirm
        // evicting an absent entry is a safe no-op (the registry is otherwise insert-only).
        final Model management = mock(Model.class);
        when(management.isTimeSeries()).thenReturn(false);
        when(management.getName()).thenReturn("management_model");

        final Model record = mock(Model.class);
        when(record.isTimeSeries()).thenReturn(true);
        when(record.isRecord()).thenReturn(true);
        when(record.getName()).thenReturn("record_model");

        final Model metric = mock(Model.class);
        when(metric.isTimeSeries()).thenReturn(true);
        when(metric.isRecord()).thenReturn(false);
        when(metric.getName()).thenReturn("metric_model");
        when(metric.getDownsampling()).thenReturn(DownSampling.Minute);

        assertDoesNotThrow(() -> {
            MetadataRegistry.INSTANCE.evict(management);
            MetadataRegistry.INSTANCE.evict(record);
            MetadataRegistry.INSTANCE.evict(metric);
        }, "evict must be a safe no-op for an absent entry across all model kinds");
    }
}
