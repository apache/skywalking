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

package org.apache.skywalking.oap.server.exporter.provider;

import lombok.*;
import org.apache.skywalking.oap.server.core.analysis.indicator.*;
import org.apache.skywalking.oap.server.core.cache.*;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;

/**
 * @author wusheng
 */
@Setter
public class MetricFormatter {
    private ServiceInventoryCache serviceInventoryCache;
    private ServiceInstanceInventoryCache serviceInstanceInventoryCache;
    private EndpointInventoryCache endpointInventoryCache;

    protected String getEntityName(IndicatorMetaInfo meta) {
        int scope = meta.getScope();
        if (DefaultScopeDefine.inServiceCatalog(scope)) {
            return serviceInventoryCache.get(scope).getName();
        } else if (DefaultScopeDefine.inServiceInstanceCatalog(scope)) {
            return serviceInstanceInventoryCache.get(scope).getName();
        } else if (DefaultScopeDefine.inEndpointCatalog(scope)) {
            return endpointInventoryCache.get(scope).getName();
        } else if (scope == DefaultScopeDefine.ALL) {
            return "";
        } else {
            return null;
        }
    }
}
