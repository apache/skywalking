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

package org.apache.skywalking.oap.server.core.analysis;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.source.NodeType;

/**
 * IDManager includes all ID encode/decode functions for service, service instance and endpoint.
 */
public class IDManager {
    /**
     * Service ID related functions.
     */
    public static class ServiceID {
        /**
         * @return encoded service id
         */
        public static String buildId(String name, NodeType type) {
            return encode(name) + Const.ID_SPLIT + type.value();
        }

        /**
         * @return service ID object decoded from {@link #buildId(String, NodeType)} result
         */
        public static ServiceIDDefinition analysisId(String id) {
            final String[] strings = id.split(Const.ID_PARSER_SPLIT);
            if (strings.length != 2) {
                throw new UnexpectedException("Can't split service id into 2 parts, " + id);
            }
            return new ServiceID.ServiceIDDefinition(
                decode(strings[0]),
                NodeType.valueOf(Integer.parseInt(strings[1]))
            );
        }

        /**
         * @return encoded service relation id
         */
        public static String buildRelationId(ServiceRelationDefine define) {
            return define.sourceId + Const.ID_SPLIT + define.destId + Const.ID_SPLIT + define.componentId;
        }

        /**
         * @return service relation ID object decoded from {@link #buildRelationId(ServiceRelationDefine)} result
         */
        public static ServiceRelationDefine analysisRelationId(String entityId) {
            String[] parts = entityId.split(Const.ID_SPLIT);
            if (parts.length != 3) {
                throw new RuntimeException("Illegal Service Relation entity id");
            }
            return new ServiceRelationDefine(parts[0], parts[1], Integer.parseInt(parts[2]));
        }

        @RequiredArgsConstructor
        @Getter
        public static class ServiceIDDefinition {
            private final String name;
            private final NodeType type;
        }

        @RequiredArgsConstructor
        @Getter
        @EqualsAndHashCode
        public static class ServiceRelationDefine {
            private final String sourceId;
            private final String destId;
            private final int componentId;
        }
    }

    /**
     * Service Instance ID related functions.
     */
    public static class ServiceInstanceID {
        /**
         * @param serviceId built by {@link ServiceID#buildId(String, NodeType)}
         * @return service instance id
         */
        public static String buildId(String serviceId, String instanceName) {
            return serviceId
                + Const.ID_SPLIT
                + encode(instanceName);
        }

        /**
         * @return service instance id object decoded from {@link #buildId(String, String)} result
         */
        public static ServiceInstanceID.InstanceIDDefinition analysisId(String id) {
            final String[] strings = id.split(Const.ID_PARSER_SPLIT);
            if (strings.length != 2) {
                throw new UnexpectedException("Can't split instance id into 2 parts, " + id);
            }
            return new ServiceInstanceID.InstanceIDDefinition(
                strings[0],
                decode(strings[1])
            );
        }

        /**
         * @return encoded service instance relation id
         */
        public static String buildRelationId(ServiceInstanceRelationDefine define) {
            return define.source + Const.ID_SPLIT + define.dest + Const.ID_SPLIT + define.componentId;
        }

        /**
         * @return service instance relation ID object decoded from {@link #buildRelationId(ServiceInstanceRelationDefine)}
         * result
         */
        public static ServiceInstanceID.ServiceInstanceRelationDefine analysisRelationId(String entityId) {
            String[] parts = entityId.split(Const.ID_SPLIT);
            if (parts.length != 3) {
                throw new RuntimeException("Illegal Service Instance Relation entity id");
            }
            return new ServiceInstanceID.ServiceInstanceRelationDefine(parts[0], parts[1], Integer.parseInt(parts[2]));
        }

        @RequiredArgsConstructor
        @Getter
        public static class InstanceIDDefinition {
            /**
             * Built by {@link ServiceID#buildId(String, NodeType)}
             */
            private final String serviceId;
            private final String name;
        }

        @RequiredArgsConstructor
        @Getter
        @EqualsAndHashCode
        public static class ServiceInstanceRelationDefine {
            /**
             * Built by {@link ServiceID#buildId(String, NodeType)}
             */
            private final String source;
            /**
             * Built by {@link ServiceID#buildId(String, NodeType)}
             */
            private final String dest;
            private final int componentId;
        }
    }

    /**
     * Endpoint ID related functions.
     */
    public static class EndpointID {
        /**
         * @param serviceId built by {@link ServiceID#buildId(String, NodeType)}
         * @return endpoint id
         */
        public static String buildId(String serviceId, String endpointName, DetectPoint detectPoint) {
            return serviceId
                + Const.ID_SPLIT
                + encode(endpointName)
                + Const.ID_SPLIT
                + detectPoint.value();
        }

        /**
         * @return Endpoint id object decoded from {@link #buildId(String, String, DetectPoint)} result.
         */
        public static EndpointIDDefinition analysisId(String id) {
            final String[] strings = id.split(Const.ID_PARSER_SPLIT);
            if (strings.length != 3) {
                throw new UnexpectedException("Can't split endpoint id into 3 parts, " + id);
            }
            return new EndpointIDDefinition(
                decode(strings[0]),
                decode(strings[1]),
                DetectPoint.valueOf(Integer.parseInt(strings[2]))
            );
        }

        /**
         * @return the endpoint relationship string id.
         */
        public static String buildRelationId(EndpointRelationDefine define) {
            return define.sourceServiceId
                + Const.ID_SPLIT
                + encode(define.source)
                + Const.ID_SPLIT
                + define.destServiceId
                + Const.ID_SPLIT
                + encode(define.dest)
                + Const.ID_SPLIT
                + define.componentId;
        }

        /**
         * @return endpoint relation ID object decoded from {@link #buildRelationId(EndpointRelationDefine)} result
         */
        public static EndpointRelationDefine splitEndpointRelationEntityId(String entityId) {
            String[] parts = entityId.split(Const.ID_SPLIT);
            if (parts.length != 5) {
                throw new UnexpectedException("Illegal endpoint Relation entity id, " + entityId);
            }
            return new EndpointRelationDefine(
                parts[0],
                decode(parts[1]),
                parts[2],
                decode(parts[3]),
                Integer.parseInt(parts[4])
            );
        }

        @RequiredArgsConstructor
        @Getter
        public static class EndpointIDDefinition {
            /**
             * Built by {@link ServiceID#buildId(String, NodeType)}
             */
            private final String serviceId;
            private final String endpointName;
            private final DetectPoint detectPoint;
        }

        @RequiredArgsConstructor
        @Getter
        @EqualsAndHashCode
        public static class EndpointRelationDefine {
            /**
             * Built by {@link ServiceID#buildId(String, NodeType)}
             */
            private final String sourceServiceId;
            private final String source;
            /**
             * Built by {@link ServiceID#buildId(String, NodeType)}
             */
            private final String destServiceId;
            private final String dest;
            private final int componentId;
        }
    }

    /**
     * Network Address Alias ID related functions.
     */
    public static class NetworkAddressAliasDefine {
        /**
         * @return encoded network address id
         */
        public static String buildId(String name) {
            return encode(name);
        }

        /**
         * @return network address id object decoded from {@link #buildId(String)} result
         */
        public static String analysisId(String id) {
            return decode(id);
        }
    }

    /**
     * @param text normal literal string
     * @return Base74 encoded UTF-8 string
     */
    private static String encode(String text) {
        return new String(Base64.getEncoder().encode(text.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    /**
     * @param base64text Base74 encoded UTF-8 string
     * @return normal literal string
     */
    private static String decode(String base64text) {
        return new String(Base64.getDecoder().decode(base64text), StandardCharsets.UTF_8);
    }
}
