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

package org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * Endpoint dependency could be detected by local and exit span in cross threads cases.
 * This is an add-on to {@link RPCAnalysisListener}, which is detecting all RPC relative statistics.
 *
 * @since 9.0.0
 */
@RequiredArgsConstructor
public class EndpointDepFromCrossThreadAnalysisListener extends CommonAnalysisListener implements ExitAnalysisListener, LocalAnalysisListener {
    private final SourceReceiver sourceReceiver;
    private final AnalyzerModuleConfig config;
    private final NamingControl namingControl;

    private final List<EndpointDependencyBuilder> depBuilders = new ArrayList<>(10);

    @Override
    public boolean containsPoint(final Point point) {
        return Point.Exit.equals(point) || Point.Local.equals(point);
    }

    @Override
    public void parseExit(final SpanObject span, final SegmentObject segmentObject) {
        parseRefForEndpointDependency(span, segmentObject);
    }

    @Override
    public void parseLocal(final SpanObject span, final SegmentObject segmentObject) {
        parseRefForEndpointDependency(span, segmentObject);
    }

    private void parseRefForEndpointDependency(final SpanObject span, final SegmentObject segmentObject) {
        if (span.getSkipAnalysis()) {
            return;
        }

        if (span.getRefsCount() > 0) {
            for (int i = 0; i < span.getRefsCount(); i++) {
                SegmentReference reference = span.getRefs(i);
                RPCTrafficSourceBuilder sourceBuilder = new RPCTrafficSourceBuilder(namingControl);

                if (StringUtil.isEmpty(reference.getParentEndpoint())) {
                    sourceBuilder.setSourceEndpointName(Const.USER_ENDPOINT_NAME);
                } else {
                    sourceBuilder.setSourceEndpointName(reference.getParentEndpoint());
                }

                final String networkAddressUsedAtPeer = reference.getNetworkAddressUsedAtPeer();
                boolean isMQ = span.getSpanLayer().equals(SpanLayer.MQ);
                if (isMQ || config.getUninstrumentedGatewaysConfig()
                                  .isAddressConfiguredAsGateway(networkAddressUsedAtPeer)) {
                    sourceBuilder.setSourceServiceName(networkAddressUsedAtPeer);
                    sourceBuilder.setSourceEndpointOwnerServiceName(reference.getParentService());
                    sourceBuilder.setSourceServiceInstanceName(networkAddressUsedAtPeer);
                    if (isMQ) {
                        sourceBuilder.setSourceLayer(Layer.VIRTUAL_MQ);
                    } else {
                        sourceBuilder.setSourceLayer(Layer.VIRTUAL_GATEWAY);
                    }
                    sourceBuilder.setSourceEndpointOwnerServiceLayer(Layer.GENERAL);
                } else {
                    sourceBuilder.setSourceServiceName(reference.getParentService());
                    sourceBuilder.setSourceServiceInstanceName(reference.getParentServiceInstance());
                    sourceBuilder.setSourceLayer(Layer.GENERAL);
                }
                sourceBuilder.setDestEndpointName(span.getOperationName());
                sourceBuilder.setDestServiceInstanceName(segmentObject.getServiceInstance());
                sourceBuilder.setDestServiceName(segmentObject.getService());
                sourceBuilder.setDestLayer(identifyServiceLayer(span.getSpanLayer()));
                sourceBuilder.setDetectPoint(DetectPoint.SERVER);
                sourceBuilder.setComponentId(span.getComponentId());
                setPublicAttrs(sourceBuilder, span);
                depBuilders.add(new EndpointDependencyBuilder(sourceBuilder));
            }
        }
    }

    private void setPublicAttrs(RPCTrafficSourceBuilder sourceBuilder, SpanObject span) {
        long latency = span.getEndTime() - span.getStartTime();
        sourceBuilder.setTimeBucket(TimeBucket.getMinuteTimeBucket(span.getStartTime()));
        sourceBuilder.setLatency((int) latency);
        sourceBuilder.setHttpResponseStatusCode(Const.NONE);
        span.getTagsList().forEach(sourceBuilder::setTag);
        sourceBuilder.setStatus(!span.getIsError());
    }

    @Override
    public void build() {
        depBuilders.forEach(endpointDep -> {
            // Source endpoint meta could be generated duplicated if it belongs to an entry span of downstream.
            // But if it belongs a local or exit span, then miss it in metadata.
            // Consider OAP has the capability to remove duplicate, generate it anyway.
            sourceReceiver.receive(endpointDep.toSourceEndpoint());
            sourceReceiver.receive(endpointDep.toEndpoint());
            sourceReceiver.receive(endpointDep.toEndpointRelation());
        });
    }

    public static class Factory implements AnalysisListenerFactory {
        private final SourceReceiver sourceReceiver;
        private final NamingControl namingControl;

        public Factory(ModuleManager moduleManager) {
            this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
            this.namingControl = moduleManager.find(CoreModule.NAME)
                                              .provider()
                                              .getService(NamingControl.class);
        }

        @Override
        public AnalysisListener create(final ModuleManager moduleManager, final AnalyzerModuleConfig config) {
            return new EndpointDepFromCrossThreadAnalysisListener(sourceReceiver, config, namingControl);
        }
    }
}
