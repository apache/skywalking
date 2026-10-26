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

package org.apache.skywalking.oap.server.core.otlp;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.hierarchy.instance.InstanceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.hierarchy.service.ServiceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class OTLPServiceTrafficTest {
    @Test
    public void theLayerColumnIsNotOneAnotherMetricHoldsAsALayerValue() {
        // Elasticsearch merges the metrics into one index, and refuses to boot when two of them declare one column
        // with different types: these hold a Layer, this a string.
        assertNotEquals(ServiceTraffic.LAYER, OTLPServiceTraffic.LAYER);
        assertNotEquals(ServiceHierarchyRelationTraffic.SERVICE_LAYER, OTLPServiceTraffic.LAYER);
        assertNotEquals(InstanceHierarchyRelationTraffic.SERVICE_LAYER, OTLPServiceTraffic.LAYER);
    }

    @Test
    public void aServiceHasARowPerLayer() {
        final OTLPServiceTraffic unlabelled = traffic("cart", "UNDEFINED");
        final OTLPServiceTraffic general = traffic("cart", "OTLP_GENERAL");

        // Insert-only rows: one keyed by the name alone would keep the first layer it saw for good.
        assertNotEquals(unlabelled, general);
        assertNotEquals(unlabelled.id().build(), general.id().build());
    }

    @Test
    public void theLayerTravelsToTheNodeThatOwnsTheRow() {
        final OTLPServiceTraffic sent = traffic("otel-demo::cart", "OTLP_GENERAL");
        sent.setTimeBucket(202610230850L);

        final OTLPServiceTraffic received = new OTLPServiceTraffic();
        received.deserialize(sent.serialize().build());

        assertEquals("otel-demo::cart", received.getServiceName());
        assertEquals("OTLP_GENERAL", received.getLayer());
        assertEquals(sent, received);
    }

    @Test
    public void aMissingLayerReadsAsUndefinedWhateverTheStorageHandsBack() {
        // Elasticsearch hands a missing value back as null, BanyanDB as "".
        for (final String missing : new String[] {null, ""}) {
            final Map<String, Object> row = new HashMap<>();
            row.put(OTLPServiceTraffic.SERVICE_NAME, "cart");
            row.put(OTLPServiceTraffic.LAYER, missing);
            row.put(OTLPServiceTraffic.TIME_BUCKET, 202610230850L);

            final OTLPServiceTraffic traffic = new OTLPServiceTraffic.Builder().storage2Entity(
                new HashMapConverter.ToEntity(row));

            assertEquals("UNDEFINED", traffic.getLayer(), String.valueOf(missing));
        }
    }

    private static OTLPServiceTraffic traffic(final String serviceName, final String layer) {
        final OTLPServiceTraffic traffic = new OTLPServiceTraffic();
        traffic.setServiceName(serviceName);
        traffic.setLayer(layer);
        return traffic;
    }
}
