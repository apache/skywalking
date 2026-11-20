/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener;

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.EndpointMeta;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceUpdate;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import static java.util.Objects.nonNull;

/**
 * Generate service, service instance and endpoint traffic by log data.
 */
@RequiredArgsConstructor
public class TrafficSinkListener implements LogSinkListener {
    private final SourceReceiver sourceReceiver;
    private final NamingControl namingControl;

    private ServiceMeta serviceMeta;
    private ServiceInstanceUpdate instanceMeta;
    private EndpointMeta endpointMeta;

    @Override
    public void build() {
        if (nonNull(serviceMeta)) {
            sourceReceiver.receive(serviceMeta);
        }
        if (nonNull(instanceMeta)) {
            sourceReceiver.receive(instanceMeta);
        }
        if (nonNull(endpointMeta)) {
            sourceReceiver.receive(endpointMeta);
        }
    }

    @Override
    public LogSinkListener parse(final LogMetadata metadata,
                                 final Object input,
                                 final ExecutionContext ctx) {
        // A registered layer here: this rule's copy of the metadata holds the layer the log arrived with, which
        // LogAnalyzer checked, or one the rule's own `layer` statement decided, which FilterSpec#doSink checked (and
        // an auto rule must decide one).
        final Layer layer = Layer.valueOf(metadata.getLayer());
        final long timeBucket = TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Minute);
        // to service traffic
        String serviceName = namingControl.formatServiceName(metadata.getService());
        String serviceId = IDManager.ServiceID.buildId(serviceName, layer.isNormal());
        serviceMeta = new ServiceMeta();
        serviceMeta.setName(namingControl.formatServiceName(metadata.getService()));
        serviceMeta.setLayer(layer);
        serviceMeta.setTimeBucket(timeBucket);
        // to service instance traffic
        if (StringUtil.isNotEmpty(metadata.getServiceInstance())) {
            instanceMeta = new ServiceInstanceUpdate();
            instanceMeta.setServiceId(serviceId);
            instanceMeta.setName(namingControl.formatInstanceName(metadata.getServiceInstance()));
            instanceMeta.setTimeBucket(timeBucket);

        }
        // to endpoint traffic
        if (StringUtil.isNotEmpty(metadata.getEndpoint())) {
            endpointMeta = new EndpointMeta();
            endpointMeta.setServiceName(serviceName);
            endpointMeta.setServiceNormal(true);
            endpointMeta.setEndpoint(namingControl.formatEndpointName(serviceName, metadata.getEndpoint()));
            endpointMeta.setTimeBucket(timeBucket);
        }
        return this;
    }

    public static class Factory implements LogSinkListenerFactory {
        private final SourceReceiver sourceReceiver;
        private final NamingControl namingControl;

        public Factory(ModuleManager moduleManager, LogAnalyzerModuleConfig moduleConfig) {
            this.sourceReceiver = moduleManager.find(CoreModule.NAME)
                                               .provider()
                                               .getService(SourceReceiver.class);
            this.namingControl = moduleManager.find(CoreModule.NAME)
                                              .provider()
                                              .getService(NamingControl.class);
        }

        @Override
        public LogSinkListener create() {
            return new TrafficSinkListener(sourceReceiver, namingControl);
        }
    }
}
