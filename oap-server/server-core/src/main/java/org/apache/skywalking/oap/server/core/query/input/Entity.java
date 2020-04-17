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

package org.apache.skywalking.oap.server.core.query.input;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;

/**
 * Entity represents the query entity, including service, instance, endpoint and conjecture service.
 *
 * @since 8.0.0
 */
@Setter
@Getter
public class Entity {
    /**
     * <pre>
     * 1. scope=All, no name is required.
     * 2. scope=Service, ServiceInstance and Endpoint, set neccessary serviceName/serviceInstanceName/endpointName
     * 3. Scope=ServiceRelation, ServiceInstanceRelation and EndpointRelation
     *    serviceName/serviceInstanceName/endpointName is/are the source(s)
     *    estServiceName/destServiceInstanceName/destEndpointName is/are destination(s)
     *    set necessary names of sources and destinations.
     * </pre>
     */
    private Scope scope;

    private String serviceName;
    /**
     * Normal service is the service having installed agent or metrics reported directly. Unnormal service is
     * conjectural service, usually detected by the agent.
     */
    private boolean isNormal;
    private String serviceInstanceName;
    private String endpointName;

    private String destServiceName;
    /**
     * Normal service is the service having installed agent or metrics reported directly. Unnormal service is
     * conjectural service, usually detected by the agent.
     */
    private boolean destIsNormal;
    private String destServiceInstanceName;
    private String destEndpointName;
}
