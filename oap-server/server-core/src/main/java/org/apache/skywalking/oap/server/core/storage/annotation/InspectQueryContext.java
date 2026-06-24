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

package org.apache.skywalking.oap.server.core.storage.annotation;

import java.util.Map;

/**
 * Request-scoped overlay of {@link ForeignMetricMeta}, holding metadata for the foreign metrics
 * referenced by ONE inspect value query, keyed by metric name.
 *
 * <p>This is NOT a behaviour side-channel: the metadata enters the system as an explicit parameter
 * to the synchronous MQE execute entry, which republishes it here and removes it in a {@code finally}
 * on the SAME thread that runs the (entirely synchronous) MQE eval + storage read. It is consulted by
 * {@link ValueColumnMetadata} (so the MQE resolution sees the foreign metric) and by the storage
 * value-read paths (which need {@link ForeignMetricMeta#getValueType()} to decode). It mirrors how
 * {@code DebuggingTraceContext.TRACE_CONTEXT} already rides the same eval→DAO thread. The public MQE /
 * GraphQL path never sets it, so it is {@code null} for every normal query.
 */
public final class InspectQueryContext {
    private static final ThreadLocal<Map<String, ForeignMetricMeta>> CONTEXT = new ThreadLocal<>();

    private InspectQueryContext() {
    }

    /**
     * Open the overlay for the current thread. Must be paired with {@link #clear()} in a
     * {@code finally} on the same thread.
     *
     * @param metaByMetric immutable map of metric name to its caller-supplied metadata
     */
    public static void set(final Map<String, ForeignMetricMeta> metaByMetric) {
        CONTEXT.set(Map.copyOf(metaByMetric));
    }

    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * @param metricName the metric being resolved
     * @return the caller-supplied metadata, or {@code null} when no overlay is active or the metric
     * is not in it (i.e. it is a locally-registered metric)
     */
    public static ForeignMetricMeta get(final String metricName) {
        final Map<String, ForeignMetricMeta> map = CONTEXT.get();
        return map == null ? null : map.get(metricName);
    }
}
