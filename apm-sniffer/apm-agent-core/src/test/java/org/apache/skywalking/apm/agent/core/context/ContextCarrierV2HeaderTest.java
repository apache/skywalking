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

package org.apache.skywalking.apm.agent.core.context;

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.ids.DistributedTraceId;
import org.apache.skywalking.apm.agent.core.context.ids.ID;
import org.apache.skywalking.apm.agent.core.context.ids.PropagatedTraceId;
import org.junit.Assert;
import org.junit.Test;

public class ContextCarrierV2HeaderTest {

    @Test
    public void testDeserializeV2Header() {
        ContextCarrier contextCarrier = new ContextCarrier();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            if (next.getHeadKey().equals(SW6CarrierItem.HEADER_NAME)) {
                next.setHeadValue("1-My40LjU=-MS4yLjM=-4-1-1-IzEyNy4wLjAuMTo4MDgw--");
            } else if (next.getHeadKey().equals(SW8CorrelationCarrierItem.HEADER_NAME)) {
                next.setHeadValue("dGVzdA==:dHJ1ZQ==");
            } else {
                throw new IllegalArgumentException("Unknown Header: " + next.getHeadKey());
            }
        }

        Assert.assertTrue(contextCarrier.isValid());
    }

    @Test
    public void testSerializeV2Header() {
        List<DistributedTraceId> distributedTraceIds = new ArrayList<>();
        distributedTraceIds.add(new PropagatedTraceId("3.4.5"));

        ContextCarrier contextCarrier = new ContextCarrier();
        contextCarrier.setTraceSegmentId(new ID(1, 2, 3));
        contextCarrier.setDistributedTraceIds(distributedTraceIds);
        contextCarrier.setSpanId(4);
        contextCarrier.setEntryServiceInstanceId(1);
        contextCarrier.setParentServiceInstanceId(1);
        contextCarrier.setPeerHost("127.0.0.1:8080");
        contextCarrier.setEntryEndpointName("/portal");
        contextCarrier.setParentEndpointName("/app");

        contextCarrier.getCorrelationContext().put("test", "true");

        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            /*
             * sampleFlag-traceId-segmentId-spanId-parentAppInstId-entryAppInstId-peerHost-entryEndpoint-parentEndpoint
             *
             * "1-3.4.5-1.2.3-4-1-1-#127.0.0.1:8080-#/portal-123"
             */
            if (next.getHeadKey().equals(SW6CarrierItem.HEADER_NAME)) {
                Assert.assertEquals("1-My40LjU=-MS4yLjM=-4-1-1-IzEyNy4wLjAuMTo4MDgw-Iy9wb3J0YWw=-Iy9hcHA=", next.getHeadValue());
            } else if (next.getHeadKey().equals(SW8CorrelationCarrierItem.HEADER_NAME)) {
                /**
                 * customKey:customValue
                 *
                 * "test:true"
                 */
                Assert.assertEquals("dGVzdA==:dHJ1ZQ==", next.getHeadValue());
            } else {
                throw new IllegalArgumentException("Unknown Header: " + next.getHeadKey());
            }
        }

        next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            if (next.getHeadKey().equals(SW6CarrierItem.HEADER_NAME)) {
                Assert.assertEquals("1-My40LjU=-MS4yLjM=-4-1-1-IzEyNy4wLjAuMTo4MDgw-Iy9wb3J0YWw=-Iy9hcHA=", next.getHeadValue());
            } else if (next.getHeadKey().equals(SW8CorrelationCarrierItem.HEADER_NAME)) {
                Assert.assertEquals("dGVzdA==:dHJ1ZQ==", next.getHeadValue());
            } else {
                throw new IllegalArgumentException("Unknown Header: " + next.getHeadKey());
            }
        }

        Assert.assertTrue(contextCarrier.isValid());
    }

    @Test
    public void testV2HeaderAccurate() {
        List<DistributedTraceId> distributedTraceIds = new ArrayList<>();
        distributedTraceIds.add(new PropagatedTraceId("3.4.5"));

        ContextCarrier contextCarrier = new ContextCarrier();
        contextCarrier.setTraceSegmentId(new ID(1, 2, 3));
        contextCarrier.setDistributedTraceIds(distributedTraceIds);
        contextCarrier.setSpanId(4);
        contextCarrier.setEntryServiceInstanceId(1);
        contextCarrier.setParentServiceInstanceId(1);
        contextCarrier.setPeerHost("127.0.0.1:8080");
        contextCarrier.setEntryEndpointName("/portal");
        contextCarrier.setParentEndpointName("/app");

        contextCarrier.getCorrelationContext().put("test", "true");

        CarrierItem next = contextCarrier.items();
        String sw6HeaderValue = null;
        String correlationHeaderValue = null;
        while (next.hasNext()) {
            next = next.next();
            if (next.getHeadKey().equals(SW6CarrierItem.HEADER_NAME)) {
                sw6HeaderValue = next.getHeadValue();
            } else if (next.getHeadKey().equals(SW8CorrelationCarrierItem.HEADER_NAME)) {
                correlationHeaderValue = next.getHeadValue();
            } else {
                throw new IllegalArgumentException("Unknown Header: " + next.getHeadKey());
            }
        }

        ContextCarrier contextCarrier2 = new ContextCarrier();
        next = contextCarrier2.items();
        while (next.hasNext()) {
            next = next.next();
            if (next.getHeadKey().equals(SW6CarrierItem.HEADER_NAME)) {
                next.setHeadValue(sw6HeaderValue);
            } else if (next.getHeadKey().equals(SW8CorrelationCarrierItem.HEADER_NAME)) {
                next.setHeadValue(correlationHeaderValue);
            } else {
                throw new IllegalArgumentException("Unknown Header: " + next.getHeadKey());
            }
        }

        Assert.assertTrue(contextCarrier2.isValid());
        Assert.assertEquals(contextCarrier.getSpanId(), contextCarrier2.getSpanId());
        Assert.assertEquals(contextCarrier.getPeerHost(), contextCarrier2.getPeerHost());
        Assert.assertEquals(contextCarrier.getDistributedTraceId(), contextCarrier2.getDistributedTraceId());
        Assert.assertEquals(contextCarrier.getTraceSegmentId(), contextCarrier2.getTraceSegmentId());
        Assert.assertEquals(contextCarrier.getEntryEndpointName(), contextCarrier2.getEntryEndpointName());
        Assert.assertEquals(contextCarrier.getEntryServiceInstanceId(), contextCarrier2.getEntryServiceInstanceId());
        Assert.assertEquals(contextCarrier.getParentServiceInstanceId(), contextCarrier2.getParentServiceInstanceId());
        Assert.assertEquals(contextCarrier.getCorrelationContext(), contextCarrier2.getCorrelationContext());
    }
}
