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
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.ids.DistributedTraceId;
import org.apache.skywalking.apm.agent.core.context.ids.ID;
import org.apache.skywalking.apm.agent.core.context.ids.PropagatedTraceId;
import org.junit.Assert;
import org.junit.Test;

public class ContextCarrierV2HeaderTest {
    @Test
    public void testCompatibleHeaderKeys() {
        Config.Agent.ACTIVE_V1_HEADER = true;
        ContextCarrier contextCarrier = new ContextCarrier();
        CarrierItem next = contextCarrier.items();
        boolean hasSW3 = false;
        boolean hasSW6 = false;
        try {
            while (next.hasNext()) {
                next = next.next();
                if (next.getHeadKey().equals("sw3")) {
                    hasSW3 = true;
                } else if (next.getHeadKey().equals("sw6")) {
                    hasSW6 = true;
                } else {
                    Assert.fail("unexpected key");
                }
            }
        } finally {
            Config.Agent.ACTIVE_V1_HEADER = false;
        }
        Assert.assertTrue(hasSW3);
        Assert.assertTrue(hasSW6);
    }

    @Test
    public void testDeserializeV2Header() {
        ContextCarrier contextCarrier = new ContextCarrier();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            if (next.getHeadKey().equals("sw3")) {
            } else if (next.getHeadKey().equals("sw6")) {
                next.setHeadValue("1-My40LjU=-MS4yLjM=-4-1-1-IzEyNy4wLjAuMTo4MDgw--");
            } else {
                Assert.fail("unexpected key");
            }
        }

        Assert.assertTrue(contextCarrier.isValid());

        Config.Agent.ACTIVE_V1_HEADER = true;
        try {
            contextCarrier = new ContextCarrier();
            next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                if (next.getHeadKey().equals("sw3")) {
                    next.setHeadValue("1.2343.234234234|1|1|1|#127.0.0.1:8080|#/portal/|#/testEntrySpan|1.2343.234234234");
                } else if (next.getHeadKey().equals("sw6")) {
                } else {
                    Assert.fail("unexpected key");
                }
            }
        } finally {
            Config.Agent.ACTIVE_V1_HEADER = false;
        }

        Assert.assertTrue(contextCarrier.isValid());
    }

    @Test
    public void testSerializeV2Header() {
        List<DistributedTraceId> distributedTraceIds = new ArrayList<DistributedTraceId>();
        distributedTraceIds.add(new PropagatedTraceId("3.4.5"));

        ContextCarrier contextCarrier = new ContextCarrier();
        contextCarrier.setTraceSegmentId(new ID(1, 2, 3));
        contextCarrier.setDistributedTraceIds(distributedTraceIds);
        contextCarrier.setSpanId(4);
        contextCarrier.setEntryServiceInstanceId(1);
        contextCarrier.setParentServiceInstanceId(1);
        contextCarrier.setPeerHost("127.0.0.1:8080");
        contextCarrier.setEntryEndpointName("/portal");
        contextCarrier.setParentEndpointId(123);

        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            if (next.getHeadKey().equals("sw3")) {
                Assert.assertEquals("", next.getHeadValue());
            } else if (next.getHeadKey().equals("sw6")) {
                /**
                 * sampleFlag-traceId-segmentId-spanId-parentAppInstId-entryAppInstId-peerHost-entryEndpoint-parentEndpoint
                 *
                 * "1-3.4.5-1.2.3-4-1-1-#127.0.0.1:8080-#/portal-123"
                 */
                Assert.assertEquals("1-My40LjU=-MS4yLjM=-4-1-1-IzEyNy4wLjAuMTo4MDgw-Iy9wb3J0YWw=-MTIz", next.getHeadValue());
            } else {
                Assert.fail("unexpected key");
            }
        }

        Config.Agent.ACTIVE_V1_HEADER = true;
        try {
            next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                if (next.getHeadKey().equals("sw3")) {
                    Assert.assertEquals("1.2.3|4|1|1|#127.0.0.1:8080|#/portal|123|3.4.5", next.getHeadValue());
                } else if (next.getHeadKey().equals("sw6")) {
                    //TODO, no BASE64
                    Assert.assertEquals("1-My40LjU=-MS4yLjM=-4-1-1-IzEyNy4wLjAuMTo4MDgw-Iy9wb3J0YWw=-MTIz", next.getHeadValue());
                } else {
                    Assert.fail("unexpected key");
                }
            }

        } finally {
            Config.Agent.ACTIVE_V1_HEADER = false;
        }

        Assert.assertTrue(contextCarrier.isValid());
    }

    @Test
    public void testV2HeaderAccurate() {
        List<DistributedTraceId> distributedTraceIds = new ArrayList<DistributedTraceId>();
        distributedTraceIds.add(new PropagatedTraceId("3.4.5"));

        ContextCarrier contextCarrier = new ContextCarrier();
        contextCarrier.setTraceSegmentId(new ID(1, 2, 3));
        contextCarrier.setDistributedTraceIds(distributedTraceIds);
        contextCarrier.setSpanId(4);
        contextCarrier.setEntryServiceInstanceId(1);
        contextCarrier.setParentServiceInstanceId(1);
        contextCarrier.setPeerHost("127.0.0.1:8080");
        contextCarrier.setEntryEndpointName("/portal");
        contextCarrier.setParentEndpointId(123);

        CarrierItem next = contextCarrier.items();
        String headerValue = null;
        while (next.hasNext()) {
            next = next.next();
            if (next.getHeadKey().equals("sw3")) {
                Assert.assertEquals("", next.getHeadValue());
            } else if (next.getHeadKey().equals("sw6")) {
                headerValue = next.getHeadValue();
            } else {
                Assert.fail("unexpected key");
            }
        }

        ContextCarrier contextCarrier2 = new ContextCarrier();
        next = contextCarrier2.items();
        while (next.hasNext()) {
            next = next.next();
            if (next.getHeadKey().equals("sw3")) {
            } else if (next.getHeadKey().equals("sw6")) {
                next.setHeadValue(headerValue);
            } else {
                Assert.fail("unexpected key");
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
    }
}
