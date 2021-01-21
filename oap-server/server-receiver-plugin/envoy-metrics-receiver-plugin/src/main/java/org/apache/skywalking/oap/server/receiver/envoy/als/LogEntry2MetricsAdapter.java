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
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import io.envoyproxy.envoy.data.accesslog.v3.AccessLogCommon;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPRequestProperties;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPResponseProperties;
import io.envoyproxy.envoy.data.accesslog.v3.ResponseFlags;
import io.envoyproxy.envoy.data.accesslog.v3.TLSProperties;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.network.common.v3.DetectPoint;
import org.apache.skywalking.apm.network.servicemesh.v3.Protocol;
import org.apache.skywalking.apm.network.servicemesh.v3.ServiceMeshMetric;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Optional.ofNullable;

/**
 * Adapt {@link HTTPAccessLogEntry} objects to {@link ServiceMeshMetric} builders.
 */
@RequiredArgsConstructor
public class LogEntry2MetricsAdapter {

    public static final String NON_TLS = "NONE";

    public static final String M_TLS = "mTLS";

    public static final String TLS = "TLS";

    /**
     * The access log entry that is to be adapted into metrics builders.
     */
    protected final HTTPAccessLogEntry entry;

    protected final ServiceMetaInfo sourceService;

    protected final ServiceMetaInfo targetService;

    /**
     * Adapt the {@code entry} into a downstream metrics {@link ServiceMeshMetric.Builder}.
     *
     * @return the {@link ServiceMeshMetric.Builder} adapted from the given entry.
     */
    public ServiceMeshMetric.Builder adaptToDownstreamMetrics() {
        final AccessLogCommon properties = entry.getCommonProperties();
        final long startTime = formatAsLong(properties.getStartTime());
        final long duration = formatAsLong(properties.getTimeToLastDownstreamTxByte());

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
        final AccessLogCommon properties = entry.getCommonProperties();
        final long startTime = formatAsLong(properties.getStartTime());
        final long outboundStartTime = startTime + formatAsLong(properties.getTimeToFirstUpstreamTxByte());
        final long outboundEndTime = startTime + formatAsLong(properties.getTimeToLastUpstreamRxByte());

        return adaptCommonPart()
            .setStartTime(outboundStartTime)
            .setEndTime(outboundEndTime)
            .setLatency((int) Math.max(1L, outboundEndTime - outboundStartTime))
            .setDetectPoint(DetectPoint.client);
    }

    protected ServiceMeshMetric.Builder adaptCommonPart() {
        final AccessLogCommon properties = entry.getCommonProperties();
        final String endpoint = endpoint();
        final int responseCode = ofNullable(entry.getResponse()).map(HTTPResponseProperties::getResponseCode)
                                                                .map(UInt32Value::getValue)
                                                                .orElse(200);
        final boolean status = responseCode >= 200 && responseCode < 400;
        final Protocol protocol = requestProtocol(entry.getRequest());
        final String tlsMode = parseTLS(properties.getTlsProperties());
        final String internalErrorCode = parseInternalErrorCode(properties.getResponseFlags());

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
        return ofNullable(entry.getRequest()).map(HTTPRequestProperties::getPath).orElse("/");
    }

    protected static long formatAsLong(final Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()).toEpochMilli();
    }

    protected static long formatAsLong(final Duration duration) {
        return Instant.ofEpochSecond(duration.getSeconds(), duration.getNanos()).toEpochMilli();
    }

    protected static Protocol requestProtocol(final HTTPRequestProperties request) {
        if (request == null) {
            return Protocol.HTTP;
        }
        final String scheme = request.getScheme();
        if (scheme.startsWith("http")) {
            return Protocol.HTTP;
        }
        return Protocol.gRPC;
    }

    protected static String parseTLS(final TLSProperties properties) {
        if (properties == null) {
            return NON_TLS;
        }
        TLSProperties.CertificateProperties lp = Optional
            .ofNullable(properties.getLocalCertificateProperties())
            .orElse(TLSProperties.CertificateProperties.newBuilder().build());
        if (isNullOrEmpty(lp.getSubject()) && !hasSAN(lp.getSubjectAltNameList())) {
            return NON_TLS;
        }
        TLSProperties.CertificateProperties pp = Optional
            .ofNullable(properties.getPeerCertificateProperties())
            .orElse(TLSProperties.CertificateProperties.newBuilder().build());
        if (isNullOrEmpty(pp.getSubject()) && !hasSAN(pp.getSubjectAltNameList())) {
            return TLS;
        }
        return M_TLS;
    }

    /**
     * Refer to https://www.envoyproxy.io/docs/envoy/latest/api-v2/data/accesslog/v2/accesslog.proto#data-accesslog-v2-responseflags
     *
     * @param responseFlags in the ALS v2
     * @return empty string if no internal error code, or literal string representing the code.
     */
    protected static String parseInternalErrorCode(final ResponseFlags responseFlags) {
        if (responseFlags != null) {
            if (responseFlags.getFailedLocalHealthcheck()) {
                return "failed_local_healthcheck";
            } else if (responseFlags.getNoHealthyUpstream()) {
                return "no_healthy_upstream";
            } else if (responseFlags.getUpstreamRequestTimeout()) {
                return "upstream_request_timeout";
            } else if (responseFlags.getLocalReset()) {
                return "local_reset";
            } else if (responseFlags.getUpstreamConnectionFailure()) {
                return "upstream_connection_failure";
            } else if (responseFlags.getUpstreamConnectionTermination()) {
                return "upstream_connection_termination";
            } else if (responseFlags.getUpstreamOverflow()) {
                return "upstream_overflow";
            } else if (responseFlags.getNoRouteFound()) {
                return "no_route_found";
            } else if (responseFlags.getDelayInjected()) {
                return "delay_injected";
            } else if (responseFlags.getFaultInjected()) {
                return "fault_injected";
            } else if (responseFlags.getRateLimited()) {
                return "rate_limited";
            } else if (responseFlags.hasUnauthorizedDetails()) {
                return "unauthorized_details";
            } else if (responseFlags.getRateLimitServiceError()) {
                return "rate_limit_service_error";
            } else if (responseFlags.getDownstreamConnectionTermination()) {
                return "downstream_connection_termination";
            } else if (responseFlags.getUpstreamRetryLimitExceeded()) {
                return "upstream_retry_limit_exceeded";
            } else if (responseFlags.getStreamIdleTimeout()) {
                return "stream_idle_timeout";
            } else if (responseFlags.getInvalidEnvoyRequestHeaders()) {
                return "invalid_envoy_request_headers";
            } else if (responseFlags.getDownstreamProtocolError()) {
                return "downstream_protocol_error";
            }
        }
        return "";
    }

    /**
     * @param subjectAltNameList from ALS LocalCertificateProperties and PeerCertificateProperties
     * @return true is there is at least one SAN, based on URI check.
     */
    private static boolean hasSAN(List<TLSProperties.CertificateProperties.SubjectAltName> subjectAltNameList) {
        for (final TLSProperties.CertificateProperties.SubjectAltName san : subjectAltNameList) {
            // Don't check DNS for now, as it is tagged not-implemented in ALS v2
            if (!isNullOrEmpty(san.getUri())) {
                return true;
            }
        }
        return false;
    }
}
