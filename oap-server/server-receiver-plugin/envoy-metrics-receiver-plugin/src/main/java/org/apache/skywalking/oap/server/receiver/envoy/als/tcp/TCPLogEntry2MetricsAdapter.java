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

package org.apache.skywalking.oap.server.receiver.envoy.als.tcp;

import io.envoyproxy.envoy.data.accesslog.v3.AccessLogCommon;
import io.envoyproxy.envoy.data.accesslog.v3.ConnectionProperties;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import io.envoyproxy.envoy.data.accesslog.v3.TCPAccessLogEntry;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.network.common.v3.DetectPoint;
import org.apache.skywalking.apm.network.servicemesh.v3.TCPServiceMeshMetric;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;

import static org.apache.skywalking.oap.server.receiver.envoy.als.LogEntry2MetricsAdapter.formatAsLong;
import static org.apache.skywalking.oap.server.receiver.envoy.als.LogEntry2MetricsAdapter.parseInternalErrorCode;
import static org.apache.skywalking.oap.server.receiver.envoy.als.LogEntry2MetricsAdapter.parseTLS;

/**
 * Adapt {@link HTTPAccessLogEntry} objects to {@link TCPServiceMeshMetric} builders.
 */
@RequiredArgsConstructor
public class TCPLogEntry2MetricsAdapter {

    /**
     * The access log entry that is to be adapted into metrics builders.
     */
    protected final TCPAccessLogEntry entry;

    protected final ServiceMetaInfo sourceService;

    protected final ServiceMetaInfo targetService;

    /**
     * Adapt the {@code entry} into a downstream metrics {@link TCPServiceMeshMetric.Builder}.
     *
     * @return the {@link TCPServiceMeshMetric.Builder} adapted from the given entry.
     */
    public TCPServiceMeshMetric.Builder adaptToDownstreamMetrics() {
        final AccessLogCommon properties = entry.getCommonProperties();
        final long startTime = formatAsLong(properties.getStartTime());
        final long duration = formatAsLong(properties.getTimeToLastDownstreamTxByte());

        return adaptCommonPart()
            .setStartTime(startTime)
            .setEndTime(startTime + duration)
            .setDetectPoint(DetectPoint.server);
    }

    /**
     * Adapt the {@code entry} into an upstream metrics {@link TCPServiceMeshMetric.Builder}.
     *
     * @return the {@link TCPServiceMeshMetric.Builder} adapted from the given entry.
     */
    public TCPServiceMeshMetric.Builder adaptToUpstreamMetrics() {
        final AccessLogCommon properties = entry.getCommonProperties();
        final long startTime = formatAsLong(properties.getStartTime());
        final long outboundStartTime = startTime + formatAsLong(properties.getTimeToFirstUpstreamTxByte());
        final long outboundEndTime = startTime + formatAsLong(properties.getTimeToLastUpstreamRxByte());

        return adaptCommonPart()
            .setStartTime(outboundStartTime)
            .setEndTime(outboundEndTime)
            .setDetectPoint(DetectPoint.client);
    }

    public TCPServiceMeshMetric.Builder adaptCommonPart() {
        final AccessLogCommon properties = entry.getCommonProperties();
        final ConnectionProperties connectionProperties = entry.getConnectionProperties();
        final String tlsMode = parseTLS(properties.getTlsProperties());
        final String internalErrorCode = parseInternalErrorCode(properties.getResponseFlags());
        final long internalRequestLatencyNanos = properties.getTimeToFirstUpstreamTxByte().getNanos();
        final long internalResponseLatencyNanos =
            properties.getTimeToLastDownstreamTxByte().getNanos()
                - properties.getTimeToFirstUpstreamRxByte().getNanos();

        final TCPServiceMeshMetric.Builder builder =
            TCPServiceMeshMetric
                .newBuilder()
                .setTlsMode(tlsMode)
                .setReceivedBytes(connectionProperties.getReceivedBytes())
                .setSentBytes(connectionProperties.getSentBytes())
                .setInternalErrorCode(internalErrorCode)
                .setInternalRequestLatencyNanos(internalRequestLatencyNanos)
                .setInternalResponseLatencyNanos(internalResponseLatencyNanos);

        Optional.ofNullable(sourceService)
                .map(ServiceMetaInfo::getServiceName)
                .ifPresent(builder::setSourceServiceName);
        Optional.ofNullable(sourceService)
                .map(ServiceMetaInfo::getServiceInstanceName)
                .ifPresent(builder::setSourceServiceInstance);
        Optional.ofNullable(targetService)
                .map(ServiceMetaInfo::getServiceName)
                .ifPresent(builder::setDestServiceName);
        Optional.ofNullable(targetService)
                .map(ServiceMetaInfo::getServiceInstanceName)
                .ifPresent(builder::setDestServiceInstance);

        return builder;
    }

}
