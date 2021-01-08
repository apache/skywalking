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

package org.apache.skywalking.oap.server.core.analysis.manual.relation.endpoint;

import org.junit.Assert;
import org.junit.Test;

public class EndpointCallRelationTest {
    @Test
    public void testEndpointRelationServerSideMetricsEquals() {
        EndpointRelationServerSideMetrics thisObject = new EndpointRelationServerSideMetrics();
        thisObject.setEntityId(
            "VXNlcg==.0-VXNlcg==-em1iaXotcHJvbW90aW9uMi1hZG1pbkAxMjUyNw==.1-L0Bpbi9hcGkvaGVhbHRo");
        thisObject.setTimeBucket(202101071505L);

        EndpointRelationServerSideMetrics otherObject = new EndpointRelationServerSideMetrics();
        otherObject.setEntityId(
            "VXNlcg==.0-VXNlcg==-em1iaXotcHJvbW90aW9uMi1hZG1pbkAxMjUyNw==.1-L0Bpbi9hcGkvaGVhbHRo");
        otherObject.setTimeBucket(202101071505L);

        Assert.assertTrue(thisObject.equals(otherObject));
    }

    @Test
    public void testEndpointRelationServerSideMetricsNotEquals() {
        EndpointRelationServerSideMetrics thisObject = new EndpointRelationServerSideMetrics();
        thisObject.setEntityId(
            "VXNlcg==.0-VXNlcg==-em1iaXotcHJvbW90aW9uMi1hZG1pbkAxMjUyNw==.1-L0Bpbi9hcGkvaGVhbHRo");
        thisObject.setTimeBucket(202101071505L);

        EndpointRelationServerSideMetrics otherObject = new EndpointRelationServerSideMetrics();
        otherObject.setEntityId(
            "VXNlcg==.0-VXNlcg==-em1iaXotcHJvbW90aW9uMi1hZG1pbkAxMjUyNw==.1-L0Bpbi9hcGkvaGVhbHRo");
        otherObject.setTimeBucket(202101071506L);

        Assert.assertFalse(thisObject.equals(otherObject));
    }
}
