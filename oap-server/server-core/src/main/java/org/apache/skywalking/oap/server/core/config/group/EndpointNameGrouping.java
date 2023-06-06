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

package org.apache.skywalking.oap.server.core.config.group;

import io.vavr.Tuple2;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.ai.pipeline.services.api.HttpUriRecognition;
import org.apache.skywalking.oap.server.core.config.group.openapi.EndpointGroupingRule4Openapi;
import org.apache.skywalking.oap.server.library.util.RunnableWithExceptionProtection;
import org.apache.skywalking.oap.server.library.util.StringFormatGroup;

@Slf4j
public class EndpointNameGrouping {
    @Setter
    private volatile EndpointGroupingRule endpointGroupingRule;
    @Setter
    private volatile EndpointGroupingRule4Openapi endpointGroupingRule4Openapi;
    private HttpUriRecognition httpUriRecognitionSvr;
    /**
     * Cache the HTTP URIs which are not formatted by the rules per service.
     * Level one map key is service name, the value is a map of HTTP URI and its count.
     * Multiple matches will be counted, because it is the pattern that the endpoint is already formatted,
     * or doesn't need to be formatted.
     * The repeatable URI is a pattern already.
     */
    private ConcurrentHashMap<String, ConcurrentHashMap<String, AtomicInteger>> cachedHttpUris = new ConcurrentHashMap<>();

    /**
     * Format the endpoint name according to the API patterns.
     *
     * @param serviceName  service name
     * @param endpointName endpoint name to be formatted.
     * @return Tuple2 where the first element is the formatted name and the second element is a boolean
     * represented the endpoint name is formatted or not.
     */
    public Tuple2<String, Boolean> format(String serviceName, String endpointName) {
        Tuple2<String, Boolean> formattedName = new Tuple2<>(endpointName, Boolean.FALSE);
        if (endpointGroupingRule4Openapi != null) {
            formattedName = formatByOpenapi(serviceName, endpointName);
        }

        if (!formattedName._2() && endpointGroupingRule != null) {
            formattedName = formatByCustom(serviceName, endpointName);
        }

        if (!formattedName._2()) {
            // Only URI starts with '/' will be cached and formatted later.
            if (endpointName.startsWith("/")) {
                ConcurrentHashMap<String, AtomicInteger> svrHttpUris = cachedHttpUris.get(serviceName);
                if (svrHttpUris == null) {
                    cachedHttpUris.putIfAbsent(serviceName, new ConcurrentHashMap<>());
                    svrHttpUris = cachedHttpUris.get(serviceName);
                }
                // Only cache first 2000 URIs per 30 mins.
                if (svrHttpUris.size() < 2000) {
                    final AtomicInteger cachedCount = svrHttpUris.putIfAbsent(endpointName, new AtomicInteger(1));
                    if (null != cachedCount) {
                        cachedCount.incrementAndGet();
                    }
                }
            }
        }

        return formattedName;
    }

    private Tuple2<String, Boolean> formatByCustom(String serviceName, String endpointName) {
        final StringFormatGroup.FormatResult formatResult = endpointGroupingRule.format(serviceName, endpointName);
        if (log.isDebugEnabled() || log.isTraceEnabled()) {
            if (formatResult.isMatch()) {
                log.debug("Endpoint {} of Service {} has been renamed in group {} by endpointGroupingRule",
                          endpointName, serviceName, formatResult.getName()
                );
            } else {
                log.trace("Endpoint {} of Service {} keeps unchanged.", endpointName, serviceName);
            }
        }
        return new Tuple2<>(formatResult.getName(), formatResult.isMatch());
    }

    private Tuple2<String, Boolean> formatByOpenapi(String serviceName, String endpointName) {
        final StringFormatGroup.FormatResult formatResult = endpointGroupingRule4Openapi.format(
            serviceName, endpointName);
        if (log.isDebugEnabled() || log.isTraceEnabled()) {
            if (formatResult.isMatch()) {
                log.debug("Endpoint {} of Service {} has been renamed in group {} by endpointGroupingRule4Openapi",
                          endpointName, serviceName, formatResult.getName()
                );
            } else {
                log.trace("Endpoint {} of Service {} keeps unchanged.", endpointName, serviceName);
            }
        }
        return new Tuple2<>(formatResult.getName(), formatResult.isMatch());
    }

    public void setHttpUriRecognitionSvr(final HttpUriRecognition httpUriRecognitionSvr) {
        this.httpUriRecognitionSvr = httpUriRecognitionSvr;
        Executors.newSingleThreadScheduledExecutor()
                 .scheduleWithFixedDelay(
                     new RunnableWithExceptionProtection(
                         () -> {
                             cachedHttpUris.forEach((serviceName, httpUris) -> {
                                 List<HttpUriRecognition.HTTPUri> uris
                                     = httpUris.keySet()
                                               .stream()
                                               .map(
                                                   uri -> new HttpUriRecognition.HTTPUri(
                                                       uri, httpUris.get(uri).get()
                                                   ))
                                               .collect(Collectors.toList());
                                 httpUriRecognitionSvr
                                     .recognize(serviceName, uris,
                                                (service, patterns) -> {
                                                    StringFormatGroup group = new StringFormatGroup(patterns.size());
                                                    patterns.forEach(
                                                        p -> group.addRule(p.getFormattedUri(), p.getPattern()));
                                                    endpointGroupingRule.setRules(serviceName, group);
                                                }
                                     );
                             });
                         },
                         t -> log.error("Try to recognize URI patterns.", t)
                     ), 30, 30, TimeUnit.MINUTES
                 );

    }
}
