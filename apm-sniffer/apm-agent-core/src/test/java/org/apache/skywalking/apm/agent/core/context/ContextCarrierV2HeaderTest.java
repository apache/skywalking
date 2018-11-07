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

    /**
     * sampleFlag-segmentId-parentAppInstId-entryAppInstId-peerHost-traceId-entryEndpoint-parentEndpoint
     */
    @Test
    public void testDeserializeV2Header() {
        ContextCarrier contextCarrier = new ContextCarrier();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            if (next.getHeadKey().equals("sw3")) {
            } else if (next.getHeadKey().equals("sw6")) {
                //TODO, wait for base64 solution
                next.setHeadValue("1-3.4.5-1.2.3-2-10-11-#127.0.0.1:8080--");
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
        contextCarrier.setEntryApplicationInstanceId(1);
        contextCarrier.setParentApplicationInstanceId(1);
        contextCarrier.setPeerHost("127.0.0.1:8080");
        contextCarrier.setEntryOperationName("/portal");
        contextCarrier.setParentOperationId(123);

        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            if (next.getHeadKey().equals("sw3")) {
                Assert.assertEquals("", next.getHeadValue());
            } else if (next.getHeadKey().equals("sw6")) {
                //TODO, no BASE64
                Assert.assertEquals("1-1.2.3-4-1-1-#127.0.0.1:8080-3.4.5-#/portal-123", next.getHeadValue());
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
                    Assert.assertEquals("1-1.2.3-4-1-1-#127.0.0.1:8080-3.4.5-#/portal-123", next.getHeadValue());
                } else {
                    Assert.fail("unexpected key");
                }
            }

        } finally {
            Config.Agent.ACTIVE_V1_HEADER = false;
        }

        Assert.assertTrue(contextCarrier.isValid());
    }
}
