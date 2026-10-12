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

package org.apache.skywalking.oap.trace.analyzer.v2.provider.span;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.TimeUnit;

/**
 * The spans this OAP node analyzed recently, so an exporter that retries a batch after a transport timeout does
 * not have a request counted twice. Keyed by trace id and span id, bounded by {@code dedupCapacity} entries and
 * forgetting each after {@code dedupWindow} seconds; a window of 0 keeps nothing and reports every span as new.
 * Per node: a retry that lands on another node is not caught.
 */
public class SeenSpans {
    private final Cache<String, Boolean> seen;

    /**
     * @param windowSeconds how long a span stays remembered; 0 disables the check
     * @param capacity      the most spans remembered at once
     */
    public SeenSpans(final int windowSeconds, final int capacity) {
        seen = windowSeconds > 0
            ? CacheBuilder.newBuilder().maximumSize(Math.max(1, capacity))
                          .expireAfterWrite(windowSeconds, TimeUnit.SECONDS).build()
            : null;
    }

    /**
     * Record the span and say whether it is new.
     *
     * @param traceId the span's trace
     * @param spanId  the span
     * @return true the first time this pair is seen inside the window, or always when the check is off
     */
    public boolean firstSight(final String traceId, final String spanId) {
        if (seen == null) {
            return true;
        }
        final String key = traceId + '/' + spanId;
        if (seen.getIfPresent(key) != null) {
            return false;
        }
        seen.put(key, Boolean.TRUE);
        return true;
    }
}
