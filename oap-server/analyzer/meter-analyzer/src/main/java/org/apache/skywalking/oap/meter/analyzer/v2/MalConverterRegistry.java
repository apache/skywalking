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

package org.apache.skywalking.oap.meter.analyzer.v2;

import org.apache.skywalking.oap.server.library.module.Service;

/**
 * Shared contract for the MAL converter registry each MAL-consuming receiver exposes, so the
 * runtime-rule hot-update plugin can add / replace / remove individual converters without
 * reshuffling anyone's boot-time list.
 *
 * <p>Today two receivers implement it:
 * <ul>
 *   <li>{@code OpenTelemetryMetricRequestProcessor} for the {@code otel-rules} catalog — OTLP
 *       metrics flow.</li>
 *   <li>The {@code log-analyzer} module for the {@code log-mal-rules} catalog — inline MAL
 *       extracted from LAL {@code metrics {}} blocks.</li>
 * </ul>
 *
 * <p>The registry is keyed by a stable string the caller picks — today that string is
 * {@code "<catalog>:<name>"} (e.g., {@code "otel-rules:vm"}). The key namespace is
 * deliberately shared between boot-registered converters and runtime-rule-registered
 * converters: runtime-rule's {@code /addOrUpdate} replaces-in-place over whichever entry the
 * boot catalog or a prior runtime push left behind, and {@code /inactivate} drops that same
 * entry. This is what lets an operator override a shipped static rule without first deleting
 * it — the update lands under the same key and takes over dispatch.
 *
 * <p>Implementations must be thread-safe — ingest threads iterate concurrently while
 * runtime-rule mutates. The expected idiom is volatile map + copy-on-write under a private
 * write lock; readers take a reference snapshot without locking.
 */
public interface MalConverterRegistry extends Service {

    /**
     * Install or replace a converter under {@code key}. Idempotent: repeated calls with the
     * same {@code key} replace the entry atomically.
     */
    void addOrReplaceConverter(String key, MetricConvert convert);

    /**
     * Remove the converter previously installed under {@code key}. No-op if absent — the
     * runtime-rule delete / teardown path treats a missing entry as "already converged".
     */
    void removeConverter(String key);
}
