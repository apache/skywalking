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

package org.apache.skywalking.oap.server.receiver.ebpf.provider.handler;

import io.grpc.stub.StreamObserver;
import io.vavr.Tuple2;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.DetectPoint;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogConnection;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogConnectionTLSMode;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogHTTPProtocol;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogKernelAcceptOperation;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogKernelCloseOperation;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogKernelConnectOperation;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogKernelLog;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogKernelReadOperation;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogKernelWriteOperation;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogProtocolLogs;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.AccessLogProtocolType;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.ConnectionAddress;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.EBPFAccessLogDownstream;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.EBPFAccessLogMessage;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.EBPFAccessLogServiceGrpc;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.EBPFAccessLogNodeInfo;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.EBPFAccessLogNodeNetInterface;
import org.apache.skywalking.apm.network.common.v3.Instant;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.EBPFTimestamp;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.IPAddress;
import org.apache.skywalking.apm.network.ebpf.accesslog.v3.KubernetesProcessAddress;
import org.apache.skywalking.library.kubernetes.ObjectID;
import org.apache.skywalking.oap.meter.analyzer.k8s.K8sInfoRegistry;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.K8SEndpoint;
import org.apache.skywalking.oap.server.core.source.K8SEndpointRelation;
import org.apache.skywalking.oap.server.core.source.K8SMetrics;
import org.apache.skywalking.oap.server.core.source.K8SService;
import org.apache.skywalking.oap.server.core.source.K8SServiceInstance;
import org.apache.skywalking.oap.server.core.source.K8SServiceInstanceRelation;
import org.apache.skywalking.oap.server.core.source.K8SServiceRelation;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class AccessLogServiceHandler extends EBPFAccessLogServiceGrpc.EBPFAccessLogServiceImplBase {
    private final SourceReceiver sourceReceiver;
    private final NamingControl namingControl;

    private final CounterMetrics inCounter;
    private final HistogramMetrics processHistogram;
    private final CounterMetrics dropCounter;
    private final ConcurrentHashMap<String, DropDataReason> dropReasons = new ConcurrentHashMap<>();

    public AccessLogServiceHandler(ModuleManager moduleManager) {
        this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        this.namingControl = moduleManager.find(CoreModule.NAME).provider().getService(NamingControl.class);

        MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
            .provider()
            .getService(MetricsCreator.class);
        this.inCounter = metricsCreator.createCounter(
            "k8s_als_in_count", "The count of envoy ALS message received", MetricsTag.EMPTY_KEY,
            MetricsTag.EMPTY_VALUE);
        this.processHistogram = metricsCreator.createHistogramMetric(
            "k8s_als_in_latency", "The process latency of envoy ALS message received", MetricsTag.EMPTY_KEY,
            MetricsTag.EMPTY_VALUE);
        this.dropCounter = metricsCreator.createCounter(
            "k8s_als_drop_count", "The count of envoy ALS message dropped", MetricsTag.EMPTY_KEY,
            MetricsTag.EMPTY_VALUE);

        // schedule to print the drop reasons(debug log)
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::printDropReasons, 10, 10, TimeUnit.SECONDS);
    }

    @Override
    public StreamObserver<EBPFAccessLogMessage> collect(StreamObserver<EBPFAccessLogDownstream> responseObserver) {
        return new StreamObserver<>() {
            private volatile boolean isFirst = true;
            private NodeInfo node;
            private volatile ConnectionInfo connection;

            @Override
            public void onNext(EBPFAccessLogMessage logMessage) {
                final HistogramMetrics.Timer timer = processHistogram.createTimer();
                try {
                    if (isFirst || logMessage.hasNode()) {
                        isFirst = false;
                        node = new NodeInfo(logMessage.getNode());
                    }
                    if (logMessage.hasConnection()) {
                        connection = new ConnectionInfo(namingControl, node, logMessage.getConnection());
                    }

                    if (log.isDebugEnabled()) {
                        log.debug(
                            "messaged is identified from eBPF node[{}], connection[{}]. Received msg {}", node,
                                connection,
                                logMessage);
                    }

                    if (connection == null || !connection.isValid()) {
                        dropCounter.inc(logMessage.getKernelLogsCount() + (logMessage.hasProtocolLog() ? 1 : 0));
                        return;
                    }

                    for (AccessLogKernelLog accessLogKernelLog : logMessage.getKernelLogsList()) {
                        inCounter.inc();
                        dispatchKernelLog(node, connection, accessLogKernelLog);
                    }

                    if (logMessage.hasProtocolLog()) {
                        inCounter.inc();
                        dispatchProtocolLog(node, connection, logMessage.getKernelLogsList(), logMessage.getProtocolLog());
                    }
                } catch (Exception e) {
                    log.error("Access log service handler process error.", e);
                } finally {
                    timer.finish();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                log.warn("Access log service handler error.", throwable);
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(EBPFAccessLogDownstream.newBuilder().build());
                responseObserver.onCompleted();
            }
        };
    }

    private void dispatchKernelLog(NodeInfo node, ConnectionInfo connection, AccessLogKernelLog kernelLog) {
        final List<K8SMetrics> metrics = Arrays.asList(connection.toService(), connection.toServiceInstance(),
            connection.toServiceRelation(), connection.toServiceInstanceRelation());

        for (K8SMetrics metric : metrics) {
            switch (kernelLog.getOperationCase()) {
                case CONNECT:
                    final AccessLogKernelConnectOperation connect = kernelLog.getConnect();
                    metric.setTimeBucket(node.parseMinuteTimeBucket(connect.getStartTime()));
                    metric.setType(K8SMetrics.TYPE_CONNECT);
                    metric.setConnect(new K8SMetrics.Connect());
                    metric.getConnect().setDuration(getDurationFromTimestamp(node, connect.getStartTime(), connect.getEndTime()));
                    metric.getConnect().setSuccess(connect.getSuccess());
                    break;
                case ACCEPT:
                    final AccessLogKernelAcceptOperation accept = kernelLog.getAccept();
                    metric.setTimeBucket(node.parseMinuteTimeBucket(accept.getStartTime()));
                    metric.setType(K8SMetrics.TYPE_ACCEPT);
                    metric.setAccept(new K8SMetrics.Accept());
                    metric.getAccept().setDuration(getDurationFromTimestamp(node, accept.getStartTime(), accept.getEndTime()));
                    break;
                case CLOSE:
                    final AccessLogKernelCloseOperation close = kernelLog.getClose();
                    metric.setTimeBucket(node.parseMinuteTimeBucket(close.getStartTime()));
                    metric.setType(K8SMetrics.TYPE_CLOSE);
                    metric.setClose(new K8SMetrics.Close());
                    metric.getClose().setDuration(getDurationFromTimestamp(node, close.getStartTime(), close.getEndTime()));
                    metric.getClose().setSuccess(close.getSuccess());
                    break;
                case READ:
                    final AccessLogKernelReadOperation read = kernelLog.getRead();
                    metric.setTimeBucket(node.parseMinuteTimeBucket(read.getStartTime()));
                    metric.setType(K8SMetrics.TYPE_READ);
                    metric.setRead(new K8SMetrics.Read());
                    metric.getRead().setDuration(getDurationFromTimestamp(node, read.getStartTime(), read.getEndTime()));
                    metric.getRead().setSyscall(read.getSyscall().name());

                    // l4
                    final K8SMetrics.ReadL4 readL4 = new K8SMetrics.ReadL4();
                    metric.getRead().setL4(readL4);
                    readL4.setDuration(read.getL4Metrics().getTotalDuration());

                    // l3
                    final K8SMetrics.ReadL3 readL3 = new K8SMetrics.ReadL3();
                    metric.getRead().setL3(readL3);
                    readL3.setDuration(read.getL3Metrics().getTotalDuration());
                    readL3.setRcvDuration(read.getL3Metrics().getTotalRecvDuration());
                    readL3.setLocalDuration(read.getL3Metrics().getTotalLocalDuration());
                    final long totalWriteNetFilterCount = read.getL3Metrics().getTotalNetFilterCount();
                    if (totalWriteNetFilterCount > 0) {
                        readL3.setNetFilterCount(totalWriteNetFilterCount);
                        readL3.setNetFilterDuration(read.getL3Metrics().getTotalNetFilterDuration());
                    }

                    // l2
                    final K8SMetrics.ReadL2 readL2 = new K8SMetrics.ReadL2();
                    metric.getRead().setL2(readL2);
                    readL2.setNetDeviceName(node.getNetInterfaceName(read.getL2Metrics().getIfindex()));
                    readL2.setPackageCount(read.getL2Metrics().getTotalPackageCount());
                    readL2.setTotalPackageSize(read.getL2Metrics().getTotalPackageSize());
                    readL2.setPackageToQueueDuration(read.getL2Metrics().getTotalPackageToQueueDuration());
                    readL2.setRcvPackageFromQueueDuration(read.getL2Metrics().getTotalRcvPackageFromQueueDuration());
                    break;

                case WRITE:
                    final AccessLogKernelWriteOperation write = kernelLog.getWrite();
                    metric.setTimeBucket(node.parseMinuteTimeBucket(write.getStartTime()));
                    metric.setType(K8SMetrics.TYPE_WRITE);
                    metric.setWrite(new K8SMetrics.Write());
                    metric.getWrite().setDuration(getDurationFromTimestamp(node, write.getStartTime(), write.getEndTime()));
                    metric.getWrite().setSyscall(write.getSyscall().name());

                    // l4
                    final K8SMetrics.WriteL4 writeL4 = new K8SMetrics.WriteL4();
                    metric.getWrite().setL4(writeL4);
                    writeL4.setDuration(write.getL4Metrics().getTotalDuration());
                    writeL4.setTransmitPackageCount(write.getL4Metrics().getTotalTransmitPackageCount());
                    writeL4.setRetransmitPackageCount(write.getL4Metrics().getTotalRetransmitPackageCount());
                    writeL4.setTotalPackageSize(write.getL4Metrics().getTotalPackageSize());

                    // l3
                    final K8SMetrics.WriteL3 writeL3 = new K8SMetrics.WriteL3();
                    metric.getWrite().setL3(writeL3);
                    writeL3.setDuration(write.getL3Metrics().getTotalDuration());
                    writeL3.setLocalDuration(write.getL3Metrics().getTotalLocalDuration());
                    writeL3.setOutputDuration(write.getL3Metrics().getTotalOutputDuration());
                    final long totalResolveMACCount = write.getL3Metrics().getTotalResolveMACCount();
                    if (totalResolveMACCount > 0) {
                        writeL3.setResolveMACCount(totalResolveMACCount);
                        writeL3.setResolveMACDuration(write.getL3Metrics().getTotalResolveMACDuration());
                    }
                    final long totalReadNetFilterCount = write.getL3Metrics().getTotalNetFilterCount();
                    if (totalReadNetFilterCount > 0) {
                        writeL3.setNetFilterCount(totalReadNetFilterCount);
                        writeL3.setNetFilterDuration(write.getL3Metrics().getTotalNetFilterDuration());
                    }

                    // l2
                    final K8SMetrics.WriteL2 writeL2 = new K8SMetrics.WriteL2();
                    metric.getWrite().setL2(writeL2);
                    writeL2.setDuration(write.getL2Metrics().getTotalDuration());
                    writeL2.setNetworkDeviceName(node.getNetInterfaceName(write.getL2Metrics().getIfindex()));
                    final long totalEnterQueueBufferCount = write.getL2Metrics().getTotalEnterQueueBufferCount();
                    writeL2.setEnterQueueBufferCount(totalEnterQueueBufferCount);
                    writeL2.setReadySendDuration(write.getL2Metrics().getTotalReadySendDuration());
                    writeL2.setNetworkDeviceSendDuration(write.getL2Metrics().getTotalNetDeviceSendDuration());
                    break;
            }

            // send the metrics
            sourceReceiver.receive(metric);
        }
    }

    private void dispatchProtocolLog(NodeInfo node, ConnectionInfo connection,
                                     List<AccessLogKernelLog> relatedKernelLogs, AccessLogProtocolLogs protocolLog) {
        long startTimeBucket = 0;
        boolean success = false;
        long duration = 0;

        final K8SMetrics.Protocol protocol = new K8SMetrics.Protocol();
        switch (protocolLog.getProtocolCase()) {
            case HTTP:
                final AccessLogHTTPProtocol http = protocolLog.getHttp();
                success = http.getResponse().getStatusCode() < 500;

                startTimeBucket = node.parseMinuteTimeBucket(http.getStartTime());
                protocol.setType(K8SMetrics.PROTOCOL_TYPE_HTTP);
                protocol.setHttp(new K8SMetrics.ProtocolHTTP());
                protocol.setSuccess(success);

                protocol.getHttp().setLatency(getDurationFromTimestamp(node, http.getStartTime(), http.getEndTime()));
                protocol.getHttp().setUrl(http.getRequest().getPath());
                protocol.getHttp().setMethod(http.getRequest().getMethod().name());
                protocol.getHttp().setStatusCode(http.getResponse().getStatusCode());
                protocol.getHttp().setSizeOfRequestHeader(http.getRequest().getSizeOfHeadersBytes());
                protocol.getHttp().setSizeOfRequestBody(http.getRequest().getSizeOfBodyBytes());
                protocol.getHttp().setSizeOfResponseHeader(http.getResponse().getSizeOfHeadersBytes());
                protocol.getHttp().setSizeOfResponseBody(http.getResponse().getSizeOfBodyBytes());
                duration = protocol.getHttp().getLatency();
                break;
        }

        // service, service instance, service relation, service instance relation
        long finalStartTimeBucket = startTimeBucket;
        Stream.of(connection.toService(), connection.toServiceInstance(),
                connection.toServiceRelation(), connection.toServiceInstanceRelation())
            .filter(Objects::nonNull)
            .forEach(metric -> {
                metric.setType(K8SMetrics.TYPE_PROTOCOL);
                metric.setProtocol(protocol);
                metric.setTimeBucket(finalStartTimeBucket);
                sourceReceiver.receive(metric);
            });

        // endpoint, endpoint relation
        final String endpointName = buildProtocolEndpointName(connection, protocolLog);
        Stream.of(connection.toEndpoint(endpointName, success, duration), connection.toEndpointRelation(endpointName, success))
            .filter(Objects::nonNull)
            .forEach(metric -> {
                metric.setType(protocol.getType());
                metric.setHttp(protocol.getHttp());
                metric.setTimeBucket(finalStartTimeBucket);

                sourceReceiver.receive(metric);
            });
    }

    private long getDurationFromTimestamp(NodeInfo nodeInfo, EBPFTimestamp start, EBPFTimestamp end) {
        return end.getOffset().getOffset() - start.getOffset().getOffset();
    }

    public static class NodeInfo {
        private final Map<Integer, String> netInterfaces;
        private final Instant bootTime;
        @Getter
        private final String clusterName;
        private final String nodeName;

        public NodeInfo(EBPFAccessLogNodeInfo node) {
            this.nodeName = node.getName();
            this.netInterfaces = node.getNetInterfacesList().stream()
                .collect(Collectors.toMap(
                    EBPFAccessLogNodeNetInterface::getIndex, EBPFAccessLogNodeNetInterface::getName, (a, b) -> a));
            this.bootTime = node.getBootTime();
            this.clusterName = node.getClusterName();
        }

        public String getNetInterfaceName(int index) {
            return netInterfaces.get(index);
        }

        public long parseMinuteTimeBucket(EBPFTimestamp timestamp) {
            final long seconds = bootTime.getSeconds() + TimeUnit.NANOSECONDS.toSeconds(timestamp.getOffset().getOffset());
            return TimeBucket.getMinuteTimeBucket(seconds * 1000);
        }

        public String toString() {
            return String.format("name: %s, clusterName: %s, network interfaces: %s",
                nodeName, clusterName, netInterfaces);
        }
    }

    protected String buildServiceNameByAddress(NodeInfo nodeInfo, KubernetesProcessAddress address) {
        return namingControl.formatServiceName(address.getServiceName());
    }

    protected String buildServiceInstanceName(KubernetesProcessAddress address) {
        return namingControl.formatInstanceName(address.getPodName());
    }

    protected String buildProtocolEndpointName(ConnectionInfo connectionInfo, AccessLogProtocolLogs protocol) {
        final String serviceName = connectionInfo.buildLocalServiceName();
        switch (protocol.getProtocolCase()) {
            case HTTP:
                final AccessLogHTTPProtocol http = protocol.getHttp();
                return namingControl.formatEndpointName(serviceName, http.getRequest().getPath());
            default:
                return null;
        }
    }

    protected void recordIgnoreSameService(String sourceService) {
        final DropDataReason dropDataReason = dropReasons.computeIfAbsent(sourceService,
            key -> DropDataReason.buildWhenSameService(sourceService));
        dropDataReason.increaseCount();
    }

    protected void recordLessConnection(AccessLogConnection connection) {
        final DropDataReason dropDataReason = dropReasons.computeIfAbsent(
            String.format("%s_%s", buildConnectionAddressString(connection.getLocal()),
                buildConnectionAddressString(connection.getRemote())),
            key -> DropDataReason.buildWhenConnectionLoss(connection));
        dropDataReason.increaseCount();
    }

    protected String buildConnectionAddressString(ConnectionAddress address) {
        switch (address.getAddressCase()) {
            case KUBERNETES:
                return String.format("%s-%s-%s-%s",
                    address.getKubernetes().getServiceName(), address.getKubernetes().getPodName(),
                    address.getKubernetes().getContainerName(), address.getKubernetes().getProcessName());
            case IP:
                return String.format("%s", address.getIp().getHost());
            default:
                return null;
        }

    }

    protected void printDropReasons() {
        if (dropReasons.isEmpty()) {
            return;
        }
        if (!log.isDebugEnabled()) {
            dropReasons.clear();
            return;
        }

        dropReasons.keySet().forEach(key -> {
            final DropDataReason dropDataReason = dropReasons.remove(key);
            if (dropDataReason == null) {
                return;
            }
            final long count = dropDataReason.count.get();
            switch (dropDataReason.type) {
                case SameService:
                    log.debug("Ignore the same service traffic, service name: {}, trigger count: {}",
                        dropDataReason.service, count);
                    break;
                case ConnectionLoss:
                    log.debug("Ignore the connection loss, connection: {}, trigger count: {}",
                        dropDataReason.connection, count);
                    break;
            }
        });
    }

    protected KubernetesProcessAddress buildKubernetesAddressByIP(NodeInfo nodeInfo, IPAddress ipAddress) {
        final ObjectID pod = K8sInfoRegistry.getInstance().findPodByIP(ipAddress.getHost());
        if (pod == ObjectID.EMPTY) {
            return null;
        }
        final ObjectID serviceName = K8sInfoRegistry.getInstance().findService(pod.namespace(), pod.name());
        if (serviceName == ObjectID.EMPTY) {
            return null;
        }

        return buildRemoteAddress(nodeInfo, serviceName, pod);
    }

    protected KubernetesProcessAddress buildRemoteAddress(NodeInfo nodeInfo, ObjectID service, ObjectID pod) {
        String serviceName = service.name() + "." + service.namespace();
        if (StringUtil.isNotEmpty(nodeInfo.getClusterName())) {
            serviceName = nodeInfo.getClusterName() + "::" + serviceName;
        }
        return KubernetesProcessAddress.newBuilder()
            .setServiceName(serviceName)
            .setPodName(pod.name())
            .build();
    }

    private class ConnectionInfo {
        private final AccessLogConnection originalConnection;
        private final NamingControl namingControl;
        private final KubernetesProcessAddress local;
        private final KubernetesProcessAddress remote;
        private final DetectPoint role;
        private final AccessLogConnectionTLSMode tlsMode;
        private final AccessLogProtocolType protocolType;
        private final NodeInfo nodeInfo;
        @Getter
        private final boolean valid;

        public ConnectionInfo(NamingControl namingControl, NodeInfo nodeInfo, AccessLogConnection connection) {
            this.originalConnection = connection;
            this.namingControl = namingControl;
            this.local = buildAddress(nodeInfo, connection.getLocal());
            this.remote = buildAddress(nodeInfo, connection.getRemote());
            this.role = connection.getRole();
            this.tlsMode = connection.getTlsMode();
            this.nodeInfo = nodeInfo;
            this.protocolType = connection.getProtocol();
            this.valid = generateIsValid();
        }

        private KubernetesProcessAddress buildAddress(NodeInfo nodeInfo, ConnectionAddress address) {
            switch (address.getAddressCase()) {
                case KUBERNETES:
                    return address.getKubernetes();
                case IP:
                    return buildKubernetesAddressByIP(nodeInfo, address.getIp());
            }
            return null;
        }

        private boolean generateIsValid() {
            // all data must not be empty
            if (local == null || remote == null || role == null || tlsMode == null || nodeInfo == null) {
                recordLessConnection(originalConnection);
                return false;
            }
            // same service traffic should ignore
            if (Objects.equals(local.getServiceName(), remote.getServiceName())) {
                recordIgnoreSameService(local.getServiceName());
                return false;
            }
            return true;
        }

        public String buildLocalServiceName() {
            return buildServiceNameByAddress(nodeInfo, local);
        }

        public K8SService toService() {
            final K8SService service = new K8SService();
            service.setName(buildServiceNameByAddress(nodeInfo, local));
            service.setLayer(Layer.K8S_SERVICE);
            service.setDetectPoint(parseToSourceRole());
            return service;
        }

        public K8SServiceInstance toServiceInstance() {
            final K8SServiceInstance serviceInstance = new K8SServiceInstance();
            serviceInstance.setServiceName(buildServiceNameByAddress(nodeInfo, local));
            serviceInstance.setServiceInstanceName(buildServiceInstanceName(local));
            serviceInstance.setLayer(Layer.K8S_SERVICE);
            serviceInstance.setDetectPoint(parseToSourceRole());
            return serviceInstance;
        }

        public K8SServiceRelation toServiceRelation() {
            final Tuple2<KubernetesProcessAddress, KubernetesProcessAddress> tuple = convertSourceAndDestAddress();
            final String sourceServiceName = buildServiceNameByAddress(nodeInfo, tuple._1);
            final String destServiceName = buildServiceNameByAddress(nodeInfo, tuple._2);
            if (Objects.equals(sourceServiceName, destServiceName)) {
                recordIgnoreSameService(sourceServiceName);
                return null;
            }

            final K8SServiceRelation serviceRelation = new K8SServiceRelation();
            serviceRelation.setSourceServiceName(sourceServiceName);
            serviceRelation.setSourceLayer(Layer.K8S_SERVICE);

            serviceRelation.setDetectPoint(parseToSourceRole());
            serviceRelation.setComponentId(buildComponentId());
            serviceRelation.setTlsMode(tlsMode);

            serviceRelation.setDestServiceName(destServiceName);
            serviceRelation.setDestLayer(Layer.K8S_SERVICE);
            return serviceRelation;
        }

        public K8SServiceInstanceRelation toServiceInstanceRelation() {
            final Tuple2<KubernetesProcessAddress, KubernetesProcessAddress> tuple = convertSourceAndDestAddress();
            final K8SServiceInstanceRelation serviceInstanceRelation = new K8SServiceInstanceRelation();
            final String sourceServiceName = buildServiceNameByAddress(nodeInfo, tuple._1);
            final String sourceServiceInstanceName = buildServiceInstanceName(tuple._1);
            final String destServiceName = buildServiceNameByAddress(nodeInfo, tuple._2);
            final String destServiceInstanceName = buildServiceInstanceName(tuple._2);

            serviceInstanceRelation.setSourceServiceName(sourceServiceName);
            serviceInstanceRelation.setSourceServiceInstanceName(sourceServiceInstanceName);
            serviceInstanceRelation.setSourceLayer(Layer.K8S_SERVICE);

            serviceInstanceRelation.setDetectPoint(parseToSourceRole());

            serviceInstanceRelation.setDestServiceName(destServiceName);
            serviceInstanceRelation.setDestServiceInstanceName(destServiceInstanceName);
            serviceInstanceRelation.setDestLayer(Layer.K8S_SERVICE);
            return serviceInstanceRelation;
        }

        private Tuple2<KubernetesProcessAddress, KubernetesProcessAddress> convertSourceAndDestAddress() {
            KubernetesProcessAddress source, dest;
            if (role == DetectPoint.server) {
                source = this.remote;
                dest = this.local;
            } else {
                source = this.local;
                dest = this.remote;
            }
            return new Tuple2<>(source, dest);
        }

        public K8SEndpoint toEndpoint(String endpointName, boolean success, long duration) {
            // if the role is client, then ignore to generate the endpoint.
            // the endpoint only should be generated in the server side
            if (role == DetectPoint.client) {
                return null;
            }
            final K8SEndpoint endpoint = new K8SEndpoint();
            final String serviceName = buildServiceNameByAddress(nodeInfo, local);
            endpoint.setServiceName(serviceName);
            endpoint.setEndpointName(namingControl.formatEndpointName(serviceName, endpointName));
            endpoint.setLayer(Layer.K8S_SERVICE);
            endpoint.setSuccess(success);
            endpoint.setDuration(duration);
            return endpoint;
        }

        public K8SEndpointRelation toEndpointRelation(String endpointName, boolean success) {
            final Tuple2<KubernetesProcessAddress, KubernetesProcessAddress> tuple = convertSourceAndDestAddress();
            final String sourceServiceName = buildServiceNameByAddress(nodeInfo, tuple._1);
            final String destServiceName = buildServiceNameByAddress(nodeInfo, tuple._2);

            final K8SEndpointRelation endpointRelation = new K8SEndpointRelation();
            endpointRelation.setSourceServiceName(sourceServiceName);
            endpointRelation.setSourceEndpointName(namingControl.formatEndpointName(sourceServiceName, endpointName));
            endpointRelation.setSourceLayer(Layer.K8S_SERVICE);

            endpointRelation.setDetectPoint(parseToSourceRole());
            endpointRelation.setComponentId(buildComponentId());

            endpointRelation.setDestServiceName(destServiceName);
            endpointRelation.setDestEndpointName(namingControl.formatEndpointName(destServiceName, endpointName));
            endpointRelation.setDestLayer(Layer.K8S_SERVICE);
            endpointRelation.setSuccess(success);
            return endpointRelation;
        }

        private int buildComponentId() {
            boolean isTLS = tlsMode == AccessLogConnectionTLSMode.TLS;
            switch (protocolType) {
                case HTTP_1:
                case HTTP_2:
                    if (isTLS) {
                        return 129; // https
                    }
                    return 49;  // http
                case TCP:
                    if (isTLS) {
                        return 130; // tls
                    }
                    return 110; // tcp
            }
            return 0;
        }

        private org.apache.skywalking.oap.server.core.source.DetectPoint parseToSourceRole() {
            switch (role) {
                case server:
                    return org.apache.skywalking.oap.server.core.source.DetectPoint.SERVER;
                case client:
                    return org.apache.skywalking.oap.server.core.source.DetectPoint.CLIENT;
                case proxy:
                    return org.apache.skywalking.oap.server.core.source.DetectPoint.PROXY;
                default:
                    return org.apache.skywalking.oap.server.core.source.DetectPoint.UNRECOGNIZED;
            }
        }

        public String toString() {
            return String.format("local: %s, remote: %s, role: %s, tlsMode: %s, protocolType: %s",
                buildConnectionAddressString(originalConnection.getLocal()),
                buildConnectionAddressString(originalConnection.getRemote()), role, tlsMode, protocolType);
        }

    }

    private static enum DropReasonType {
        SameService,
        ConnectionLoss
    }

    private static class DropDataReason {
        private final DropReasonType type;
        private final String service;
        private final AccessLogConnection connection;
        private final AtomicLong count = new AtomicLong(0);

        private DropDataReason(DropReasonType type, String service, AccessLogConnection connection) {
            this.type = type;
            this.service = service;
            this.connection = connection;
        }

        public static DropDataReason buildWhenSameService(String service) {
            return new DropDataReason(DropReasonType.SameService, service, null);
        }

        public static DropDataReason buildWhenConnectionLoss(AccessLogConnection connection) {
            return new DropDataReason(DropReasonType.ConnectionLoss, null, connection);
        }

        public void increaseCount() {
            count.incrementAndGet();
        }
    }
}
