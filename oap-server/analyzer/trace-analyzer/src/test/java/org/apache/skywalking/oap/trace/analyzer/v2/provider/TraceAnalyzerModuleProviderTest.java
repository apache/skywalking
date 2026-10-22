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

package org.apache.skywalking.oap.trace.analyzer.v2.provider;

import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.query.MetadataQueryService;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.remote.client.Address;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClient;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.KnownServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceAnalyzerModuleProviderTest {
    @Test
    void onlyANodeAloneKeepsItsPairs() {
        assertTrue(TraceAnalyzerModuleProvider.pairsLocally(List.of()), "no cluster metadata yet");
        assertTrue(TraceAnalyzerModuleProvider.pairsLocally(List.of(client(true))), "a single node");
        assertFalse(TraceAnalyzerModuleProvider.pairsLocally(List.of(client(false))),
            "a Receiver with one Aggregator sends its pairs there, where every receiver's meet");
        assertFalse(TraceAnalyzerModuleProvider.pairsLocally(List.of(client(true), client(false))));
    }

    private static RemoteClient client(final boolean self) {
        final RemoteClient client = mock(RemoteClient.class);
        when(client.getAddress()).thenReturn(new Address("10.0.0.1", 11800, self));
        return client;
    }

    @Test
    void aRestartedNodeKnowsTheStoredServicesBeforeTheyEmitAgain() throws Exception {
        // Neither flagd has emitted since the restart; the stored services still name them.
        final MetadataQueryService metadata = mock(MetadataQueryService.class);
        when(metadata.listServices("OTLP_SERVICE", null)).thenReturn(List.of(
            service("shop::flagd", true), service("outlet::flagd", true), service("cart", true),
            service("10.0.0.7:8080", false)));
        final KnownServices known = new KnownServices();

        assertEquals(3, TraceAnalyzerModuleProvider.recordStoredServices(metadata, known));

        assertTrue(known.contains("shop::flagd"));
        assertTrue(known.contains("outlet::flagd"));
        assertTrue(known.contains("cart"));
        assertFalse(known.contains("10.0.0.7:8080"), "a conjectured, address-named node is no service");
        assertTrue(known.hasGroupedServices());
    }

    private static Service service(final String name, final boolean normal) {
        final Service service = new Service();
        service.setId(IDManager.ServiceID.buildId(name, normal));
        service.setName(name);
        return service;
    }
}
