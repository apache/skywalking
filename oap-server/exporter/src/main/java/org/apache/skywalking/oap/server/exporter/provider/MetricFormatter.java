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

import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.metrics.MetricsMetaInfo;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;

@Setter
public class MetricFormatter {
    protected String getEntityName(MetricsMetaInfo meta) {
        int scope = meta.getScope();
        if (DefaultScopeDefine.inServiceCatalog(scope)) {
            final String serviceId = meta.getId();
            final IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition = IDManager.ServiceID.analysisId(
                serviceId);
            return serviceIDDefinition.getName();
        } else if (DefaultScopeDefine.inServiceInstanceCatalog(scope)) {
            final String instanceId = meta.getId();
            final IDManager.ServiceInstanceID.InstanceIDDefinition instanceIDDefinition = IDManager.ServiceInstanceID.analysisId(
                instanceId);
            return instanceIDDefinition.getName();
        } else if (DefaultScopeDefine.inEndpointCatalog(scope)) {
            final String endpointId = meta.getId();
            final IDManager.EndpointID.EndpointIDDefinition endpointIDDefinition = IDManager.EndpointID.analysisId(
                endpointId);
            return endpointIDDefinition.getEndpointName();
        } else if (scope == DefaultScopeDefine.ALL) {
            return "";
        } else {
            return null;
        }
    }
}
