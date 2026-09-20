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

package org.apache.skywalking.oap.query.traceql.converter;

import java.util.Map;
import zipkin2.Annotation;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;

/**
 * Re-evaluates a Zipkin {@link QueryRequest} against one span, so a search result lists the spans that matched
 * instead of every span of the matched trace, as Tempo does. The conditions are the ones the Zipkin query DAOs
 * apply per span: local and remote service, span name, the annotation query over tag keys, {@code key=value}
 * tags and annotation values, and the duration range.
 */
public final class ZipkinSpanMatcher {
    private final QueryRequest request;

    public ZipkinSpanMatcher(final QueryRequest request) {
        this.request = request;
    }

    public boolean matches(final Span span) {
        if (request.serviceName() != null && !request.serviceName().equals(span.localServiceName())) {
            return false;
        }
        if (request.remoteServiceName() != null && !request.remoteServiceName().equals(span.remoteServiceName())) {
            return false;
        }
        if (request.spanName() != null && !request.spanName().equals(span.name())) {
            return false;
        }
        if (request.minDuration() != null && span.durationAsLong() < request.minDuration()) {
            return false;
        }
        if (request.maxDuration() != null && span.durationAsLong() > request.maxDuration()) {
            return false;
        }
        for (final Map.Entry<String, String> condition : request.annotationQuery().entrySet()) {
            if (condition.getValue().isEmpty()) {
                if (!span.tags().containsKey(condition.getKey()) && !hasAnnotation(span, condition.getKey())) {
                    return false;
                }
            } else if (!condition.getValue().equals(span.tags().get(condition.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasAnnotation(final Span span, final String value) {
        for (final Annotation annotation : span.annotations()) {
            if (value.equals(annotation.value())) {
                return true;
            }
        }
        return false;
    }
}
