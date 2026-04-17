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
 */

package org.apache.skywalking.oap.analyzer.ios.listener;

import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.Endpoint;
import org.apache.skywalking.oap.server.core.source.RequestType;
import org.apache.skywalking.oap.server.core.source.Service;
import org.apache.skywalking.oap.server.core.source.ServiceInstance;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.trace.OTLPSpanReader;
import org.apache.skywalking.oap.server.core.trace.SpanListener;
import org.apache.skywalking.oap.server.core.trace.SpanListenerResult;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * Extracts per-request HTTP traffic metrics from iOS URLSession spans and emits
 * OAL sources ({@link Service}, {@link ServiceInstance}, {@link Endpoint}).
 *
 * <p>Detection: resource attribute {@code os.name} is {@code iOS} or {@code iPadOS},
 * the instrumentation scope is {@code NSURLSession}, the span kind is
 * {@code CLIENT}, and the span has {@code http.method} plus
 * {@code net.peer.name} (domain name).
 *
 * <p>These keys match the current default output of the OpenTelemetry Swift
 * {@code URLSessionInstrumentation}. Its default HTTP semantic convention is
 * {@code .old}, which emits {@code net.peer.name} and {@code http.status_code}
 * rather than the stable {@code server.address} and
 * {@code http.response.status_code} keys.
 *
 * <p>Endpoint name uses the domain only ({@code net.peer.name}) to keep cardinality
 * manageable on the client side.
 *
 * <p>The existing {@code core.oal} rules automatically produce traffic metrics
 * ({@code service_cpm}, {@code service_resp_time}, {@code endpoint_cpm}, etc.)
 * from these sources, all under {@link Layer#IOS}.
 */
@Slf4j
public class IOSHTTPSpanListener implements SpanListener {
    private SourceReceiver sourceReceiver;
    private NamingControl namingControl;

    @Override
    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }

    @Override
    public void init(final ModuleManager moduleManager) {
        sourceReceiver = moduleManager.find(CoreModule.NAME)
                                      .provider()
                                      .getService(SourceReceiver.class);
        namingControl = moduleManager.find(CoreModule.NAME)
                                     .provider()
                                     .getService(NamingControl.class);
    }

    @Override
    public SpanListenerResult onOTLPSpan(final OTLPSpanReader span,
                                         final Map<String, String> resourceAttributes,
                                         final String scopeName,
                                         final String scopeVersion) {
        final String osName = resourceAttributes.get("os.name");
        if (!"iOS".equals(osName) && !"iPadOS".equals(osName)) {
            return SpanListenerResult.CONTINUE;
        }

        // OpenTelemetry Swift URLSessionInstrumentation supports three HTTP
        // semantic-convention modes: `.old` (legacy: net.peer.name / http.method /
        // http.status_code), `.stable` (server.address / http.request.method /
        // http.response.status_code), and `.httpDup` (both). We prefer the stable
        // keys and fall back to legacy so all three modes work.
        if (!"NSURLSession".equals(scopeName) || !"SPAN_KIND_CLIENT".equals(span.spanKind())) {
            return SpanListenerResult.CONTINUE;
        }

        final String httpMethod = attr(span, "http.request.method", "http.method");
        if (httpMethod == null || httpMethod.isEmpty()) {
            return SpanListenerResult.CONTINUE;
        }

        final String domain = attr(span, "server.address", "net.peer.name");
        if (domain == null || domain.isEmpty()) {
            return SpanListenerResult.CONTINUE;
        }

        final String rawServiceName = resourceAttributes.getOrDefault("service.name", "");
        if (rawServiceName.isEmpty()) {
            return SpanListenerResult.CONTINUE;
        }

        final String serviceName = namingControl.formatServiceName(rawServiceName);
        final String instanceName = resourceAttributes.getOrDefault("service.version", "");
        final String endpointName = namingControl.formatEndpointName(serviceName, domain);
        final long now = System.currentTimeMillis();
        final long endTimeMs = span.endTimeNanos() > 0 ? span.endTimeNanos() / 1_000_000 : now;
        final long startTimeMs = span.startTimeNanos() > 0 ? span.startTimeNanos() / 1_000_000 : endTimeMs;
        final long timeBucket = TimeBucket.getMinuteTimeBucket(endTimeMs);
        final int latency = (int) (endTimeMs - startTimeMs);

        final String statusCodeStr = attr(span, "http.response.status_code", "http.status_code");
        int httpStatusCode = 0;
        if (statusCodeStr != null && !statusCodeStr.isEmpty()) {
            try {
                httpStatusCode = Integer.parseInt(statusCodeStr);
            } catch (NumberFormatException ignored) {
            }
        }
        final boolean status = httpStatusCode == 0 || httpStatusCode < 400;

        // ServiceMeta — register the service with IOS layer
        final ServiceMeta serviceMeta = new ServiceMeta();
        serviceMeta.setName(serviceName);
        serviceMeta.setLayer(Layer.IOS);
        serviceMeta.setTimeBucket(timeBucket);
        sourceReceiver.receive(serviceMeta);

        // Service source — feeds OAL service_cpm, service_resp_time, service_sla, etc.
        final Service service = new Service();
        service.setName(serviceName);
        service.setLayer(Layer.IOS);
        service.setServiceInstanceName(instanceName);
        service.setEndpointName(endpointName);
        service.setLatency(latency);
        service.setStatus(status);
        service.setHttpResponseStatusCode(httpStatusCode);
        service.setType(RequestType.HTTP);
        service.setTags(Collections.emptyList());
        service.setOriginalTags(Collections.emptyMap());
        service.setTimeBucket(timeBucket);
        sourceReceiver.receive(service);

        // ServiceInstance source — feeds OAL service_instance_cpm, etc.
        if (!instanceName.isEmpty()) {
            final ServiceInstance serviceInstance = new ServiceInstance();
            serviceInstance.setName(instanceName);
            serviceInstance.setServiceName(serviceName);
            serviceInstance.setServiceLayer(Layer.IOS);
            serviceInstance.setEndpointName(endpointName);
            serviceInstance.setLatency(latency);
            serviceInstance.setStatus(status);
            serviceInstance.setHttpResponseStatusCode(httpStatusCode);
            serviceInstance.setType(RequestType.HTTP);
            serviceInstance.setTags(Collections.emptyList());
            serviceInstance.setOriginalTags(Collections.emptyMap());
            serviceInstance.setTimeBucket(timeBucket);
            sourceReceiver.receive(serviceInstance);
        }

        // Endpoint source — feeds OAL endpoint_cpm, endpoint_resp_time, etc.
        // Endpoint name is the domain (net.peer.name) only
        final Endpoint endpoint = new Endpoint();
        endpoint.setName(endpointName);
        endpoint.setServiceName(serviceName);
        endpoint.setServiceLayer(Layer.IOS);
        endpoint.setServiceInstanceName(instanceName);
        endpoint.setLatency(latency);
        endpoint.setStatus(status);
        endpoint.setHttpResponseStatusCode(httpStatusCode);
        endpoint.setType(RequestType.HTTP);
        endpoint.setTags(Collections.emptyList());
        endpoint.setOriginalTags(Collections.emptyMap());
        endpoint.setTimeBucket(timeBucket);
        sourceReceiver.receive(endpoint);

        return SpanListenerResult.CONTINUE;
    }

    /**
     * Prefer the OTel stable HTTP semconv attribute, fall back to the legacy key.
     * Supports all three Swift URLSessionInstrumentation modes (.old, .stable, .httpDup).
     */
    private static String attr(final OTLPSpanReader span, final String stableKey, final String legacyKey) {
        final String v = span.getAttribute(stableKey);
        if (v != null && !v.isEmpty()) {
            return v;
        }
        return span.getAttribute(legacyKey);
    }
}
