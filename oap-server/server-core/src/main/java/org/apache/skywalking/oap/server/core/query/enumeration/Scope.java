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

package org.apache.skywalking.oap.server.core.query.enumeration;

import lombok.Getter;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.inEndpointCatalog;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.inEndpointRelationCatalog;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.inProcessCatalog;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.inProcessRelationCatalog;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.inServiceCatalog;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.inServiceInstanceCatalog;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.inServiceInstanceRelationCatalog;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.inServiceRelationCatalog;

/**
 * Scope in query stage represents the scope catalog. All scopes with their catalogs are defined in {@link DefaultScopeDefine}.
 * Scope IDs could be various due to different OAL/MAL input.
 * Scope catalog provides high dimension classifications for all scopes as a hierarchy structure.
 */
public enum Scope {
    /**
     * @since Deprecated from 9.0.0
     */
    @Deprecated
    All(DefaultScopeDefine.ALL),
    Service(DefaultScopeDefine.SERVICE),
    ServiceInstance(DefaultScopeDefine.SERVICE_INSTANCE),
    Endpoint(DefaultScopeDefine.ENDPOINT),
    ServiceRelation(DefaultScopeDefine.SERVICE_RELATION),
    ServiceInstanceRelation(DefaultScopeDefine.SERVICE_INSTANCE_RELATION),
    EndpointRelation(DefaultScopeDefine.ENDPOINT_RELATION),
    Process(DefaultScopeDefine.PROCESS),
    ProcessRelation(DefaultScopeDefine.PROCESS_RELATION);

    /**
     * Scope ID is defined in {@link DefaultScopeDefine}.
     */
    @Getter
    private int scopeId;

    Scope(int scopeId) {
        this.scopeId = scopeId;
    }

    public static class Finder {
        public static Scope valueOf(int scopeId) {
            if (inServiceCatalog(scopeId)) {
                return Service;
            }
            if (inServiceInstanceCatalog(scopeId)) {
                return ServiceInstance;
            }
            if (inEndpointCatalog(scopeId)) {
                return Endpoint;
            }
            if (inServiceRelationCatalog(scopeId)) {
                return ServiceRelation;
            }
            if (inServiceInstanceRelationCatalog(scopeId)) {
                return ServiceInstanceRelation;
            }
            if (inEndpointRelationCatalog(scopeId)) {
                return EndpointRelation;
            }
            if (inProcessCatalog(scopeId)) {
                return Process;
            }
            if (inProcessRelationCatalog(scopeId)) {
                return ProcessRelation;
            }
            return All;
        }
    }

}
