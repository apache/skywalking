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

package org.apache.skywalking.oap.server.fetcher.cilium.handler;

import com.google.protobuf.util.Timestamps;
import io.cilium.api.flow.DNS;
import io.cilium.api.flow.Endpoint;
import io.cilium.api.flow.Flow;
import io.cilium.api.flow.HTTP;
import io.cilium.api.flow.Kafka;
import io.cilium.api.flow.L7FlowType;
import io.cilium.api.flow.TrafficDirection;
import io.cilium.api.flow.Verdict;
import io.cilium.api.observer.GetFlowsRequest;
import io.cilium.api.observer.GetFlowsResponse;
import io.cilium.api.observer.ObserverGrpc;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.source.CiliumEndpointRelation;
import org.apache.skywalking.oap.server.core.source.CiliumEndpoint;
import org.apache.skywalking.oap.server.core.source.CiliumMetrics;
import org.apache.skywalking.oap.server.core.source.CiliumServiceInstanceRelation;
import org.apache.skywalking.oap.server.core.source.CiliumServiceInstance;
import org.apache.skywalking.oap.server.core.source.CiliumService;
import org.apache.skywalking.oap.server.core.source.CiliumServiceRelation;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.fetcher.cilium.CiliumFetcherConfig;
import org.apache.skywalking.oap.server.fetcher.cilium.nodes.CiliumNode;
import org.apache.skywalking.oap.server.fetcher.cilium.nodes.CiliumNodeUpdateListener;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.RunnableWithExceptionProtection;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CiliumFlowListener implements CiliumNodeUpdateListener {
    private static final Executor EXECUTOR = Executors.newCachedThreadPool();
    private final SourceReceiver sourceReceiver;
    private final Integer retrySecond;
    private final boolean convertClientAsServerTraffic;

    public static final Layer SERVICE_LAYER = Layer.CILIUM_SERVICE;

    public CiliumFlowListener(ModuleManager moduleManager, CiliumFetcherConfig config) {
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        this.retrySecond = config.getFetchFailureRetrySecond();
        this.convertClientAsServerTraffic = config.isConvertClientAsServerTraffic();
    }

    @Override
    public void onNodeAdded(CiliumNode node) {
        final String address = node.getAddress();
        EXECUTOR.execute(new RunnableWithExceptionProtection(() -> {
            final ObserverGrpc.ObserverBlockingStub stub = node.getObserverStub();
            if (stub == null) {
                return;
            }
            final Iterator<GetFlowsResponse> flows = stub.getFlows(
                GetFlowsRequest.newBuilder().setSince(Timestamps.now()).setFollow(true).build());
            final Thread thread = Thread.currentThread();
            node.addingCloseable(thread::interrupt);
            flows.forEachRemaining(flow -> {
                switch (flow.getResponseTypesCase()) {
                    case FLOW:
                        log.debug("Detect flow data: address: {}, flow: {}", address, flow.getFlow());
                        handleFlow(node, flow.getFlow());
                        break;
                    case LOST_EVENTS:
                        log.warn("Detected lost events, address: {}, events: {}", address, flow.getLostEvents());
                        break;
                    case NODE_STATUS:
                        log.debug("Detected node status, address: {}, status: {}", address, flow.getNodeStatus());
                        break;
                }
            });
        }, t -> {
            if (t instanceof InterruptedException || (t.getCause() != null && t.getCause() instanceof InterruptedException)) {
                log.debug("detected the node have been closed: {}, stopping to get flows", node.getAddress());
                return;
            }
            log.error("Failed to fetch flows from Cilium node: {}, will retry after {} seconds.", node.getAddress(), this.retrySecond, t);
            try {
                TimeUnit.SECONDS.sleep(this.retrySecond);
            } catch (InterruptedException e) {
                log.error("Failed to sleep for {} seconds.", this.retrySecond, e);
                return;
            }

            onNodeAdded(node);
        }));
    }

    @Override
    public void onNodeDelete(CiliumNode node) {
    }

    protected void handleFlow(CiliumNode node, Flow flow) {
        // if no source or no destination, then ignore this flow
        if (shouldIgnoreFlow(node, flow)) {
            return;
        }

        flow = convertTraffic(node, flow);

        final ServiceMetadata sourceMetadata = new ServiceMetadata(flow.getSource());
        final ServiceMetadata destMetadata = new ServiceMetadata(flow.getDestination());
        DetectPoint detectPoint = parseDetectPoint(flow);
        if (convertClientAsServerTraffic) {
            // if the client traffic is converted as server traffic, then the detect point should be only be server side
            detectPoint = DetectPoint.SERVER;
        }
        log.debug("ready to building cilium traffic from {}{} -> {}{}, flow: {}, type: {}",
            detectPoint.equals(DetectPoint.CLIENT) ? "*" : "",
            sourceMetadata.getServiceName(),
            detectPoint.equals(DetectPoint.SERVER) ? "*" : "",
            destMetadata.getServiceName(), parseDirectionString(flow),
            flow.getType());

        switch (flow.getType()) {
            case L3_L4:
                buildL34Metrics(node, flow, sourceMetadata, destMetadata, detectPoint);
                break;
            case L7:
                buildL7Metrics(node, flow, sourceMetadata, destMetadata, detectPoint);
                break;
        }
    }

    protected Flow convertTraffic(CiliumNode node, Flow flow) {
        final Flow.Builder builder = flow.toBuilder();
        // if the flow is reply traffic
        if (flow.getIsReply().getValue()) {
            // need to convert the traffic direction
            // the reply flow traffic is opposite to the original flow
            builder.setTrafficDirection(convertDirection(flow.getTrafficDirection()));

            // correct the source and destination
            // when the flow is reply, the source and destination should be exchanged to the client -> server
            final Endpoint source = flow.getSource();
            final Endpoint dest = flow.getDestination();
            builder.setSource(dest);
            builder.setDestination(source);
        }

        if (convertClientAsServerTraffic) {
            // convert the traffic direction
            builder.setTrafficDirection(convertDirection(builder.getTrafficDirection()));
        }

        return builder.build();
    }

    protected TrafficDirection convertDirection(TrafficDirection direction) {
        switch (direction) {
            case INGRESS:
                return TrafficDirection.EGRESS;
            case EGRESS:
                return TrafficDirection.INGRESS;
        }
        return direction;
    }

    private void buildL34Metrics(CiliumNode node, Flow flow, ServiceMetadata sourceMetadata, ServiceMetadata destMetadata, DetectPoint detectPoint) {
        ServiceMetadata currentService = detectPoint.equals(DetectPoint.CLIENT) ? sourceMetadata : destMetadata;
        List<? extends CiliumMetrics> metrics = Arrays.asList(
            buildService(node, flow, currentService, detectPoint), buildServiceRelation(node, flow, sourceMetadata, destMetadata, detectPoint),
            buildServiceInstance(node, flow, currentService, detectPoint), buildServiceInstanceRelation(node, flow, sourceMetadata, destMetadata, detectPoint));

        metrics.forEach(metric -> {
            setBasicInfo(metric, flow, CiliumMetrics.TYPE_TCP);

            sourceReceiver.receive(metric);
        });
    }

    private void buildL7Metrics(CiliumNode node, Flow flow, ServiceMetadata sourceMetadata, ServiceMetadata destMetadata, DetectPoint detectPoint) {
        switch (flow.getL7().getRecordCase()) {
            case HTTP:
                buildHttpMetrics(node, flow, sourceMetadata, destMetadata, detectPoint, flow.getL7().getHttp());
                break;
            case DNS:
                buildDnsMetrics(node, flow, sourceMetadata, destMetadata, detectPoint, flow.getL7().getDns());
                break;
            case KAFKA:
                buildKafkaMetrics(node, flow, sourceMetadata, destMetadata, detectPoint, flow.getL7().getKafka());
                break;
        }
    }

    private void buildKafkaMetrics(CiliumNode node, Flow flow, ServiceMetadata sourceMetadata, ServiceMetadata destMetadata, DetectPoint detectPoint, Kafka kafka) {
        // only acknowledge the response flow
        if (flow.getL7().getType() != L7FlowType.RESPONSE) {
            return;
        }
        boolean success = kafka.getErrorCode() == 0;
        String endpoint = "Kafka/" + kafka.getTopic() + "/" + kafka.getApiKey();
        List<CiliumMetrics> metrics = buildingL7Metrics(node, flow, sourceMetadata, destMetadata, detectPoint, endpoint);

        metrics.stream().filter(Objects::nonNull).forEach(metric -> {
            setBasicInfo(metric, flow, CiliumMetrics.TYPE_KAFKA);
            metric.setSuccess(success);
            metric.setDuration(flow.getL7().getLatencyNs());

            metric.setKafka(new CiliumMetrics.KafkaMetrics());
            metric.getKafka().setErrorCode(kafka.getErrorCode());
            metric.getKafka().setErrorCodeString(KafkaCodes.ERROR_CODES.getOrDefault(kafka.getErrorCode(), "UNKNOWN"));
            metric.getKafka().setApiVersion(kafka.getApiVersion());
            metric.getKafka().setApiKey(kafka.getApiKey());
            metric.getKafka().setCorrelationId(kafka.getCorrelationId());
            metric.getKafka().setTopic(kafka.getTopic());

            sourceReceiver.receive(metric);
        });
    }

    private void buildDnsMetrics(CiliumNode node, Flow flow, ServiceMetadata sourceMetadata, ServiceMetadata destMetadata, DetectPoint detectPoint, DNS dns) {
        // only acknowledge the response flow
        if (flow.getL7().getType() != L7FlowType.RESPONSE) {
            return;
        }
        boolean success = dns.getRcode() == 0;
        String endpoint = "DNS/" + (dns.getQtypesCount() > 0 ? dns.getQtypesList().get(0) : "UNKNOWN");
        List<CiliumMetrics> metrics = buildingL7Metrics(node, flow, sourceMetadata, destMetadata, detectPoint, endpoint);

        metrics.stream().filter(Objects::nonNull).forEach(metric -> {
            setBasicInfo(metric, flow, CiliumMetrics.TYPE_DNS);
            metric.setSuccess(success);
            metric.setDuration(flow.getL7().getLatencyNs());

            metric.setDns(new CiliumMetrics.DNSMetrics());
            metric.getDns().setDomain(dns.getQuery());
            metric.getDns().setQueryType(dns.getQtypesCount() > 0 ? dns.getQtypesList().get(0) : "UNKNOWN");
            metric.getDns().setRcode(dns.getRcode());
            metric.getDns().setRcodeString(DNSCodes.RETURN_CODES.getOrDefault(dns.getRcode(), "UNKNOWN"));
            metric.getDns().setTtl(dns.getTtl());
            metric.getDns().setIpCount(dns.getIpsCount());

            sourceReceiver.receive(metric);
        });
    }

    private void buildHttpMetrics(CiliumNode node, Flow flow, ServiceMetadata sourceMetadata, ServiceMetadata destMetadata, DetectPoint detectPoint, HTTP http) {
        // if the http code is 0, then ignore this flow, it should be request
        if (http.getCode() == 0) {
            return;
        }
        final URL url;
        try {
            url = new URL(http.getUrl());
        } catch (MalformedURLException e) {
            log.warn("Failed to parse the URL: {} from {} -> {}", http.getUrl(),
                sourceMetadata.getServiceInstanceName(), destMetadata.getServiceInstanceName(), e);
            return;
        }
        String endpointName = http.getMethod() + ":" + url.getPath();
        final boolean httpSuccess = parseHTTPSuccess(flow, http);
        List<CiliumMetrics> metrics = buildingL7Metrics(node, flow, sourceMetadata, destMetadata, detectPoint, endpointName);

        metrics.stream().filter(Objects::nonNull).forEach(metric -> {
            setBasicInfo(metric, flow, CiliumMetrics.TYPE_HTTP);
            metric.setSuccess(httpSuccess);
            metric.setDuration(flow.getL7().getLatencyNs());

            metric.setHttp(new CiliumMetrics.HTTPMetrics());
            metric.getHttp().setUrl(http.getUrl());
            metric.getHttp().setCode(http.getCode());
            metric.getHttp().setProtocol(http.getProtocol());
            metric.getHttp().setMethod(http.getMethod());

            sourceReceiver.receive(metric);
        });
    }

    private List<CiliumMetrics> buildingL7Metrics(CiliumNode node, Flow flow, ServiceMetadata sourceMetadata, ServiceMetadata destMetadata, DetectPoint detectPoint, String endpointName) {
        ServiceMetadata currentService = detectPoint.equals(DetectPoint.CLIENT) ? sourceMetadata : destMetadata;
        return Arrays.asList(
            buildService(node, flow, currentService, detectPoint), buildServiceRelation(node, flow, sourceMetadata, destMetadata, detectPoint),
            buildServiceInstance(node, flow, currentService, detectPoint), buildServiceInstanceRelation(node, flow, sourceMetadata, destMetadata, detectPoint),
            buildEndpoint(node, flow, currentService, endpointName, detectPoint), buildEndpointRelation(node, flow, sourceMetadata, destMetadata, detectPoint, endpointName)
        );
    }

    private void setBasicInfo(CiliumMetrics metric, Flow flow, String type) {
        metric.setVerdict(parseVerdictString(flow));
        metric.setType(type);
        metric.setDirection(parseDirectionString(flow));
        metric.setTimeBucket(TimeBucket.getMinuteTimeBucket(flow.getTime().getSeconds() * 1000));
        if (Verdict.DROPPED.equals(flow.getVerdict())) {
            metric.setDropReason(flow.getDropReasonDesc().toString());
        }
    }

    protected boolean shouldIgnoreEndpoint(Endpoint endpoint) {
        if (endpoint.getID() != 0) {
            return false;
        }

        return StringUtil.isEmpty(endpoint.getPodName()) || StringUtil.isEmpty(endpoint.getNamespace());
    }

    protected boolean shouldIgnoreFlow(CiliumNode node, Flow flow) {
        // must have source and destination
        if (!flow.hasSource() || !flow.hasDestination()) {
            return true;
        }
        // if the source and destination or not set, then ignore this flow
        if (shouldIgnoreEndpoint(flow.getSource()) || shouldIgnoreEndpoint(flow.getDestination())) {
            return true;
        }
        // only acknowledge the flows is forwarded or dropped
        switch (flow.getVerdict()) {
            case FORWARDED:
            case DROPPED:
                break;
            default:
                return true;
        }
        // traffic direction must be set
        if (flow.getTrafficDirection() == TrafficDirection.TRAFFIC_DIRECTION_UNKNOWN) {
            return true;
        }
        // flow type is only support for L3, L4 and L7
        switch (flow.getType()) {
            case L3_L4:
            case L7: break;
            default: return true;
        }
        // ignore the client traffic if we convert the client as server traffic
        if (this.convertClientAsServerTraffic && DetectPoint.SERVER.equals(parseDetectPoint(flow))) {
            return true;
        }
        return false;
    }

    private String parseVerdictString(Flow flow) {
        switch (flow.getVerdict()) {
            case FORWARDED:
                return CiliumMetrics.VERDICT_FORWARDED;
            case DROPPED:
                return CiliumMetrics.VERDICT_DROPPED;
        }
        return "";
    }

    private String parseDirectionString(Flow flow) {
        switch (flow.getTrafficDirection()) {
            case INGRESS:
                return CiliumMetrics.DIRECTION_INGRESS;
            case EGRESS:
                return CiliumMetrics.DIRECTION_EGRESS;
        }
        return "";
    }

    protected CiliumMetrics buildService(CiliumNode node, Flow flow, ServiceMetadata metadata, DetectPoint detectPoint) {
        final CiliumService service = new CiliumService();
        service.setServiceName(metadata.getServiceName());
        service.setLayer(SERVICE_LAYER);
        service.setDetectPoint(detectPoint);
        return service;
    }

    protected CiliumMetrics buildServiceRelation(CiliumNode node, Flow flow, ServiceMetadata source, ServiceMetadata dest, DetectPoint detectPoint) {
        final CiliumServiceRelation serviceRelation = new CiliumServiceRelation();
        serviceRelation.setSourceServiceName(source.getServiceName());
        serviceRelation.setSourceLayer(SERVICE_LAYER);
        serviceRelation.setDestServiceName(dest.getServiceName());
        serviceRelation.setDestLayer(SERVICE_LAYER);
        serviceRelation.setDetectPoint(detectPoint);
        serviceRelation.setComponentId(parseComponentId(flow));
        return serviceRelation;
    }

    protected CiliumMetrics buildServiceInstance(CiliumNode node, Flow flow, ServiceMetadata metadata, DetectPoint detectPoint) {
        final CiliumServiceInstance serviceInstance = new CiliumServiceInstance();
        serviceInstance.setServiceName(metadata.getServiceName());
        serviceInstance.setServiceInstanceName(metadata.getServiceInstanceName());
        serviceInstance.setLayer(SERVICE_LAYER);
        serviceInstance.setDetectPoint(detectPoint);
        return serviceInstance;
    }

    protected CiliumMetrics buildServiceInstanceRelation(CiliumNode node, Flow flow, ServiceMetadata source, ServiceMetadata dest, DetectPoint detectPoint) {
        final CiliumServiceInstanceRelation serviceInstanceRelation = new CiliumServiceInstanceRelation();
        serviceInstanceRelation.setSourceServiceName(source.getServiceName());
        serviceInstanceRelation.setSourceServiceInstanceName(source.getServiceInstanceName());
        serviceInstanceRelation.setSourceLayer(SERVICE_LAYER);
        serviceInstanceRelation.setDestServiceName(dest.getServiceName());
        serviceInstanceRelation.setDestServiceInstanceName(dest.getServiceInstanceName());
        serviceInstanceRelation.setDestLayer(SERVICE_LAYER);
        serviceInstanceRelation.setDetectPoint(detectPoint);
        serviceInstanceRelation.setComponentId(parseComponentId(flow));
        return serviceInstanceRelation;
    }

    protected CiliumMetrics buildEndpoint(CiliumNode node, Flow flow, ServiceMetadata source, String endpointName, DetectPoint detectPoint) {
        if (DetectPoint.CLIENT.equals(detectPoint)) {
            return null;
        }
        final CiliumEndpoint endpoint = new CiliumEndpoint();
        endpoint.setServiceName(source.getServiceName());
        endpoint.setEndpointName(endpointName);
        endpoint.setLayer(SERVICE_LAYER);
        return endpoint;
    }

    protected CiliumMetrics buildEndpointRelation(CiliumNode node, Flow flow, ServiceMetadata source, ServiceMetadata dest,
                                                  DetectPoint detectPoint, String endpointName) {
        final CiliumEndpointRelation endpointRelation = new CiliumEndpointRelation();
        endpointRelation.setSourceServiceName(source.getServiceName());
        endpointRelation.setSourceEndpointName(endpointName);
        endpointRelation.setSourceLayer(SERVICE_LAYER);
        endpointRelation.setDestServiceName(dest.getServiceName());
        endpointRelation.setDestEndpointName(endpointName);
        endpointRelation.setDestLayer(SERVICE_LAYER);
        endpointRelation.setDetectPoint(detectPoint);
        return endpointRelation;
    }

    protected boolean parseHTTPSuccess(Flow flow, HTTP http) {
        return http.getCode() < 500;
    }

    private DetectPoint parseDetectPoint(Flow flow) {
        boolean isReply = flow.getIsReply().getValue();
        if (!isReply) {
            return flow.getSource().getID() != 0 ? DetectPoint.CLIENT : DetectPoint.SERVER;
        }
        return flow.getDestination().getID() != 0 ? DetectPoint.CLIENT : DetectPoint.SERVER;
    }

    private int parseComponentId(Flow flow) {
        switch (flow.getType()) {
            case L3_L4:
                return 110;                             // For the L3/L4 flow, just use the TCP component
            case L7:
                switch (flow.getL7().getRecordCase()) {
                    case HTTP: return 49;               // HTTP
                    case DNS: return 159;               // DNS
                    case KAFKA: return 27;              // Kafka
                }
        }
        return 0;
    }

}
