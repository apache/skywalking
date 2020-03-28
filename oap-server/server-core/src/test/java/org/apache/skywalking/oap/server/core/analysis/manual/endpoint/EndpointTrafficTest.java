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

package org.apache.skywalking.oap.server.core.analysis.manual.endpoint;

import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.junit.Assert;
import org.junit.Test;

public class EndpointTrafficTest {
    @Test
    public void testBuildID() {
        int serviceId = 4;
        String endpointName = "/endpoint-123";
        DetectPoint detectPoint = DetectPoint.SERVER;

        EndpointTraffic endpointTraffic = new EndpointTraffic();
        endpointTraffic.setServiceId(serviceId);
        endpointTraffic.setName(endpointName);
        endpointTraffic.setDetectPoint(detectPoint.value());

        Assert.assertEquals(
            EndpointTraffic.buildId(serviceId, endpointName, detectPoint), EndpointTraffic.buildId(endpointTraffic));

        final EndpointTraffic.EndpointID endpointID = EndpointTraffic.splitID(EndpointTraffic.buildId(endpointTraffic));
        Assert.assertEquals(endpointName, endpointID.getEndpointName());
    }

    @Test
    public void testSerialization() {
        int serviceId = 4;
        String endpointName = "/endpoint-123";
        DetectPoint detectPoint = DetectPoint.SERVER;

        EndpointTraffic endpointTraffic = new EndpointTraffic();
        endpointTraffic.setTimeBucket(202003281420L);
        endpointTraffic.setServiceId(serviceId);
        endpointTraffic.setName(endpointName);
        endpointTraffic.setDetectPoint(detectPoint.value());

        EndpointTraffic another = new EndpointTraffic();
        another.deserialize(endpointTraffic.serialize().build());

        Assert.assertEquals(endpointTraffic, another);
    }

    @Test
    public void testPersistence() {
        int serviceId = 4;
        String endpointName = "/endpoint-123";
        DetectPoint detectPoint = DetectPoint.SERVER;

        EndpointTraffic endpointTraffic = new EndpointTraffic();
        endpointTraffic.setTimeBucket(202003281420L);
        endpointTraffic.setServiceId(serviceId);
        endpointTraffic.setName(endpointName);
        endpointTraffic.setDetectPoint(detectPoint.value());

        final EndpointTraffic.Builder builder = new EndpointTraffic.Builder();
        final EndpointTraffic another = builder.map2Data(builder.data2Map(endpointTraffic));

        Assert.assertEquals(endpointTraffic, another);
    }
}
