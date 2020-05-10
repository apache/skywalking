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
        } else if (DefaultScopeDefine.inServiceRelationCatalog(scope)) {
            final String serviceRelationId = meta.getId();
            final IDManager.ServiceID.ServiceRelationDefine serviceRelationDefine = IDManager.ServiceID.analysisRelationId(
                serviceRelationId);
            final IDManager.ServiceID.ServiceIDDefinition sourceIdDefinition = IDManager.ServiceID.analysisId(
                serviceRelationDefine.getSourceId());
            final IDManager.ServiceID.ServiceIDDefinition destIdDefinition = IDManager.ServiceID.analysisId(
                serviceRelationDefine.getDestId());
            return sourceIdDefinition.getName() + " to " + destIdDefinition.getName();
        } else if (DefaultScopeDefine.inServiceInstanceRelationCatalog(scope)) {
            final String instanceRelationId = meta.getId();
            final IDManager.ServiceInstanceID.ServiceInstanceRelationDefine serviceRelationDefine = IDManager.ServiceInstanceID.analysisRelationId(
                instanceRelationId);
            final IDManager.ServiceInstanceID.InstanceIDDefinition sourceIdDefinition = IDManager.ServiceInstanceID.analysisId(
                serviceRelationDefine.getSourceId());
            final IDManager.ServiceID.ServiceIDDefinition sourceServiceId = IDManager.ServiceID.analysisId(
                sourceIdDefinition.getServiceId());
            final IDManager.ServiceInstanceID.InstanceIDDefinition destIdDefinition = IDManager.ServiceInstanceID.analysisId(
                serviceRelationDefine.getDestId());
            final IDManager.ServiceID.ServiceIDDefinition destServiceId = IDManager.ServiceID.analysisId(
                destIdDefinition.getServiceId());
            return sourceIdDefinition.getName() + " of " + sourceServiceId.getName()
                + " to " + destIdDefinition.getName() + " of " + destServiceId.getName();
        } else if (DefaultScopeDefine.inEndpointRelationCatalog(scope)) {
            final String endpointRelationId = meta.getId();
            final IDManager.EndpointID.EndpointRelationDefine endpointRelationDefine = IDManager.EndpointID.analysisRelationId(
                endpointRelationId);
            final IDManager.ServiceID.ServiceIDDefinition sourceService = IDManager.ServiceID.analysisId(
                endpointRelationDefine.getSourceServiceId());
            final IDManager.ServiceID.ServiceIDDefinition destService = IDManager.ServiceID.analysisId(
                endpointRelationDefine.getDestServiceId());
            return endpointRelationDefine.getSource() + " in " + sourceService.getName()
                + " to " + endpointRelationDefine.getDest() + " in " + destService.getName();
        } else if (scope == DefaultScopeDefine.ALL) {
            return "";
        } else {
            return null;
        }
    }
}
