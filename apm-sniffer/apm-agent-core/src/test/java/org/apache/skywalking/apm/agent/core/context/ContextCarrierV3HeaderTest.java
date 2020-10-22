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
import org.apache.skywalking.apm.agent.core.context.ids.PropagatedTraceId;
import org.junit.Assert;
import org.junit.Test;

public class ContextCarrierV3HeaderTest {

    @Test
    public void testDeserializeV3Header() {
        ContextCarrier contextCarrier = new ContextCarrier();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            if (next.getHeadKey().equals(SW8CarrierItem.HEADER_NAME)) {
                next.setHeadValue("1-My40LjU=-MS4yLjM=-4-c2VydmljZQ==-aW5zdGFuY2U=-L2FwcA==-MTI3LjAuMC4xOjgwODA=");
            } else if (next.getHeadKey().equals(SW8CorrelationCarrierItem.HEADER_NAME)) {
                next.setHeadValue("dGVzdA==:dHJ1ZQ==");
            } else if (next.getHeadKey().equals(SW8ExtensionCarrierItem.HEADER_NAME)) {
                next.setHeadValue("1- ");
            } else {
                throw new IllegalArgumentException("Unknown Header: " + next.getHeadKey());
            }
        }

        Assert.assertTrue(contextCarrier.isValid());
    }

    @Test
    public void testSerializeV3Header() {
        List<DistributedTraceId> distributedTraceIds = new ArrayList<>();

        ContextCarrier contextCarrier = new ContextCarrier();
        contextCarrier.setTraceSegmentId("1.2.3");
        contextCarrier.setTraceId("3.4.5");
        contextCarrier.setSpanId(4);
        contextCarrier.setParentService("service");
        contextCarrier.setParentServiceInstance("instance");
        contextCarrier.setAddressUsedAtClient("127.0.0.1:8080");
        contextCarrier.setParentEndpoint("/portal");
        contextCarrier.setParentEndpoint("/app");

        contextCarrier.getCorrelationContext().put("test", "true");

        contextCarrier.getExtensionContext().deserialize("1- ");

        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            if (next.getHeadKey().equals(SW8CarrierItem.HEADER_NAME)) {
                Assert.assertEquals("1-My40LjU=-MS4yLjM=-4-c2VydmljZQ==-aW5zdGFuY2U=-L2FwcA==-MTI3LjAuMC4xOjgwODA=", next.getHeadValue());
            } else if (next.getHeadKey().equals(SW8CorrelationCarrierItem.HEADER_NAME)) {
                /**
                 * customKey:customValue
                 *
                 * "test:true"
                 */
                Assert.assertEquals("dGVzdA==:dHJ1ZQ==", next.getHeadValue());
            } else if (next.getHeadKey().equals(SW8ExtensionCarrierItem.HEADER_NAME)) {
                Assert.assertEquals("1- ", next.getHeadValue());
            } else {
                throw new IllegalArgumentException("Unknown Header: " + next.getHeadKey());
            }
        }

        next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            if (next.getHeadKey().equals(SW8CarrierItem.HEADER_NAME)) {
                Assert.assertEquals("1-My40LjU=-MS4yLjM=-4-c2VydmljZQ==-aW5zdGFuY2U=-L2FwcA==-MTI3LjAuMC4xOjgwODA=", next.getHeadValue());
            } else if (next.getHeadKey().equals(SW8CorrelationCarrierItem.HEADER_NAME)) {
                Assert.assertEquals("dGVzdA==:dHJ1ZQ==", next.getHeadValue());
            } else if (next.getHeadKey().equals(SW8ExtensionCarrierItem.HEADER_NAME)) {
                Assert.assertEquals("1- ", next.getHeadValue());
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
        contextCarrier.setTraceSegmentId("1.2.3");
        contextCarrier.setTraceId("3.4.5");
        contextCarrier.setSpanId(4);
        contextCarrier.setParentService("service");
        contextCarrier.setParentServiceInstance("instance");
        contextCarrier.setAddressUsedAtClient("127.0.0.1:8080");
        contextCarrier.setParentEndpoint("/portal");
        contextCarrier.setParentEndpoint("/app");

        contextCarrier.getCorrelationContext().put("test", "true");
        contextCarrier.getExtensionContext().deserialize("1- ");

        CarrierItem next = contextCarrier.items();
        String sw6HeaderValue = null;
        String correlationHeaderValue = null;
        String extensionHeaderValue = null;
        while (next.hasNext()) {
            next = next.next();
            if (next.getHeadKey().equals(SW8CarrierItem.HEADER_NAME)) {
                sw6HeaderValue = next.getHeadValue();
            } else if (next.getHeadKey().equals(SW8CorrelationCarrierItem.HEADER_NAME)) {
                correlationHeaderValue = next.getHeadValue();
            } else if (next.getHeadKey().equals(SW8ExtensionCarrierItem.HEADER_NAME)) {
                extensionHeaderValue = next.getHeadValue();
            } else {
                throw new IllegalArgumentException("Unknown Header: " + next.getHeadKey());
            }
        }

        ContextCarrier contextCarrier2 = new ContextCarrier();
        next = contextCarrier2.items();
        while (next.hasNext()) {
            next = next.next();
            if (next.getHeadKey().equals(SW8CarrierItem.HEADER_NAME)) {
                next.setHeadValue(sw6HeaderValue);
            } else if (next.getHeadKey().equals(SW8CorrelationCarrierItem.HEADER_NAME)) {
                next.setHeadValue(correlationHeaderValue);
            } else if (next.getHeadKey().equals(SW8ExtensionCarrierItem.HEADER_NAME)) {
                next.setHeadValue(extensionHeaderValue);
            } else {
                throw new IllegalArgumentException("Unknown Header: " + next.getHeadKey());
            }
        }

        Assert.assertTrue(contextCarrier2.isValid());
        Assert.assertEquals(contextCarrier.getSpanId(), contextCarrier2.getSpanId());
        Assert.assertEquals(contextCarrier.getAddressUsedAtClient(), contextCarrier2.getAddressUsedAtClient());
        Assert.assertEquals(contextCarrier.getTraceId(), contextCarrier2.getTraceId());
        Assert.assertEquals(contextCarrier.getTraceSegmentId(), contextCarrier2.getTraceSegmentId());
        Assert.assertEquals(contextCarrier.getParentService(), contextCarrier2.getParentService());
        Assert.assertEquals(contextCarrier.getParentServiceInstance(), contextCarrier2.getParentServiceInstance());
        Assert.assertEquals(contextCarrier.getParentEndpoint(), contextCarrier2.getParentEndpoint());
        Assert.assertEquals(contextCarrier.getCorrelationContext(), contextCarrier2.getCorrelationContext());
        Assert.assertEquals(contextCarrier.getExtensionContext(), contextCarrier2.getExtensionContext());
    }
}
