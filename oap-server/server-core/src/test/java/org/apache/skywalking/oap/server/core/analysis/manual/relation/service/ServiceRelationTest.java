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

package org.apache.skywalking.oap.server.core.analysis.manual.relation.service;

import org.junit.Assert;
import org.junit.Test;

public class ServiceRelationTest {
    @Test
    public void testServiceRelationClientSideMetricsEquals() {
        ServiceRelationClientSideMetrics thisObject = new ServiceRelationClientSideMetrics();
        thisObject.setEntityId("VXNlcg==.0-em0tY2xpZW50LXNldHRpbmctd2ViYXBpQDEwNjQ4.1");
        thisObject.setTimeBucket(202101071505L);

        ServiceRelationClientSideMetrics otherObject = new ServiceRelationClientSideMetrics();
        otherObject.setEntityId("VXNlcg==.0-em0tY2xpZW50LXNldHRpbmctd2ViYXBpQDEwNjQ4.1");
        otherObject.setTimeBucket(202101071505L);

        Assert.assertTrue(thisObject.equals(otherObject));
    }

    @Test
    public void testServiceRelationClientSideMetricsNotEquals() {
        ServiceRelationClientSideMetrics thisObject = new ServiceRelationClientSideMetrics();
        thisObject.setEntityId("VXNlcg==.0-em0tY2xpZW50LXNldHRpbmctd2ViYXBpQDEwNjQ4.1");
        thisObject.setTimeBucket(202101071505L);

        ServiceRelationClientSideMetrics otherObject = new ServiceRelationClientSideMetrics();
        otherObject.setEntityId("VXNlcg==.0-em0tY2xpZW50LXNldHRpbmctd2ViYXBpQDEwNjQ4.1");
        otherObject.setTimeBucket(202101071506L);

        Assert.assertFalse(thisObject.equals(otherObject));
    }

    @Test
    public void testServiceRelationServerSideMetricsEquals() {
        ServiceRelationServerSideMetrics thisObject = new ServiceRelationServerSideMetrics();
        thisObject.setEntityId("VXNlcg==.0-em0tY2xpZW50LXNldHRpbmctd2ViYXBpQDEwNjQ4.1");
        thisObject.setTimeBucket(202101071505L);

        ServiceRelationServerSideMetrics otherObject = new ServiceRelationServerSideMetrics();
        otherObject.setEntityId("VXNlcg==.0-em0tY2xpZW50LXNldHRpbmctd2ViYXBpQDEwNjQ4.1");
        otherObject.setTimeBucket(202101071505L);

        Assert.assertTrue(thisObject.equals(otherObject));
    }

    @Test
    public void testServiceRelationServerSideMetricsNotEquals() {
        ServiceRelationServerSideMetrics thisObject = new ServiceRelationServerSideMetrics();
        thisObject.setEntityId("VXNlcg==.0-em0tY2xpZW50LXNldHRpbmctd2ViYXBpQDEwNjQ4.1");
        thisObject.setTimeBucket(202101071505L);

        ServiceRelationServerSideMetrics otherObject = new ServiceRelationServerSideMetrics();
        otherObject.setEntityId("VXNlcg==.0-em0tY2xpZW50LXNldHRpbmctd2ViYXBpQDEwNjQ4.1");
        otherObject.setTimeBucket(202101071506L);

        Assert.assertFalse(thisObject.equals(otherObject));
    }
}
