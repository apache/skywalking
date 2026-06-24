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

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.skywalking.oap.server.admin.inspect.response.MqeEntity;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;

/**
 * Decodes a stored {@code entity_id} into the named-attribute shape the
 * inspect API publishes as {@code decoded}, plus the matching
 * {@link MqeEntity} payload an inspect-API client pastes into the public
 * GraphQL {@code execExpression} mutation.
 *
 * <p>Switches on {@link Scope} to pick the right
 * {@link IDManager}.{@code *.analysisId(...)} or
 * {@code *.analysisRelationId(...)} entry point. Scopes that the inspect
 * API explicitly skips ({@link Scope#All}, {@link Scope#Process},
 * {@link Scope#ProcessRelation}) throw {@link IllegalArgumentException} —
 * the caller is expected to filter those before reaching this decoder.
 */
public final class EntityDecoder {

    private EntityDecoder() {
    }

    /**
     * Decoded payload for a single row plus the MQE-ready entity. The
     * {@code decoded} map shape is scope-specific:
     * <ul>
     *   <li>Service / ServiceInstance / Endpoint — single-entity, flat
     *       fields ({@code serviceName}, {@code isReal}, optional
     *       {@code serviceInstanceName} / {@code endpointName}).</li>
     *   <li>ServiceRelation / ServiceInstanceRelation / EndpointRelation —
     *       paired, nested {@code source} and {@code destination} maps.</li>
     * </ul>
     */
    public static final class Decoded {
        public final Map<String, Object> decodedFields;
        public final MqeEntity mqeEntity;
        /**
         * The serviceId of the row's source entity (or the only entity for
         * non-relation scopes), used as the cache key for layer enrichment.
         * {@code null} for scopes with no service-id structure.
         */
        public final String serviceIdForLayer;

        Decoded(final Map<String, Object> decodedFields,
                final MqeEntity mqeEntity,
                final String serviceIdForLayer) {
            this.decodedFields = decodedFields;
            this.mqeEntity = mqeEntity;
            this.serviceIdForLayer = serviceIdForLayer;
        }
    }

    public static Decoded decode(final Scope scope, final String entityId) {
        switch (scope) {
            case Service:
                return decodeService(entityId);
            case ServiceInstance:
                return decodeServiceInstance(entityId);
            case Endpoint:
                return decodeEndpoint(entityId);
            case ServiceRelation:
                return decodeServiceRelation(entityId);
            case ServiceInstanceRelation:
                return decodeServiceInstanceRelation(entityId);
            case EndpointRelation:
                return decodeEndpointRelation(entityId);
            default:
                throw new IllegalArgumentException(
                    "scope " + scope + " is not supported by /inspect/entities");
        }
    }

    /**
     * Structural, scope-free decode for a metric this OAP does not define (no {@link Scope}
     * available). The stored {@code entity_id} self-encodes the names with standard base64 plus
     * the {@code .} / {@code _} / {@code -} delimiters (none of which appear in base64 output), so
     * the entity kind is recoverable from delimiter structure alone:
     * <ul>
     *   <li>no {@code -}, no {@code _} → service</li>
     *   <li>no {@code -}, one {@code _} → 2nd-level entity (service instance OR endpoint —
     *       byte-identical encoding, emitted as a generic {@code name})</li>
     *   <li>one {@code -} (2 parts) → service relation, or 2nd-level relation when each side has a
     *       {@code _}</li>
     *   <li>three {@code -} (4 parts) → endpoint relation</li>
     * </ul>
     * The only thing not recoverable is the instance-vs-endpoint label, so the leaf is reported as
     * {@code name} and no {@link MqeEntity} is produced — MQE re-query needs the exact scope, and a
     * foreign metric is not MQE-queryable on this node anyway.
     */
    public static Decoded decodeUnknownScope(final String entityId) {
        final String[] relationParts = entityId.split(Const.RELATION_ID_PARSER_SPLIT);
        switch (relationParts.length) {
            case 1:
                return entityId.contains(Const.ID_CONNECTOR)
                    ? decodeLevel2Generic(entityId)
                    : decodeServiceGeneric(entityId);
            case 2:
                return relationParts[0].contains(Const.ID_CONNECTOR)
                    ? decodeLevel2RelationGeneric(entityId)
                    : decodeServiceRelationGeneric(entityId);
            case 4:
                return decodeEndpointRelationGeneric(entityId);
            default:
                throw new IllegalArgumentException(
                    "cannot structurally decode entity_id without scope: " + entityId);
        }
    }

    private static Decoded decodeServiceGeneric(final String entityId) {
        final IDManager.ServiceID.ServiceIDDefinition def = IDManager.ServiceID.analysisId(entityId);
        final Map<String, Object> decoded = new LinkedHashMap<>();
        decoded.put("serviceName", def.getName());
        decoded.put("isReal", def.isReal());
        return new Decoded(decoded, null, entityId);
    }

    private static Decoded decodeLevel2Generic(final String entityId) {
        final IDManager.ServiceInstanceID.InstanceIDDefinition def =
            IDManager.ServiceInstanceID.analysisId(entityId);
        final IDManager.ServiceID.ServiceIDDefinition svc =
            IDManager.ServiceID.analysisId(def.getServiceId());
        return new Decoded(toLevel2Map(svc, def.getName()), null, def.getServiceId());
    }

    private static Decoded decodeServiceRelationGeneric(final String entityId) {
        final IDManager.ServiceID.ServiceRelationDefine rel =
            IDManager.ServiceID.analysisRelationId(entityId);
        final IDManager.ServiceID.ServiceIDDefinition src = IDManager.ServiceID.analysisId(rel.getSourceId());
        final IDManager.ServiceID.ServiceIDDefinition dst = IDManager.ServiceID.analysisId(rel.getDestId());
        final Map<String, Object> decoded = new LinkedHashMap<>();
        decoded.put("source", toServiceMap(src));
        decoded.put("destination", toServiceMap(dst));
        return new Decoded(decoded, null, rel.getSourceId());
    }

    private static Decoded decodeLevel2RelationGeneric(final String entityId) {
        final IDManager.ServiceInstanceID.ServiceInstanceRelationDefine rel =
            IDManager.ServiceInstanceID.analysisRelationId(entityId);
        final IDManager.ServiceInstanceID.InstanceIDDefinition srcInst =
            IDManager.ServiceInstanceID.analysisId(rel.getSourceId());
        final IDManager.ServiceInstanceID.InstanceIDDefinition dstInst =
            IDManager.ServiceInstanceID.analysisId(rel.getDestId());
        final Map<String, Object> decoded = new LinkedHashMap<>();
        decoded.put("source", toLevel2Map(IDManager.ServiceID.analysisId(srcInst.getServiceId()), srcInst.getName()));
        decoded.put("destination", toLevel2Map(IDManager.ServiceID.analysisId(dstInst.getServiceId()), dstInst.getName()));
        return new Decoded(decoded, null, srcInst.getServiceId());
    }

    private static Decoded decodeEndpointRelationGeneric(final String entityId) {
        final IDManager.EndpointID.EndpointRelationDefine rel =
            IDManager.EndpointID.analysisRelationId(entityId);
        final IDManager.ServiceID.ServiceIDDefinition srcSvc =
            IDManager.ServiceID.analysisId(rel.getSourceServiceId());
        final IDManager.ServiceID.ServiceIDDefinition dstSvc =
            IDManager.ServiceID.analysisId(rel.getDestServiceId());
        final Map<String, Object> decoded = new LinkedHashMap<>();
        decoded.put("source", toLevel2Map(srcSvc, rel.getSource()));
        decoded.put("destination", toLevel2Map(dstSvc, rel.getDest()));
        return new Decoded(decoded, null, rel.getSourceServiceId());
    }

    private static Map<String, Object> toLevel2Map(final IDManager.ServiceID.ServiceIDDefinition svc,
                                                   final String leafName) {
        final Map<String, Object> map = toServiceMap(svc);
        map.put("name", leafName);
        return map;
    }

    private static Decoded decodeService(final String entityId) {
        final IDManager.ServiceID.ServiceIDDefinition def = IDManager.ServiceID.analysisId(entityId);
        final Map<String, Object> decoded = new LinkedHashMap<>();
        decoded.put("serviceName", def.getName());
        decoded.put("isReal", def.isReal());
        final MqeEntity mqe = MqeEntity.builder()
                                             .scope("Service")
                                             .serviceName(def.getName())
                                             .normal(def.isReal())
                                             .build();
        return new Decoded(decoded, mqe, entityId);
    }

    private static Decoded decodeServiceInstance(final String entityId) {
        final IDManager.ServiceInstanceID.InstanceIDDefinition def =
            IDManager.ServiceInstanceID.analysisId(entityId);
        final IDManager.ServiceID.ServiceIDDefinition svc =
            IDManager.ServiceID.analysisId(def.getServiceId());
        final Map<String, Object> decoded = new LinkedHashMap<>();
        decoded.put("serviceName", svc.getName());
        decoded.put("isReal", svc.isReal());
        decoded.put("serviceInstanceName", def.getName());
        final MqeEntity mqe = MqeEntity.builder()
                                             .scope("ServiceInstance")
                                             .serviceName(svc.getName())
                                             .normal(svc.isReal())
                                             .serviceInstanceName(def.getName())
                                             .build();
        return new Decoded(decoded, mqe, def.getServiceId());
    }

    private static Decoded decodeEndpoint(final String entityId) {
        final IDManager.EndpointID.EndpointIDDefinition def =
            IDManager.EndpointID.analysisId(entityId);
        final IDManager.ServiceID.ServiceIDDefinition svc =
            IDManager.ServiceID.analysisId(def.getServiceId());
        final Map<String, Object> decoded = new LinkedHashMap<>();
        decoded.put("serviceName", svc.getName());
        decoded.put("isReal", svc.isReal());
        decoded.put("endpointName", def.getEndpointName());
        final MqeEntity mqe = MqeEntity.builder()
                                             .scope("Endpoint")
                                             .serviceName(svc.getName())
                                             .normal(svc.isReal())
                                             .endpointName(def.getEndpointName())
                                             .build();
        return new Decoded(decoded, mqe, def.getServiceId());
    }

    private static Decoded decodeServiceRelation(final String entityId) {
        final IDManager.ServiceID.ServiceRelationDefine rel =
            IDManager.ServiceID.analysisRelationId(entityId);
        final IDManager.ServiceID.ServiceIDDefinition src =
            IDManager.ServiceID.analysisId(rel.getSourceId());
        final IDManager.ServiceID.ServiceIDDefinition dst =
            IDManager.ServiceID.analysisId(rel.getDestId());
        final Map<String, Object> decoded = new LinkedHashMap<>();
        decoded.put("source", toServiceMap(src));
        decoded.put("destination", toServiceMap(dst));
        final MqeEntity mqe = MqeEntity.builder()
                                             .scope("ServiceRelation")
                                             .serviceName(src.getName())
                                             .normal(src.isReal())
                                             .destServiceName(dst.getName())
                                             .destNormal(dst.isReal())
                                             .build();
        return new Decoded(decoded, mqe, rel.getSourceId());
    }

    private static Decoded decodeServiceInstanceRelation(final String entityId) {
        final IDManager.ServiceInstanceID.ServiceInstanceRelationDefine rel =
            IDManager.ServiceInstanceID.analysisRelationId(entityId);
        final IDManager.ServiceInstanceID.InstanceIDDefinition srcInst =
            IDManager.ServiceInstanceID.analysisId(rel.getSourceId());
        final IDManager.ServiceInstanceID.InstanceIDDefinition dstInst =
            IDManager.ServiceInstanceID.analysisId(rel.getDestId());
        final IDManager.ServiceID.ServiceIDDefinition srcSvc =
            IDManager.ServiceID.analysisId(srcInst.getServiceId());
        final IDManager.ServiceID.ServiceIDDefinition dstSvc =
            IDManager.ServiceID.analysisId(dstInst.getServiceId());
        final Map<String, Object> decoded = new LinkedHashMap<>();
        decoded.put("source", toInstanceMap(srcSvc, srcInst));
        decoded.put("destination", toInstanceMap(dstSvc, dstInst));
        final MqeEntity mqe = MqeEntity.builder()
                                             .scope("ServiceInstanceRelation")
                                             .serviceName(srcSvc.getName())
                                             .normal(srcSvc.isReal())
                                             .serviceInstanceName(srcInst.getName())
                                             .destServiceName(dstSvc.getName())
                                             .destNormal(dstSvc.isReal())
                                             .destServiceInstanceName(dstInst.getName())
                                             .build();
        return new Decoded(decoded, mqe, srcInst.getServiceId());
    }

    private static Decoded decodeEndpointRelation(final String entityId) {
        final IDManager.EndpointID.EndpointRelationDefine rel =
            IDManager.EndpointID.analysisRelationId(entityId);
        final IDManager.ServiceID.ServiceIDDefinition srcSvc =
            IDManager.ServiceID.analysisId(rel.getSourceServiceId());
        final IDManager.ServiceID.ServiceIDDefinition dstSvc =
            IDManager.ServiceID.analysisId(rel.getDestServiceId());
        final Map<String, Object> decoded = new LinkedHashMap<>();
        decoded.put("source", toEndpointMap(srcSvc, rel.getSource()));
        decoded.put("destination", toEndpointMap(dstSvc, rel.getDest()));
        final MqeEntity mqe = MqeEntity.builder()
                                             .scope("EndpointRelation")
                                             .serviceName(srcSvc.getName())
                                             .normal(srcSvc.isReal())
                                             .endpointName(rel.getSource())
                                             .destServiceName(dstSvc.getName())
                                             .destNormal(dstSvc.isReal())
                                             .destEndpointName(rel.getDest())
                                             .build();
        return new Decoded(decoded, mqe, rel.getSourceServiceId());
    }

    private static Map<String, Object> toServiceMap(final IDManager.ServiceID.ServiceIDDefinition svc) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("serviceName", svc.getName());
        map.put("isReal", svc.isReal());
        return map;
    }

    private static Map<String, Object> toInstanceMap(final IDManager.ServiceID.ServiceIDDefinition svc,
                                                    final IDManager.ServiceInstanceID.InstanceIDDefinition inst) {
        final Map<String, Object> map = toServiceMap(svc);
        map.put("serviceInstanceName", inst.getName());
        return map;
    }

    private static Map<String, Object> toEndpointMap(final IDManager.ServiceID.ServiceIDDefinition svc,
                                                    final String endpointName) {
        final Map<String, Object> map = toServiceMap(svc);
        map.put("endpointName", endpointName);
        return map;
    }
}
