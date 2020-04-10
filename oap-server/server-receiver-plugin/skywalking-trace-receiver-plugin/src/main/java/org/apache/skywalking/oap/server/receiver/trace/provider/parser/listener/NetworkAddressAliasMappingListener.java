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

package org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.language.agent.v3.RefType;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.source.NetworkAddressAliasSetup;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.trace.provider.TraceServiceModuleConfig;

/**
 * NetworkAddressAliasMappingListener use the propagated data in the segment reference, set up the alias relationship
 * between network address and current service and instance. The alias relationship will be used in the {@link
 * MultiScopesAnalysisListener#parseExit(SpanObject, SegmentObject)} to setup the accurate target destination service
 * and instance.
 *
 * This is a key point of SkyWalking header propagation protocol.
 */
@Slf4j
public class NetworkAddressAliasMappingListener implements EntryAnalysisListener {
    private final TraceServiceModuleConfig config;
    private final SourceReceiver sourceReceiver;

    public NetworkAddressAliasMappingListener(ModuleManager moduleManager, TraceServiceModuleConfig config) {
        this.config = config;
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
    }

    @Override
    public void parseEntry(SpanObject span, SegmentObject segmentObject) {
        if (span.getSkipAnalysis()) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("service instance mapping listener parse reference");
        }
        if (!span.getSpanLayer().equals(SpanLayer.MQ)) {
            span.getRefsList().forEach(segmentReference -> {
                if (RefType.CrossProcess.equals(segmentReference.getRefType())) {
                    final String networkAddressUsedAtPeer = segmentReference.getNetworkAddressUsedAtPeer();
                    if (config.getUninstrumentedGatewaysConfig().isAddressConfiguredAsGateway(
                        networkAddressUsedAtPeer)) {
                        /*
                         * If this network address has been set as an uninstrumented gateway, no alias should be set.
                         */
                        return;
                    }
                    final NetworkAddressAliasSetup networkAddressAliasSetup = new NetworkAddressAliasSetup();
                    networkAddressAliasSetup.setAddress(networkAddressUsedAtPeer);
                    networkAddressAliasSetup.setRepresentService(segmentObject.getService());
                    networkAddressAliasSetup.setRepresentServiceNodeType(NodeType.Normal);
                    networkAddressAliasSetup.setRepresentServiceInstance(segmentObject.getServiceInstance());
                    networkAddressAliasSetup.setTimeBucket(TimeBucket.getMinuteTimeBucket(span.getStartTime()));

                    sourceReceiver.receive(networkAddressAliasSetup);
                }

            });
        }
    }

    @Override
    public void build() {
    }

    @Override
    public boolean containsPoint(Point point) {
        return Point.Entry.equals(point);
    }

    public static class Factory implements AnalysisListenerFactory {

        @Override
        public AnalysisListener create(ModuleManager moduleManager, TraceServiceModuleConfig config) {
            return new NetworkAddressAliasMappingListener(moduleManager, config);
        }
    }
}
