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
import io.envoyproxy.envoy.data.accesslog.v3.AccessLogCommon;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import io.envoyproxy.envoy.data.accesslog.v3.HTTPRequestProperties;
import io.envoyproxy.envoy.data.accesslog.v3.ResponseFlags;
import io.envoyproxy.envoy.data.accesslog.v3.TLSProperties;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.network.common.v3.DetectPoint;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.servicemesh.v3.HTTPServiceMeshMetric;
import org.apache.skywalking.apm.network.servicemesh.v3.Protocol;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.skywalking.oap.server.core.Const.TLS_MODE.M_TLS;
import static org.apache.skywalking.oap.server.core.Const.TLS_MODE.NON_TLS;
import static org.apache.skywalking.oap.server.core.Const.TLS_MODE.TLS;

/**
 * Adapt {@link HTTPAccessLogEntry} objects to {@link HTTPServiceMeshMetric} builders.
 */
@RequiredArgsConstructor
public class LogEntry2MetricsAdapter {
    /**
     * An internal latency the access log cannot measure. The internal latency metrics in {@code mesh.oal} leave it
     * out with {@code filter(... >= 0)}.
     */
    public static final long UNKNOWN_INTERNAL_LATENCY = -1;

    private static final String VIA_UPSTREAM = "via_upstream";

    /**
     * The access log entry that is to be adapted into metrics builders.
     */
    protected final HTTPAccessLogEntry entry;

    protected final ServiceMetaInfo sourceService;

    protected final ServiceMetaInfo targetService;

    /**
     * Adapt the {@code entry} into a downstream metrics {@link HTTPServiceMeshMetric.Builder}.
     *
     * @return the {@link HTTPServiceMeshMetric.Builder} adapted from the given entry.
     */
    public HTTPServiceMeshMetric.Builder adaptToDownstreamMetrics() {
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
     * Adapt the {@code entry} into an upstream metrics {@link HTTPServiceMeshMetric.Builder}.
     *
     * @return the {@link HTTPServiceMeshMetric.Builder} adapted from the given entry.
     */
    public HTTPServiceMeshMetric.Builder adaptToUpstreamMetrics() {
        final AccessLogCommon properties = entry.getCommonProperties();
        final long startTime = formatAsLong(properties.getStartTime());
        final long outboundStartTime = startTime + formatAsLong(properties.getTimeToFirstUpstreamTxByte());
        final long outboundEndTime = startTime + formatAsLong(properties.getTimeToLastUpstreamRxByte());

        final HTTPServiceMeshMetric.Builder builder = adaptCommonPart();
        // For client side call, status needs to be overridden because 4xx http status codes
        // are considered as errors too.
        final boolean status = builder.getResponseCode() < 400;

        return builder
            .setStartTime(outboundStartTime)
            .setEndTime(outboundEndTime)
            .setLatency((int) Math.max(1L, outboundEndTime - outboundStartTime))
            .setStatus(status)
            .setDetectPoint(DetectPoint.client);
    }

    public HTTPServiceMeshMetric.Builder adaptCommonPart() {
        final AccessLogCommon properties = entry.getCommonProperties();
        final String endpoint = endpoint();
        int responseCode = entry.getResponse().getResponseCode().getValue();
        responseCode = responseCode > 0 ? responseCode : 200;
        final boolean status = responseCode < 500;
        final Protocol protocol = requestProtocol(entry.getRequest());
        final String tlsMode = parseTLS(properties.getTlsProperties());
        final String internalErrorCode = parseInternalErrorCode(properties.getResponseFlags());
        // The upstream timings count from the start of the downstream request, so after a retry or a hedged
        // attempt the first upstream byte can include the earlier attempts and the backoff between them.
        final long internalRequestLatencyNanos =
            properties.hasTimeToFirstUpstreamTxByte() && properties.getUpstreamRequestAttemptCount() <= 1
                ? toNanos(properties.getTimeToFirstUpstreamTxByte())
                : UNKNOWN_INTERNAL_LATENCY;
        // Envoy leaves out a timing that never happened, and an absent Duration reads as zero. Its own replies, such
        // as the 504 of a timeout during a retry backoff, can carry an earlier attempt's upstream timing, so only a
        // response whose details say it came via the upstream is measured. A later reset or timeout replaces those
        // details, so such a response is left out too. An Envoy that reports no details leaves them empty.
        final String responseCodeDetails = entry.getResponse().getResponseCodeDetails();
        final long internalResponseLatencyNanos =
            (responseCodeDetails.isEmpty() || VIA_UPSTREAM.equals(responseCodeDetails))
                && properties.hasTimeToFirstUpstreamRxByte() && properties.hasTimeToFirstDownstreamTxByte()
                ? toNanos(properties.getTimeToFirstDownstreamTxByte())
                    - toNanos(properties.getTimeToFirstUpstreamRxByte())
                : UNKNOWN_INTERNAL_LATENCY;

        final HTTPServiceMeshMetric.Builder builder =
            HTTPServiceMeshMetric
                .newBuilder()
                .setEndpoint(endpoint)
                .setResponseCode(Math.toIntExact(responseCode))
                .setStatus(status)
                .setProtocol(protocol)
                .setTlsMode(tlsMode)
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

        Optional
            .ofNullable(sourceService)
            .map(ServiceMetaInfo::getTags)
            .ifPresent(tags -> {
                tags.forEach(p -> {
                    builder.addSourceInstanceProperties(
                        KeyStringValuePair.newBuilder().setKey(p.getKey()).setValue(p.getValue()));
                });
            });

        Optional
            .ofNullable(targetService)
            .map(ServiceMetaInfo::getTags)
            .ifPresent(tags -> {
                tags.forEach(p -> {
                    builder.addDestInstanceProperties(
                        KeyStringValuePair.newBuilder().setKey(p.getKey()).setValue(p.getValue()));
                });
            });

        return builder;
    }

    protected String endpoint() {
        if (!entry.hasRequest()) {
            return "/";
        }
        final HTTPRequestProperties request = entry.getRequest();
        final String method = request.getRequestMethod().name();
        return method + ":" + request.getPath();
    }

    public static long formatAsLong(final Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()).toEpochMilli();
    }

    public static long formatAsLong(final Duration duration) {
        return Instant.ofEpochSecond(duration.getSeconds(), duration.getNanos()).toEpochMilli();
    }

    public static long toNanos(final Duration duration) {
        return TimeUnit.SECONDS.toNanos(duration.getSeconds()) + duration.getNanos();
    }

    public static Protocol requestProtocol(final HTTPRequestProperties request) {
        if (request == null) {
            return Protocol.HTTP;
        }
        final String scheme = request.getScheme();
        if (scheme.startsWith("http")) {
            return Protocol.HTTP;
        }
        return Protocol.gRPC;
    }

    public static String parseTLS(final TLSProperties properties) {
        if (properties == null) {
            return NON_TLS;
        }
        TLSProperties.CertificateProperties lp = properties.getLocalCertificateProperties();
        if (isNullOrEmpty(lp.getSubject()) && !hasSAN(lp.getSubjectAltNameList())) {
            return NON_TLS;
        }
        TLSProperties.CertificateProperties pp = properties.getPeerCertificateProperties();
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
    public static String parseInternalErrorCode(final ResponseFlags responseFlags) {
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
