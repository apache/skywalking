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

package org.apache.skywalking.oap.server.admin.inspect.decoder;

import java.util.Map;
import org.apache.skywalking.oap.server.admin.inspect.response.MqeEntity;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip tests: build an entity_id with the same {@link IDManager} entry points
 * the framework uses, decode it back through {@link EntityDecoder}, and verify the
 * decoded names + isReal flag + the MQE-ready entity payload match.
 */
class EntityDecoderTest {

    @Test
    void serviceNormal() {
        final String id = IDManager.ServiceID.buildId("payment", true);
        final EntityDecoder.Decoded d = EntityDecoder.decode(Scope.Service, id);

        assertEquals("payment", d.decodedFields.get("serviceName"));
        assertEquals(Boolean.TRUE, d.decodedFields.get("isReal"));
        final MqeEntity e = d.mqeEntity;
        assertEquals("Service", e.getScope());
        assertEquals("payment", e.getServiceName());
        assertEquals(Boolean.TRUE, e.getNormal());
        assertEquals(id, d.serviceIdForLayer);
    }

    @Test
    void serviceConjectured() {
        // "isNormal=false" — virtual / conjectured service (e.g., a database we detected
        // from a span tag instead of an agent installation).
        final String id = IDManager.ServiceID.buildId("mysql", false);
        final EntityDecoder.Decoded d = EntityDecoder.decode(Scope.Service, id);

        assertEquals("mysql", d.decodedFields.get("serviceName"));
        assertEquals(Boolean.FALSE, d.decodedFields.get("isReal"));
        assertEquals(Boolean.FALSE, d.mqeEntity.getNormal());
    }

    @Test
    void serviceInstance() {
        final String svcId = IDManager.ServiceID.buildId("payment", true);
        final String id = IDManager.ServiceInstanceID.buildId(svcId, "pod-01");
        final EntityDecoder.Decoded d = EntityDecoder.decode(Scope.ServiceInstance, id);

        assertEquals("payment", d.decodedFields.get("serviceName"));
        assertEquals(Boolean.TRUE, d.decodedFields.get("isReal"));
        assertEquals("pod-01", d.decodedFields.get("serviceInstanceName"));
        assertEquals("ServiceInstance", d.mqeEntity.getScope());
        assertEquals("payment", d.mqeEntity.getServiceName());
        assertEquals("pod-01", d.mqeEntity.getServiceInstanceName());
        assertEquals(svcId, d.serviceIdForLayer);
    }

    @Test
    void endpoint() {
        final String svcId = IDManager.ServiceID.buildId("payment", true);
        final String id = IDManager.EndpointID.buildId(svcId, "POST:/charge");
        final EntityDecoder.Decoded d = EntityDecoder.decode(Scope.Endpoint, id);

        assertEquals("payment", d.decodedFields.get("serviceName"));
        assertEquals("POST:/charge", d.decodedFields.get("endpointName"));
        assertEquals("Endpoint", d.mqeEntity.getScope());
        assertEquals("POST:/charge", d.mqeEntity.getEndpointName());
        assertEquals(svcId, d.serviceIdForLayer);
    }

    @Test
    void serviceRelation() {
        final String src = IDManager.ServiceID.buildId("checkout", true);
        final String dst = IDManager.ServiceID.buildId("payment", true);
        final String id = IDManager.ServiceID.buildRelationId(
            new IDManager.ServiceID.ServiceRelationDefine(src, dst));
        final EntityDecoder.Decoded d = EntityDecoder.decode(Scope.ServiceRelation, id);

        @SuppressWarnings("unchecked") final Map<String, Object> srcMap =
            (Map<String, Object>) d.decodedFields.get("source");
        @SuppressWarnings("unchecked") final Map<String, Object> dstMap =
            (Map<String, Object>) d.decodedFields.get("destination");
        assertEquals("checkout", srcMap.get("serviceName"));
        assertEquals(Boolean.TRUE, srcMap.get("isReal"));
        assertEquals("payment", dstMap.get("serviceName"));

        assertEquals("ServiceRelation", d.mqeEntity.getScope());
        assertEquals("checkout", d.mqeEntity.getServiceName());
        assertEquals("payment", d.mqeEntity.getDestServiceName());
        assertEquals(src, d.serviceIdForLayer);
    }

    @Test
    void serviceInstanceRelation() {
        final String srcSvc = IDManager.ServiceID.buildId("consumer", true);
        final String dstSvc = IDManager.ServiceID.buildId("provider", true);
        final String srcInst = IDManager.ServiceInstanceID.buildId(srcSvc, "pod-a");
        final String dstInst = IDManager.ServiceInstanceID.buildId(dstSvc, "pod-b");
        final String id = IDManager.ServiceInstanceID.buildRelationId(
            new IDManager.ServiceInstanceID.ServiceInstanceRelationDefine(srcInst, dstInst));
        final EntityDecoder.Decoded d = EntityDecoder.decode(Scope.ServiceInstanceRelation, id);

        @SuppressWarnings("unchecked") final Map<String, Object> srcMap =
            (Map<String, Object>) d.decodedFields.get("source");
        @SuppressWarnings("unchecked") final Map<String, Object> dstMap =
            (Map<String, Object>) d.decodedFields.get("destination");
        assertEquals("consumer", srcMap.get("serviceName"));
        assertEquals("pod-a", srcMap.get("serviceInstanceName"));
        assertEquals("provider", dstMap.get("serviceName"));
        assertEquals("pod-b", dstMap.get("serviceInstanceName"));

        assertEquals("consumer", d.mqeEntity.getServiceName());
        assertEquals("pod-a", d.mqeEntity.getServiceInstanceName());
        assertEquals("provider", d.mqeEntity.getDestServiceName());
        assertEquals("pod-b", d.mqeEntity.getDestServiceInstanceName());
        assertEquals(srcSvc, d.serviceIdForLayer);
    }

    @Test
    void endpointRelation() {
        final String srcSvc = IDManager.ServiceID.buildId("consumer", true);
        final String dstSvc = IDManager.ServiceID.buildId("provider", true);
        final String id = IDManager.EndpointID.buildRelationId(
            new IDManager.EndpointID.EndpointRelationDefine(srcSvc, "/order", dstSvc, "/charge"));
        final EntityDecoder.Decoded d = EntityDecoder.decode(Scope.EndpointRelation, id);

        @SuppressWarnings("unchecked") final Map<String, Object> srcMap =
            (Map<String, Object>) d.decodedFields.get("source");
        @SuppressWarnings("unchecked") final Map<String, Object> dstMap =
            (Map<String, Object>) d.decodedFields.get("destination");
        assertEquals("consumer", srcMap.get("serviceName"));
        assertEquals("/order", srcMap.get("endpointName"));
        assertEquals("provider", dstMap.get("serviceName"));
        assertEquals("/charge", dstMap.get("endpointName"));

        assertEquals("consumer", d.mqeEntity.getServiceName());
        assertEquals("/order", d.mqeEntity.getEndpointName());
        assertEquals("provider", d.mqeEntity.getDestServiceName());
        assertEquals("/charge", d.mqeEntity.getDestEndpointName());
        assertEquals(srcSvc, d.serviceIdForLayer);
    }

    @Test
    void unsupportedScopesReject() {
        // Process / ProcessRelation / All are explicitly out of v1 scope.
        assertThrows(IllegalArgumentException.class,
            () -> EntityDecoder.decode(Scope.Process, "deadbeef"));
        assertThrows(IllegalArgumentException.class,
            () -> EntityDecoder.decode(Scope.ProcessRelation, "deadbeef-cafebabe"));
        assertThrows(IllegalArgumentException.class,
            () -> EntityDecoder.decode(Scope.All, "anything"));
    }

    @Test
    void serviceMqeEntityOmitsRelationFields() {
        // Sanity check the @JsonInclude(NON_NULL) annotation on MqeEntity does what we expect:
        // a Service-scope payload should not carry destServiceName / destNormal / instance /
        // endpoint fields. We assert by checking the field accessors directly.
        final EntityDecoder.Decoded d = EntityDecoder.decode(
            Scope.Service, IDManager.ServiceID.buildId("a", true));
        assertNull(d.mqeEntity.getDestServiceName());
        assertNull(d.mqeEntity.getDestNormal());
        assertNull(d.mqeEntity.getServiceInstanceName());
        assertNull(d.mqeEntity.getEndpointName());
        assertTrue(d.mqeEntity.getNormal());
    }

    // ==================== scope-free (foreign metric) decode ====================

    @Test
    void unknownScopeService() {
        final String id = IDManager.ServiceID.buildId("payment", true);
        final EntityDecoder.Decoded d = EntityDecoder.decodeUnknownScope(id);
        assertEquals("payment", d.decodedFields.get("serviceName"));
        assertEquals(Boolean.TRUE, d.decodedFields.get("isReal"));
        assertNull(d.mqeEntity);
        assertEquals(id, d.serviceIdForLayer);
    }

    @Test
    void unknownScopeServiceConjectured() {
        final String id = IDManager.ServiceID.buildId("mysql", false);
        final EntityDecoder.Decoded d = EntityDecoder.decodeUnknownScope(id);
        assertEquals("mysql", d.decodedFields.get("serviceName"));
        assertEquals(Boolean.FALSE, d.decodedFields.get("isReal"));
    }

    @Test
    void unknownScopeLevel2InstanceAndEndpointDecodeIdentically() {
        // Instance and endpoint encode byte-identically (serviceId + "_" + base64(name)), so the
        // scope-free decode yields the same shape with a generic "name" leaf for both.
        final String svcId = IDManager.ServiceID.buildId("payment", true);
        final String instId = IDManager.ServiceInstanceID.buildId(svcId, "pod-01");
        final String epId = IDManager.EndpointID.buildId(svcId, "POST:/charge");

        final EntityDecoder.Decoded inst = EntityDecoder.decodeUnknownScope(instId);
        assertEquals("payment", inst.decodedFields.get("serviceName"));
        assertEquals("pod-01", inst.decodedFields.get("name"));
        assertNull(inst.decodedFields.get("serviceInstanceName"));
        assertNull(inst.mqeEntity);
        assertEquals(svcId, inst.serviceIdForLayer);

        final EntityDecoder.Decoded ep = EntityDecoder.decodeUnknownScope(epId);
        assertEquals("payment", ep.decodedFields.get("serviceName"));
        assertEquals("POST:/charge", ep.decodedFields.get("name"));
        assertNull(ep.decodedFields.get("endpointName"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void unknownScopeServiceRelation() {
        final String src = IDManager.ServiceID.buildId("checkout", true);
        final String dst = IDManager.ServiceID.buildId("payment", true);
        final String id = IDManager.ServiceID.buildRelationId(
            new IDManager.ServiceID.ServiceRelationDefine(src, dst));

        final EntityDecoder.Decoded d = EntityDecoder.decodeUnknownScope(id);
        final Map<String, Object> source = (Map<String, Object>) d.decodedFields.get("source");
        final Map<String, Object> dest = (Map<String, Object>) d.decodedFields.get("destination");
        assertEquals("checkout", source.get("serviceName"));
        assertEquals("payment", dest.get("serviceName"));
        assertNull(source.get("name"));
        assertNull(d.mqeEntity);
        assertEquals(src, d.serviceIdForLayer);
    }

    @SuppressWarnings("unchecked")
    @Test
    void unknownScopeLevel2Relation() {
        final String srcSvc = IDManager.ServiceID.buildId("consumer", true);
        final String dstSvc = IDManager.ServiceID.buildId("provider", true);
        final String srcInst = IDManager.ServiceInstanceID.buildId(srcSvc, "pod-a");
        final String dstInst = IDManager.ServiceInstanceID.buildId(dstSvc, "pod-b");
        final String id = IDManager.ServiceInstanceID.buildRelationId(
            new IDManager.ServiceInstanceID.ServiceInstanceRelationDefine(srcInst, dstInst));

        final EntityDecoder.Decoded d = EntityDecoder.decodeUnknownScope(id);
        final Map<String, Object> source = (Map<String, Object>) d.decodedFields.get("source");
        final Map<String, Object> dest = (Map<String, Object>) d.decodedFields.get("destination");
        assertEquals("consumer", source.get("serviceName"));
        assertEquals("pod-a", source.get("name"));
        assertEquals("provider", dest.get("serviceName"));
        assertEquals("pod-b", dest.get("name"));
        assertEquals(srcSvc, d.serviceIdForLayer);
    }

    @SuppressWarnings("unchecked")
    @Test
    void unknownScopeEndpointRelation() {
        final String srcSvc = IDManager.ServiceID.buildId("consumer", true);
        final String dstSvc = IDManager.ServiceID.buildId("provider", true);
        final String id = IDManager.EndpointID.buildRelationId(
            new IDManager.EndpointID.EndpointRelationDefine(srcSvc, "/order", dstSvc, "/charge"));

        final EntityDecoder.Decoded d = EntityDecoder.decodeUnknownScope(id);
        final Map<String, Object> source = (Map<String, Object>) d.decodedFields.get("source");
        final Map<String, Object> dest = (Map<String, Object>) d.decodedFields.get("destination");
        assertEquals("consumer", source.get("serviceName"));
        assertEquals("/order", source.get("name"));
        assertEquals("provider", dest.get("serviceName"));
        assertEquals("/charge", dest.get("name"));
        assertEquals(srcSvc, d.serviceIdForLayer);
    }
}
