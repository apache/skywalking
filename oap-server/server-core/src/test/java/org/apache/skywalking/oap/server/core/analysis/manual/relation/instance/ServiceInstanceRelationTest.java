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

package org.apache.skywalking.oap.server.core.analysis.manual.relation.instance;

import org.junit.Assert;
import org.junit.Test;

public class ServiceInstanceRelationTest {
    @Test
    public void testServiceInstanceRelationClientSideMetricsEquals() {
        ServiceInstanceRelationClientSideMetrics thisObject = new ServiceInstanceRelationClientSideMetrics();
        thisObject.setEntityId(
            "em1jLWJlYWNvbi1taWRkbGV3YXJlQDExMTIz.1_MTAuMTExLjIzMi4yMDc=-MTkyLjE2OC40Ni4xNDM6NDY2MDY=.0_MTkyLjE2OC40Ni4xNDM6NDY2MDY=");
        thisObject.setTimeBucket(202101071505L);

        ServiceInstanceRelationClientSideMetrics otherObject = new ServiceInstanceRelationClientSideMetrics();
        otherObject.setEntityId(
            "em1jLWJlYWNvbi1taWRkbGV3YXJlQDExMTIz.1_MTAuMTExLjIzMi4yMDc=-MTkyLjE2OC40Ni4xNDM6NDY2MDY=.0_MTkyLjE2OC40Ni4xNDM6NDY2MDY=");
        otherObject.setTimeBucket(202101071505L);

        Assert.assertTrue(thisObject.equals(otherObject));
    }

    @Test
    public void testServiceInstanceRelationClientSideMetricsNotEquals() {
        ServiceInstanceRelationClientSideMetrics thisObject = new ServiceInstanceRelationClientSideMetrics();
        thisObject.setEntityId(
            "em1jLWJlYWNvbi1taWRkbGV3YXJlQDExMTIz.1_MTAuMTExLjIzMi4yMDc=-MTkyLjE2OC40Ni4xNDM6NDY2MDY=.0_MTkyLjE2OC40Ni4xNDM6NDY2MDY=");
        thisObject.setTimeBucket(202101071505L);

        ServiceInstanceRelationClientSideMetrics otherObject = new ServiceInstanceRelationClientSideMetrics();
        otherObject.setEntityId(
            "em1jLWJlYWNvbi1taWRkbGV3YXJlQDExMTIz.1_MTAuMTExLjIzMi4yMDc=-MTkyLjE2OC40Ni4xNDM6NDY2MDY=.0_MTkyLjE2OC40Ni4xNDM6NDY2MDY=");
        otherObject.setTimeBucket(202101071506L);

        Assert.assertFalse(thisObject.equals(otherObject));
    }

    @Test
    public void testServiceInstanceRelationServerSideMetricsEquals() {
        ServiceInstanceRelationServerSideMetrics thisObject = new ServiceInstanceRelationServerSideMetrics();
        thisObject.setEntityId(
            "em1jLWJlYWNvbi1taWRkbGV3YXJlQDExMTIz.1_MTAuMTExLjIzMi4yMDc=-MTkyLjE2OC40Ni4xNDM6NDY2MDY=.0_MTkyLjE2OC40Ni4xNDM6NDY2MDY=");
        thisObject.setTimeBucket(202101071505L);

        ServiceInstanceRelationServerSideMetrics otherObject = new ServiceInstanceRelationServerSideMetrics();
        otherObject.setEntityId(
            "em1jLWJlYWNvbi1taWRkbGV3YXJlQDExMTIz.1_MTAuMTExLjIzMi4yMDc=-MTkyLjE2OC40Ni4xNDM6NDY2MDY=.0_MTkyLjE2OC40Ni4xNDM6NDY2MDY=");
        otherObject.setTimeBucket(202101071505L);

        Assert.assertTrue(thisObject.equals(otherObject));
    }

    @Test
    public void testServiceInstanceRelationServerSideMetricsNotEquals() {
        ServiceInstanceRelationServerSideMetrics thisObject = new ServiceInstanceRelationServerSideMetrics();
        thisObject.setEntityId(
            "em1jLWJlYWNvbi1taWRkbGV3YXJlQDExMTIz.1_MTAuMTExLjIzMi4yMDc=-MTkyLjE2OC40Ni4xNDM6NDY2MDY=.0_MTkyLjE2OC40Ni4xNDM6NDY2MDY=");
        thisObject.setTimeBucket(202101071505L);

        ServiceInstanceRelationServerSideMetrics otherObject = new ServiceInstanceRelationServerSideMetrics();
        otherObject.setEntityId(
            "em1jLWJlYWNvbi1taWRkbGV3YXJlQDExMTIz.1_MTAuMTExLjIzMi4yMDc=-MTkyLjE2OC40Ni4xNDM6NDY2MDY=.0_MTkyLjE2OC40Ni4xNDM6NDY2MDY=");
        otherObject.setTimeBucket(202101071506L);

        Assert.assertFalse(thisObject.equals(otherObject));
    }
}
