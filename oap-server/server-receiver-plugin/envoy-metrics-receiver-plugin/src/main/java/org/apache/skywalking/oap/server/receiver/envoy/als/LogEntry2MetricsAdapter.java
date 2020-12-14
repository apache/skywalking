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

package org.apache.skywalking.oap.server.receiver.envoy.als;

import com.google.protobuf.Duration;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.network.common.v3.DetectPoint;
import org.apache.skywalking.apm.network.servicemesh.v3.Protocol;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetric;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.skywalking.oap.server.receiver.envoy.als.ProtoMessages.findField;

/**
 * Adapt {@link Message access log entry} objects to {@link ServiceMeshMetric} builders.
 */
@RequiredArgsConstructor
public class LogEntry2MetricsAdapter {

    public static final String NON_TLS = "NONE";

    public static final String M_TLS = "mTLS";

    public static final String TLS = "TLS";

    /**
     * The access log entry that is to be adapted into metrics builders.
     */
    protected final Message logEntry;

    protected final ServiceMetaInfo sourceService;

    protected final ServiceMetaInfo targetService;

    /**
     * Adapt the {@code entry} into a downstream metrics {@link ServiceMeshMetric.Builder}.
     *
     * @return the {@link ServiceMeshMetric.Builder} adapted from the given entry.
     */
    public ServiceMeshMetric.Builder adaptToDownstreamMetrics() {
        final long startTime = formatAsLong(findField(logEntry, "common_properties.start_time", Timestamp.newBuilder().build()));
        final long duration = formatAsLong(findField(logEntry, "common_properties.time_to_last_rx_byte", Duration.newBuilder().build()));

        return adaptCommonPart()
            .setStartTime(startTime)
            .setEndTime(startTime + duration)
            .setLatency((int) Math.max(1L, duration))
            .setDetectPoint(DetectPoint.server);
    }

    /**
     * Adapt the {@code entry} into a upstream metrics {@link ServiceMeshMetric.Builder}.
     *
     * @return the {@link ServiceMeshMetric.Builder} adapted from the given entry.
     */
    public ServiceMeshMetric.Builder adaptToUpstreamMetrics() {
        final long startTime = formatAsLong(findField(logEntry, "common_properties.start_time", Timestamp.newBuilder().build()));
        final long outboundStartTime = startTime + formatAsLong(findField(logEntry, "common_properties.time_to_first_upstream_tx_byte", Duration.newBuilder().build()));
        final long outboundEndTime = startTime + formatAsLong(findField(logEntry, "common_properties.time_to_last_upstream_tx_byte", Duration.newBuilder().build()));

        return adaptCommonPart()
            .setStartTime(outboundStartTime)
            .setEndTime(outboundEndTime)
            .setLatency((int) Math.max(1L, outboundEndTime - outboundStartTime))
            .setDetectPoint(DetectPoint.client);
    }

    protected ServiceMeshMetric.Builder adaptCommonPart() {
        final String endpoint = endpoint();
        final int responseCode = ProtoMessages.<UInt32Value>findField(logEntry, "response.response_code").map(UInt32Value::getValue).orElse(200);
        final boolean status = responseCode >= 200 && responseCode < 400;
        final Protocol protocol = requestProtocol();
        final String tlsMode = parseTLS();
        final String internalErrorCode = parseInternalErrorCode(findField(logEntry, "common_properties.response_flags", null));

        final ServiceMeshMetric.Builder builder =
            ServiceMeshMetric.newBuilder()
                             .setEndpoint(endpoint)
                             .setResponseCode(Math.toIntExact(responseCode))
                             .setStatus(status)
                             .setProtocol(protocol)
                             .setTlsMode(tlsMode)
                             .setInternalErrorCode(internalErrorCode);

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

    protected String endpoint() {
        return findField(logEntry, "request.path", "/");
    }

    protected static long formatAsLong(final Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()).toEpochMilli();
    }

    protected static long formatAsLong(final Duration duration) {
        return Instant.ofEpochSecond(duration.getSeconds(), duration.getNanos()).toEpochMilli();
    }

    protected Protocol requestProtocol() {
        if (!findField(logEntry, "request").isPresent()) {
            return Protocol.HTTP;
        }
        final String scheme = findField(logEntry, "request.scheme", "");
        if (scheme.startsWith("http")) {
            return Protocol.HTTP;
        }
        return Protocol.gRPC;
    }

    protected String parseTLS() {
        if (!findField(logEntry, "common_properties.tls_properties").isPresent()) {
            return NON_TLS;
        }
        final String localCertSubject = findField(logEntry, "common_properties.tls_properties.local_certificate_properties.subject", null);
        final List<Message> localCertSubjectAltNames = findField(logEntry, "common_properties.tls_properties.local_certificate_properties.subject_alt_name", Collections.emptyList());
        final String peerCertSubject = findField(logEntry, "common_properties.tls_properties.peer_certificate_properties.subject", null);
        final List<Message> peerCertSubjectAltNames = findField(logEntry, "common_properties.tls_properties.peer_certificate_properties.subject_alt_name", Collections.emptyList());
        if (isNullOrEmpty(localCertSubject) && !hasSAN(localCertSubjectAltNames)) {
            return NON_TLS;
        }
        if (isNullOrEmpty(peerCertSubject) && !hasSAN(peerCertSubjectAltNames)) {
            return TLS;
        }
        return M_TLS;
    }

    private static final String[] INTERNAL_ERROR_CODES = new String[] {
        "failed_local_healthcheck",
        "no_healthy_upstream",
        "upstream_request_timeout",
        "local_reset",
        "upstream_remote_reset",
        "upstream_connection_failure",
        "upstream_connection_termination",
        "upstream_overflow",
        "no_route_found",
        "delay_injected",
        "fault_injected",
        "rate_limited",
        "rate_limit_service_error",
        "downstream_connection_termination",
        "upstream_retry_limit_exceeded",
        "stream_idle_timeout",
        "invalid_envoy_request_headers",
        "downstream_protocol_error"
    };

    /**
     * Refer to https://www.envoyproxy.io/docs/envoy/latest/api-v2/data/accesslog/v2/accesslog.proto#data-accesslog-v2-responseflags
     *
     * @param responseFlags in the ALS v2
     * @return empty string if no internal error code, or literal string representing the code.
     */
    protected static String parseInternalErrorCode(final Message responseFlags) {
        if (responseFlags != null) {
            for (final String internalErrorCode : INTERNAL_ERROR_CODES) {
                if (findField(responseFlags, internalErrorCode, false)) {
                    return internalErrorCode;
                }
            }
            if (!findField(responseFlags, "unauthorized_details").isPresent()) {
                return "unauthorized_details";
            }
        }
        return "";
    }

    /**
     * @param subjectAltNameList from ALS LocalCertificateProperties and PeerCertificateProperties
     * @return true is there is at least one SAN, based on URI check.
     */
    private static boolean hasSAN(List<Message> subjectAltNameList) {
        for (final Message san : subjectAltNameList) {
            // Don't check DNS for now, as it is tagged not-implemented in ALS v2
            if (!isNullOrEmpty(findField(san, "uri", ""))) {
                return true;
            }
        }
        return false;
    }
}
