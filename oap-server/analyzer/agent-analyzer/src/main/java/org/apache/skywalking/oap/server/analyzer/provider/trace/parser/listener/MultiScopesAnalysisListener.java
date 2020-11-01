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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.DBLatencyThresholdsAndWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.SpanTags;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.DatabaseSlowStatement;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.source.EndpointRelation;
import org.apache.skywalking.oap.server.core.source.RequestType;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceRelation;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import static org.apache.skywalking.oap.server.analyzer.provider.trace.parser.SpanTags.LOGIC_ENDPOINT;

/**
 * MultiScopesSpanListener includes the most segment to source(s) logic.
 *
 * This listener traverses the whole segment.
 */
@Slf4j
@RequiredArgsConstructor
public class MultiScopesAnalysisListener implements EntryAnalysisListener, ExitAnalysisListener, LocalAnalysisListener {
    private final List<SourceBuilder> entrySourceBuilders = new ArrayList<>(10);
    private final List<SourceBuilder> exitSourceBuilders = new ArrayList<>(10);
    private final List<DatabaseSlowStatement> slowDatabaseAccesses = new ArrayList<>(10);
    private final List<SourceBuilder> logicEndpointBuilders = new ArrayList<>(10);
    private final Gson gson = new Gson();
    private final SourceReceiver sourceReceiver;
    private final AnalyzerModuleConfig config;
    private final NetworkAddressAliasCache networkAddressAliasCache;
    private final NamingControl namingControl;

    @Override
    public boolean containsPoint(Point point) {
        return Point.Entry.equals(point) || Point.Exit.equals(point) || Point.Local.equals(point);
    }

    /**
     * All entry spans are transferred as the Service, Instance and Endpoint related sources. Entry spans are treated on
     * the behalf of the observability status of the service reported these spans.
     *
     * Also, when face the MQ and uninstrumented Gateways, there is different logic to generate the relationship between
     * services/instances rather than the normal RPC direct call. The reason is the same, we aren't expecting the agent
     * installed in the MQ server, and Gateway may not have suitable agent. Any uninstrumented service if they have the
     * capability to forward SkyWalking header through themselves, you could consider the uninstrumented configurations
     * to make the topology works to be a whole.
     */
    @Override
    public void parseEntry(SpanObject span, SegmentObject segmentObject) {
        if (span.getSkipAnalysis()) {
            return;
        }

        if (span.getRefsCount() > 0) {
            for (int i = 0; i < span.getRefsCount(); i++) {
                SegmentReference reference = span.getRefs(i);
                SourceBuilder sourceBuilder = new SourceBuilder(namingControl);

                if (StringUtil.isEmpty(reference.getParentEndpoint())) {
                    sourceBuilder.setSourceEndpointName(Const.USER_ENDPOINT_NAME);
                } else {
                    sourceBuilder.setSourceEndpointName(reference.getParentEndpoint());
                }

                final String networkAddressUsedAtPeer = reference.getNetworkAddressUsedAtPeer();

                if (span.getSpanLayer().equals(SpanLayer.MQ) ||
                    config.getUninstrumentedGatewaysConfig().isAddressConfiguredAsGateway(networkAddressUsedAtPeer)) {
                    sourceBuilder.setSourceServiceName(networkAddressUsedAtPeer);
                    sourceBuilder.setSourceEndpointOwnerServiceName(reference.getParentService());
                    sourceBuilder.setSourceServiceInstanceName(networkAddressUsedAtPeer);
                    sourceBuilder.setSourceNodeType(NodeType.fromSpanLayerValue(span.getSpanLayer()));
                } else {
                    sourceBuilder.setSourceServiceName(reference.getParentService());
                    sourceBuilder.setSourceServiceInstanceName(reference.getParentServiceInstance());
                    sourceBuilder.setSourceNodeType(NodeType.Normal);
                }
                sourceBuilder.setDestEndpointName(span.getOperationName());
                sourceBuilder.setDestServiceInstanceName(segmentObject.getServiceInstance());
                sourceBuilder.setDestServiceName(segmentObject.getService());
                sourceBuilder.setDestNodeType(NodeType.Normal);
                sourceBuilder.setDetectPoint(DetectPoint.SERVER);
                sourceBuilder.setComponentId(span.getComponentId());
                setPublicAttrs(sourceBuilder, span);
                entrySourceBuilders.add(sourceBuilder);
            }
        } else {
            SourceBuilder sourceBuilder = new SourceBuilder(namingControl);
            sourceBuilder.setSourceServiceName(Const.USER_SERVICE_NAME);
            sourceBuilder.setSourceServiceInstanceName(Const.USER_INSTANCE_NAME);
            sourceBuilder.setSourceEndpointName(Const.USER_ENDPOINT_NAME);
            sourceBuilder.setSourceNodeType(NodeType.User);
            sourceBuilder.setDestServiceInstanceName(segmentObject.getServiceInstance());
            sourceBuilder.setDestServiceName(segmentObject.getService());
            sourceBuilder.setDestNodeType(NodeType.Normal);
            sourceBuilder.setDestEndpointName(span.getOperationName());
            sourceBuilder.setDetectPoint(DetectPoint.SERVER);
            sourceBuilder.setComponentId(span.getComponentId());

            setPublicAttrs(sourceBuilder, span);
            entrySourceBuilders.add(sourceBuilder);
        }

        parseLogicEndpoints(span, segmentObject);
    }

    /**
     * The exit span should be transferred to the service, instance and relationships from the client side detect
     * point.
     */
    @Override
    public void parseExit(SpanObject span, SegmentObject segmentObject) {
        if (span.getSkipAnalysis()) {
            return;
        }

        SourceBuilder sourceBuilder = new SourceBuilder(namingControl);

        final String networkAddress = span.getPeer();
        if (StringUtil.isEmpty(networkAddress)) {
            return;
        }

        sourceBuilder.setSourceServiceName(segmentObject.getService());
        sourceBuilder.setSourceNodeType(NodeType.Normal);
        sourceBuilder.setSourceServiceInstanceName(segmentObject.getServiceInstance());

        final NetworkAddressAlias networkAddressAlias = networkAddressAliasCache.get(networkAddress);
        if (networkAddressAlias == null) {
            sourceBuilder.setDestServiceName(networkAddress);
            sourceBuilder.setDestServiceInstanceName(networkAddress);
            sourceBuilder.setDestNodeType(NodeType.fromSpanLayerValue(span.getSpanLayer()));
        } else {
            /*
             * If alias exists, mean this network address is representing a real service.
             */
            final IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition = IDManager.ServiceID.analysisId(
                networkAddressAlias.getRepresentServiceId());
            final IDManager.ServiceInstanceID.InstanceIDDefinition instanceIDDefinition = IDManager.ServiceInstanceID
                .analysisId(
                    networkAddressAlias.getRepresentServiceInstanceId());
            sourceBuilder.setDestServiceName(serviceIDDefinition.getName());
            /*
             * Some of the agent can not have the upstream real network address, such as https://github.com/apache/skywalking-nginx-lua.
             * Keeping dest instance name as NULL makes no instance relation generate from this exit span.
             */
            if (!config.shouldIgnorePeerIPDue2Virtual(span.getComponentId())) {
                sourceBuilder.setDestServiceInstanceName(instanceIDDefinition.getName());
            }
            sourceBuilder.setDestNodeType(NodeType.Normal);
        }

        sourceBuilder.setDetectPoint(DetectPoint.CLIENT);
        sourceBuilder.setComponentId(span.getComponentId());
        setPublicAttrs(sourceBuilder, span);
        exitSourceBuilders.add(sourceBuilder);

        if (sourceBuilder.getType().equals(RequestType.DATABASE)) {
            boolean isSlowDBAccess = false;

            DatabaseSlowStatement statement = new DatabaseSlowStatement();
            statement.setId(segmentObject.getTraceSegmentId() + "-" + span.getSpanId());
            statement.setDatabaseServiceId(
                IDManager.ServiceID.buildId(networkAddress, NodeType.Database)
            );
            statement.setLatency(sourceBuilder.getLatency());
            statement.setTimeBucket(TimeBucket.getRecordTimeBucket(span.getStartTime()));
            statement.setTraceId(segmentObject.getTraceId());
            for (KeyStringValuePair tag : span.getTagsList()) {
                if (SpanTags.DB_STATEMENT.equals(tag.getKey())) {
                    String sqlStatement = tag.getValue();
                    if (StringUtil.isEmpty(sqlStatement)) {
                        statement.setStatement("[No statement]/" + span.getOperationName());
                    } else if (sqlStatement.length() > config.getMaxSlowSQLLength()) {
                        statement.setStatement(sqlStatement.substring(0, config.getMaxSlowSQLLength()));
                    } else {
                        statement.setStatement(sqlStatement);
                    }
                } else if (SpanTags.DB_TYPE.equals(tag.getKey())) {
                    String dbType = tag.getValue();
                    DBLatencyThresholdsAndWatcher thresholds = config.getDbLatencyThresholdsAndWatcher();
                    int threshold = thresholds.getThreshold(dbType);
                    if (sourceBuilder.getLatency() > threshold) {
                        isSlowDBAccess = true;
                    }
                }
            }

            if (isSlowDBAccess) {
                slowDatabaseAccesses.add(statement);
            }
        }
    }

    private void setPublicAttrs(SourceBuilder sourceBuilder, SpanObject span) {
        long latency = span.getEndTime() - span.getStartTime();
        sourceBuilder.setTimeBucket(TimeBucket.getMinuteTimeBucket(span.getStartTime()));
        sourceBuilder.setLatency((int) latency);
        sourceBuilder.setResponseCode(Const.NONE);
        span.getTagsList().forEach(tag -> {
            if (SpanTags.STATUS_CODE.equals(tag.getKey())) {
                try {
                    sourceBuilder.setResponseCode(Integer.parseInt(tag.getValue()));
                } catch (NumberFormatException e) {
                    log.warn("span {} has illegal status code {}", span, tag.getValue());
                }
            }
            sourceBuilder.setTag(tag);
        });

        sourceBuilder.setStatus(!span.getIsError());

        switch (span.getSpanLayer()) {
            case Http:
                sourceBuilder.setType(RequestType.HTTP);
                break;
            case Database:
                sourceBuilder.setType(RequestType.DATABASE);
                break;
            default:
                sourceBuilder.setType(RequestType.RPC);
                break;
        }
    }

    @Override
    public void parseLocal(final SpanObject span, final SegmentObject segmentObject) {
        parseLogicEndpoints(span, segmentObject);
    }

    @Override
    public void build() {
        entrySourceBuilders.forEach(entrySourceBuilder -> {
            sourceReceiver.receive(entrySourceBuilder.toAll());
            sourceReceiver.receive(entrySourceBuilder.toService());
            sourceReceiver.receive(entrySourceBuilder.toServiceInstance());
            sourceReceiver.receive(entrySourceBuilder.toEndpoint());
            sourceReceiver.receive(entrySourceBuilder.toServiceRelation());
            sourceReceiver.receive(entrySourceBuilder.toServiceInstanceRelation());
            EndpointRelation endpointRelation = entrySourceBuilder.toEndpointRelation();
            /*
             * Parent endpoint could be none, because in SkyWalking Cross Process Propagation Headers Protocol v2,
             * endpoint in ref could be empty, based on that, endpoint relation maybe can't be established.
             * So, I am making this source as optional.
             *
             * Also, since 6.6.0, source endpoint could be none, if this trace begins by an internal task(local span or exit span), such as Timer,
             * rather than, normally begin as an entry span, like a RPC server side.
             */
            if (endpointRelation != null) {
                sourceReceiver.receive(endpointRelation);
            }
        });

        exitSourceBuilders.forEach(exitSourceBuilder -> {
            sourceReceiver.receive(exitSourceBuilder.toServiceRelation());

            /*
             * Some of the agent can not have the upstream real network address, such as https://github.com/apache/skywalking-nginx-lua.
             */
            final ServiceInstanceRelation serviceInstanceRelation = exitSourceBuilder.toServiceInstanceRelation();
            if (serviceInstanceRelation != null) {
                sourceReceiver.receive(serviceInstanceRelation);
            }
            if (RequestType.DATABASE.equals(exitSourceBuilder.getType())) {
                sourceReceiver.receive(exitSourceBuilder.toServiceMeta());
                sourceReceiver.receive(exitSourceBuilder.toDatabaseAccess());
            }
        });

        slowDatabaseAccesses.forEach(sourceReceiver::receive);

        logicEndpointBuilders.forEach(logicEndpointBuilder -> {
            sourceReceiver.receive(logicEndpointBuilder.toEndpoint());
        });
    }

    /**
     * Logic endpoint could be represent through an entry span or local span. It has special meaning from API
     * perspective. But it is an actual RPC call.
     */
    private void parseLogicEndpoints(final SpanObject span, final SegmentObject segmentObject) {
        span.getTagsList().forEach(tag -> {
            switch (tag.getKey()) {
                case LOGIC_ENDPOINT:
                    final JsonObject tagValue = gson.fromJson(tag.getValue(), JsonObject.class);
                    final boolean isLocalSpan = SpanType.Local.equals(span.getSpanType());
                    String logicEndpointName;
                    int latency;
                    boolean status;
                    if (isLocalSpan && tagValue.has("logic-span") && tagValue.get("logic-span").getAsBoolean()) {
                        logicEndpointName = span.getOperationName();
                        latency = (int) (span.getEndTime() - span.getStartTime());
                        status = !span.getIsError();
                    } else if (tagValue.has("name") && tagValue.has("latency") && tagValue.has("status")) {
                        logicEndpointName = tagValue.get("name").getAsString();
                        latency = tagValue.get("latency").getAsInt();
                        status = tagValue.get("status").getAsBoolean();
                    } else {
                        break;
                    }
                    SourceBuilder sourceBuilder = new SourceBuilder(namingControl);
                    sourceBuilder.setTimeBucket(TimeBucket.getMinuteTimeBucket(span.getStartTime()));
                    sourceBuilder.setDestServiceName(segmentObject.getService());
                    sourceBuilder.setDestServiceInstanceName(segmentObject.getServiceInstance());
                    sourceBuilder.setDestEndpointName(logicEndpointName);
                    sourceBuilder.setDestNodeType(NodeType.Normal);
                    sourceBuilder.setDetectPoint(DetectPoint.SERVER);
                    sourceBuilder.setLatency(latency);
                    sourceBuilder.setStatus(status);
                    sourceBuilder.setType(RequestType.LOGIC);
                    sourceBuilder.setResponseCode(Const.NONE);
                    logicEndpointBuilders.add(sourceBuilder);
                default:
                    break;
            }
        });
    }

    public static class Factory implements AnalysisListenerFactory {
        private final SourceReceiver sourceReceiver;
        private final NetworkAddressAliasCache networkAddressAliasCache;
        private final NamingControl namingControl;

        public Factory(ModuleManager moduleManager) {
            this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
            this.networkAddressAliasCache = moduleManager.find(CoreModule.NAME)
                                                         .provider()
                                                         .getService(NetworkAddressAliasCache.class);
            this.namingControl = moduleManager.find(CoreModule.NAME)
                                              .provider()
                                              .getService(NamingControl.class);
        }

        @Override
        public AnalysisListener create(ModuleManager moduleManager, AnalyzerModuleConfig config) {
            return new MultiScopesAnalysisListener(
                sourceReceiver, config, networkAddressAliasCache, namingControl);
        }
    }
}
