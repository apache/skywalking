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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.SpanTags;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.source.EndpointRelation;
import org.apache.skywalking.oap.server.core.source.RequestType;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceRelation;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

import static org.apache.skywalking.oap.server.analyzer.provider.trace.parser.SpanTags.LOGIC_ENDPOINT;

/**
 * RPCAnalysisListener detects all RPC relative statistics.
 */
@Slf4j
@RequiredArgsConstructor
public class RPCAnalysisListener extends CommonAnalysisListener implements EntryAnalysisListener, ExitAnalysisListener, LocalAnalysisListener {
    private final List<RPCTrafficSourceBuilder> callingInTraffic = new ArrayList<>(10);
    private final List<RPCTrafficSourceBuilder> callingOutTraffic = new ArrayList<>(10);
    private final List<EndpointSourceBuilder> logicEndpointBuilders = new ArrayList<>(10);
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
                callingInTraffic.add(sourceBuilder);
            }
        } else if (span.getSpanLayer() == SpanLayer.MQ && StringUtil.isNotBlank(span.getPeer())) {
            // For MQ, if there is no producer-side instrumentation, we set the existing peer as the source service name.
            RPCTrafficSourceBuilder sourceBuilder = new RPCTrafficSourceBuilder(namingControl);
            sourceBuilder.setSourceServiceName(span.getPeer());
            sourceBuilder.setSourceServiceInstanceName(span.getPeer());
            sourceBuilder.setDestEndpointName(span.getOperationName());
            sourceBuilder.setSourceLayer(Layer.VIRTUAL_MQ);
            sourceBuilder.setDestServiceInstanceName(segmentObject.getServiceInstance());
            sourceBuilder.setDestServiceName(segmentObject.getService());
            sourceBuilder.setDestLayer(identifyServiceLayer(span.getSpanLayer()));
            sourceBuilder.setDetectPoint(DetectPoint.SERVER);
            sourceBuilder.setComponentId(span.getComponentId());
            setPublicAttrs(sourceBuilder, span);
            callingInTraffic.add(sourceBuilder);
        } else {
            RPCTrafficSourceBuilder sourceBuilder = new RPCTrafficSourceBuilder(namingControl);
            sourceBuilder.setSourceServiceName(Const.USER_SERVICE_NAME);
            sourceBuilder.setSourceServiceInstanceName(Const.USER_INSTANCE_NAME);
            sourceBuilder.setSourceEndpointName(Const.USER_ENDPOINT_NAME);
            sourceBuilder.setSourceLayer(Layer.UNDEFINED);
            sourceBuilder.setDestServiceInstanceName(segmentObject.getServiceInstance());
            sourceBuilder.setDestServiceName(segmentObject.getService());
            sourceBuilder.setDestLayer(identifyServiceLayer(span.getSpanLayer()));
            sourceBuilder.setDestEndpointName(span.getOperationName());
            sourceBuilder.setDetectPoint(DetectPoint.SERVER);
            sourceBuilder.setComponentId(span.getComponentId());

            setPublicAttrs(sourceBuilder, span);
            callingInTraffic.add(sourceBuilder);
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

        RPCTrafficSourceBuilder sourceBuilder = new RPCTrafficSourceBuilder(namingControl);

        final String networkAddress = span.getPeer();
        if (StringUtil.isEmpty(networkAddress)) {
            return;
        }

        sourceBuilder.setSourceServiceName(segmentObject.getService());
        sourceBuilder.setSourceServiceInstanceName(segmentObject.getServiceInstance());
        sourceBuilder.setSourceLayer(identifyServiceLayer(span.getSpanLayer()));

        final NetworkAddressAlias networkAddressAlias = networkAddressAliasCache.get(networkAddress);
        if (networkAddressAlias == null) {
            sourceBuilder.setDestServiceName(networkAddress);
            sourceBuilder.setDestServiceInstanceName(networkAddress);
            sourceBuilder.setDestLayer(identifyRemoteServiceLayer(span.getSpanLayer(), span.getPeer()));
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
             * Some agents can not have the upstream real network address, such as https://github.com/apache/skywalking-nginx-lua.
             * Keeping dest instance name as NULL makes no instance relation generate from this exit span.
             */
            if (!config.shouldIgnorePeerIPDue2Virtual(span.getComponentId())) {
                sourceBuilder.setDestServiceInstanceName(instanceIDDefinition.getName());
            }
            sourceBuilder.setDestLayer(Layer.GENERAL);
        }

        sourceBuilder.setDetectPoint(DetectPoint.CLIENT);
        sourceBuilder.setComponentId(span.getComponentId());
        setPublicAttrs(sourceBuilder, span);
        callingOutTraffic.add(sourceBuilder);
    }

    private void setPublicAttrs(RPCTrafficSourceBuilder sourceBuilder, SpanObject span) {
        long latency = span.getEndTime() - span.getStartTime();
        sourceBuilder.setTimeBucket(TimeBucket.getMinuteTimeBucket(span.getStartTime()));
        sourceBuilder.setLatency((int) latency);
        sourceBuilder.setHttpResponseStatusCode(Const.NONE);
        span.getTagsList().forEach(tag -> {
            final String tagKey = tag.getKey();
            if (SpanTags.STATUS_CODE.equals(tagKey) || SpanTags.HTTP_RESPONSE_STATUS_CODE.equals(tagKey)) {
                try {
                    sourceBuilder.setHttpResponseStatusCode(Integer.parseInt(tag.getValue()));
                } catch (NumberFormatException e) {
                    log.warn("span {} has illegal status code {}", span, tag.getValue());
                }
            } else if (SpanTags.RPC_RESPONSE_STATUS_CODE.equals(tagKey)) {
                sourceBuilder.setRpcStatusCode(tag.getValue());
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
            case MQ:
                sourceBuilder.setType(RequestType.MQ);
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
        callingInTraffic.forEach(callingIn -> {
            callingIn.prepare();
            sourceReceiver.receive(callingIn.toService());
            sourceReceiver.receive(callingIn.toServiceInstance());
            sourceReceiver.receive(callingIn.toServiceRelation());
            sourceReceiver.receive(callingIn.toServiceInstanceRelation());
            // Service is equivalent to endpoint in FaaS (function as a service)
            // Don't generate endpoint and endpoint dependency to avoid unnecessary costs.
            if (Layer.FAAS != callingIn.getDestLayer()) {
                sourceReceiver.receive(callingIn.toEndpoint());
                EndpointRelation endpointRelation = callingIn.toEndpointRelation();
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
            }
        });

        callingOutTraffic.forEach(callingOut -> {
            callingOut.prepare();
            sourceReceiver.receive(callingOut.toServiceRelation());

            /*
             * Some of the agent can not have the upstream real network address, such as https://github.com/apache/skywalking-nginx-lua.
             */
            final ServiceInstanceRelation serviceInstanceRelation = callingOut.toServiceInstanceRelation();
            if (serviceInstanceRelation != null) {
                sourceReceiver.receive(serviceInstanceRelation);
            }
        });
        logicEndpointBuilders.forEach(logicEndpointBuilder -> {
            logicEndpointBuilder.prepare();
            sourceReceiver.receive(logicEndpointBuilder.toEndpoint());
        });
    }

    /**
     * Logic endpoint could represent through an entry span or local span. It has special meaning from API
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
                    EndpointSourceBuilder sourceBuilder = new EndpointSourceBuilder(namingControl);
                    sourceBuilder.setTimeBucket(TimeBucket.getMinuteTimeBucket(span.getStartTime()));
                    sourceBuilder.setDestServiceName(segmentObject.getService());
                    sourceBuilder.setDestServiceInstanceName(segmentObject.getServiceInstance());
                    sourceBuilder.setDestEndpointName(logicEndpointName);
                    sourceBuilder.setDestLayer(Layer.GENERAL);
                    sourceBuilder.setDetectPoint(DetectPoint.SERVER);
                    sourceBuilder.setLatency(latency);
                    sourceBuilder.setStatus(status);
                    sourceBuilder.setType(RequestType.LOGIC);
                    logicEndpointBuilders.add(sourceBuilder);
                default:
                    break;
            }
        });
    }

    /**
     * Identify the layer of remote service. Such as  ${@link Layer#DATABASE} and ${@link Layer#CACHE}.
     */
    protected Layer identifyRemoteServiceLayer(SpanLayer spanLayer, String peer) {
        switch (spanLayer) {
            case Unknown:
                return Layer.UNDEFINED;
            case Database:
                return Layer.VIRTUAL_DATABASE;
            case RPCFramework:
                return Layer.GENERAL;
            case Http:
                if (config.getUninstrumentedGatewaysConfig().isAddressConfiguredAsGateway(peer)) {
                    return Layer.VIRTUAL_GATEWAY;
                }
                return Layer.GENERAL;
            case MQ:
                return Layer.VIRTUAL_MQ;
            case Cache:
                return Layer.VIRTUAL_CACHE;
            case UNRECOGNIZED:
                return Layer.UNDEFINED;
            case FAAS:
                return Layer.FAAS;
            default:
                throw new UnexpectedException("Can't transfer to the Layer. SpanLayer=" + spanLayer);
        }
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
            return new RPCAnalysisListener(
                sourceReceiver, config, networkAddressAliasCache, namingControl);
        }
    }
}
