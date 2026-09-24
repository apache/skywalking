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
 */

package org.apache.skywalking.oap.server.receiver.otel;

import java.util.List;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtelMetricReceiverProviderTest {
    @Test
    void shouldRefuseSearchableTagsWithoutAScope() {
        final OtelMetricReceiverConfig config = new OtelMetricReceiverConfig();
        config.setOtlpTraceSearchableTags("resource.service.namespace,http.request.method,span.");
        final OtelMetricReceiverProvider provider = new OtelMetricReceiverProvider();
        provider.newConfigCreator().onInitialized(config);

        final ModuleStartException e = assertThrows(ModuleStartException.class, provider::prepare);
        assertTrue(e.getMessage().contains("[http.request.method, span.]"), e.getMessage());
    }

    @Test
    void shouldAcceptScopedSearchableTags() {
        final OtelMetricReceiverConfig config = new OtelMetricReceiverConfig();
        config.setOtlpTraceSearchableTags("resource.service.namespace, span.http.request.method");

        assertEquals(List.of(), config.getUnscopedOtlpTraceSearchableTags());
    }

    @Test
    void shouldNotCheckSearchableTagsInZipkinMode() {
        final OtelMetricReceiverConfig config = new OtelMetricReceiverConfig();
        config.setOtlpTraceStorage(OtelMetricReceiverConfig.OTLP_TRACE_STORAGE_ZIPKIN);
        config.setOtlpTraceSearchableTags("http.request.method");
        final OtelMetricReceiverProvider provider = new OtelMetricReceiverProvider();
        provider.newConfigCreator().onInitialized(config);

        // Zipkin mode never reads the list, so an unscoped entry there is not a reason to fail the boot.
        assertDoesNotThrow(provider::prepare);
    }
}
