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

package org.apache.skywalking.oap.server.core.analysis.meter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MeterEntityTest {
    @BeforeAll
    static void setUp() {
        MeterEntity.setNamingControl(new NamingControl(512, 512, 512, new EndpointNameGrouping()));
    }

    @AfterAll
    static void tearDown() {
        MeterEntity.setNamingControl(null);
    }

    @Test
    void shouldUseConjecturedServiceIdsForVirtualLayerRelations() {
        final MeterEntity relation = MeterEntity.newServiceRelation(
            "source", "destination", DetectPoint.CLIENT, Layer.VIRTUAL_GENAI, 0
        );
        final String sourceServiceId = IDManager.ServiceID.buildId("source", false);
        final String destServiceId = IDManager.ServiceID.buildId("destination", false);

        assertEquals(sourceServiceId, relation.sourceServiceId());
        assertEquals(destServiceId, relation.destServiceId());
        assertEquals(
            IDManager.ServiceID.buildRelationId(
                new IDManager.ServiceID.ServiceRelationDefine(sourceServiceId, destServiceId)
            ),
            relation.id()
        );
    }

    @Test
    void shouldUseConjecturedServiceIdsForVirtualLayerInstanceRelations() {
        final MeterEntity relation = MeterEntity.newServiceInstanceRelation(
            "source", "source-instance",
            "destination", "destination-instance",
            DetectPoint.CLIENT, Layer.VIRTUAL_GENAI, 0
        );
        final String sourceServiceId = IDManager.ServiceID.buildId("source", false);
        final String destServiceId = IDManager.ServiceID.buildId("destination", false);
        final String sourceInstanceId = IDManager.ServiceInstanceID.buildId(sourceServiceId, "source-instance");
        final String destInstanceId = IDManager.ServiceInstanceID.buildId(destServiceId, "destination-instance");

        assertEquals(sourceServiceId, relation.sourceServiceId());
        assertEquals(destServiceId, relation.destServiceId());
        assertEquals(sourceInstanceId, relation.sourceServiceInstanceId());
        assertEquals(destInstanceId, relation.destServiceInstanceId());
        assertEquals(
            IDManager.ServiceInstanceID.buildRelationId(
                new IDManager.ServiceInstanceID.ServiceInstanceRelationDefine(sourceInstanceId, destInstanceId)
            ),
            relation.id()
        );
    }

    @Test
    void shouldKeepNormalServiceIdsForNormalLayerRelations() {
        final MeterEntity relation = MeterEntity.newServiceRelation(
            "source", "destination", DetectPoint.CLIENT, Layer.GENERAL, 0
        );

        assertEquals(IDManager.ServiceID.buildId("source", true), relation.sourceServiceId());
        assertEquals(IDManager.ServiceID.buildId("destination", true), relation.destServiceId());
    }
}
