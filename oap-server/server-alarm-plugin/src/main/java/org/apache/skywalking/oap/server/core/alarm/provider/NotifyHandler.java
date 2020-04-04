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

package org.apache.skywalking.oap.server.core.alarm.provider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.alarm.AlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.EndpointMetaInAlarm;
import org.apache.skywalking.oap.server.core.alarm.MetaInAlarm;
import org.apache.skywalking.oap.server.core.alarm.MetricsNotify;
import org.apache.skywalking.oap.server.core.alarm.ServiceInstanceMetaInAlarm;
import org.apache.skywalking.oap.server.core.alarm.ServiceMetaInAlarm;
import org.apache.skywalking.oap.server.core.alarm.provider.grpc.GRPCCallback;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.MetricsMetaInfo;
import org.apache.skywalking.oap.server.core.analysis.metrics.WithMetadata;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;

@Slf4j
public class NotifyHandler implements MetricsNotify {
    private final AlarmCore core;
    private final AlarmRulesWatcher alarmRulesWatcher;

    public NotifyHandler(AlarmRulesWatcher alarmRulesWatcher) {
        this.alarmRulesWatcher = alarmRulesWatcher;
        core = new AlarmCore(alarmRulesWatcher);
    }

    @Override
    public void notify(Metrics metrics) {
        WithMetadata withMetadata = (WithMetadata) metrics;
        MetricsMetaInfo meta = withMetadata.getMeta();
        int scope = meta.getScope();

        if (!DefaultScopeDefine.inServiceCatalog(scope) && !DefaultScopeDefine.inServiceInstanceCatalog(
            scope) && !DefaultScopeDefine
            .inEndpointCatalog(scope)) {
            return;
        }

        MetaInAlarm metaInAlarm;
        if (DefaultScopeDefine.inServiceCatalog(scope)) {
            final String serviceId = meta.getId();
            final IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition = IDManager.ServiceID.analysisId(
                serviceId);
            ServiceMetaInAlarm serviceMetaInAlarm = new ServiceMetaInAlarm();
            serviceMetaInAlarm.setMetricsName(meta.getMetricsName());
            serviceMetaInAlarm.setId(serviceId);
            serviceMetaInAlarm.setName(serviceIDDefinition.getName());
            metaInAlarm = serviceMetaInAlarm;
        } else if (DefaultScopeDefine.inServiceInstanceCatalog(scope)) {
            final String instanceId = meta.getId();
            final IDManager.ServiceInstanceID.InstanceIDDefinition instanceIDDefinition = IDManager.ServiceInstanceID.analysisId(
                instanceId);
            final IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition = IDManager.ServiceID.analysisId(
                instanceIDDefinition.getServiceId());
            ServiceInstanceMetaInAlarm instanceMetaInAlarm = new ServiceInstanceMetaInAlarm();
            instanceMetaInAlarm.setMetricsName(meta.getMetricsName());
            instanceMetaInAlarm.setId(instanceId);
            instanceMetaInAlarm.setName(instanceIDDefinition.getName() + " of " + serviceIDDefinition.getName());
            metaInAlarm = instanceMetaInAlarm;
        } else if (DefaultScopeDefine.inEndpointCatalog(scope)) {
            final String endpointId = meta.getId();
            final IDManager.EndpointID.EndpointIDDefinition endpointIDDefinition = IDManager.EndpointID.analysisId(
                endpointId);
            final IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition = IDManager.ServiceID.analysisId(
                endpointIDDefinition.getServiceId());

            EndpointMetaInAlarm endpointMetaInAlarm = new EndpointMetaInAlarm();
            endpointMetaInAlarm.setMetricsName(meta.getMetricsName());
            endpointMetaInAlarm.setId(meta.getId());
            endpointMetaInAlarm.setName(
                endpointIDDefinition.getEndpointName() + " in " + serviceIDDefinition.getName());
            metaInAlarm = endpointMetaInAlarm;
        } else {
            return;
        }

        List<RunningRule> runningRules = core.findRunningRule(meta.getMetricsName());
        if (runningRules == null) {
            return;
        }

        runningRules.forEach(rule -> rule.in(metaInAlarm, metrics));
    }

    public void init(AlarmCallback... callbacks) {
        List<AlarmCallback> allCallbacks = new ArrayList<>(Arrays.asList(callbacks));
        allCallbacks.add(new WebhookCallback(alarmRulesWatcher));
        allCallbacks.add(new GRPCCallback(alarmRulesWatcher));
        core.start(allCallbacks);
    }
}
