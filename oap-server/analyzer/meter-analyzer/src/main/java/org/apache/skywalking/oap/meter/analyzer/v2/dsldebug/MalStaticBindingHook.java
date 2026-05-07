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

package org.apache.skywalking.oap.meter.analyzer.v2.dsldebug;

import java.util.concurrent.atomic.AtomicReference;
import org.apache.skywalking.oap.meter.analyzer.v2.Analyzer;
import org.apache.skywalking.oap.meter.analyzer.v2.MetricConvert;
import org.apache.skywalking.oap.server.core.dsldebug.GateHolder;

/**
 * Process-wide hook that publishes static-loader MAL rule bindings into the
 * dsl-debugging session registry. Avoids plumbing a {@code MALHolderRegistry}
 * reference through every receiver-plugin's static-rule loader: each loader
 * builds its {@link MetricConvert} as before, then calls
 * {@link #publish(String, String, MetricConvert)} once. The hook is no-op
 * until the dsl-debugging module installs a real sink in its provider's
 * {@code start()} phase.
 *
 * <h2>Workflow</h2>
 * <pre>
 *   dsl-debugging start()      ─►  MalStaticBindingHook.install(sink)
 *
 *   receiver-plugin boot loop:
 *       MetricConvert convert = new MetricConvert(rule, meterSystem);
 *       MalStaticBindingHook.publish("otel-rules", rule.getName(), convert);
 *                                       │
 *                                       └─ for each analyzer in convert:
 *                                              sink.publish(catalog, name, metricName, holder)
 *                                              (sink wraps holder into MALHolderRegistry.register)
 * </pre>
 *
 * <p>Why a static hook and not an SPI: MAL static loaders run before any
 * module-manager service is available in some test topologies, and walking
 * {@code ServiceLoader} on every static rule load is wasteful when the
 * binding cost is one method call. The hook is process-scoped, set once,
 * and never overwritten in a normal OAP boot. Tests that don't enable
 * dsl-debugging see the no-op default and pay nothing.
 */
public final class MalStaticBindingHook {

    /** Sink the hook forwards every bound holder to. */
    @FunctionalInterface
    public interface Sink {
        void publish(String catalog, String name, String metricName, GateHolder holder);
    }

    private static final Sink NOOP = (catalog, name, metricName, holder) -> {
    };

    /**
     * Active sink reference. AtomicReference (rather than a plain volatile
     * field) keeps the field name conventionally lowercase by treating it as
     * a final container holding mutable state — matches checkstyle's
     * static-naming rule without forcing the SINK identifier on the API.
     */
    private static final AtomicReference<Sink> SINK = new AtomicReference<>(NOOP);

    private MalStaticBindingHook() {
    }

    /**
     * Install the active sink. Called once by the dsl-debugging module
     * provider's {@code prepare()}. A second install replaces the prior
     * sink — useful in tests; harmless in production where only
     * dsl-debugging installs one sink per OAP process.
     */
    public static void install(final Sink s) {
        SINK.set(s == null ? NOOP : s);
    }

    /** Reset to the no-op sink. Used by tests after install/teardown. */
    public static void reset() {
        SINK.set(NOOP);
    }

    /**
     * Walk the supplied {@link MetricConvert}'s analyzers and forward each
     * {@code (catalog, name, metricName, debugHolder)} tuple to the active
     * sink. Convert is read-only (we do not mutate). No-op when the convert
     * is null or carries no analyzers.
     */
    public static void publish(final String catalog, final String name,
                               final MetricConvert convert) {
        if (convert == null || catalog == null || name == null) {
            return;
        }
        final Sink s = SINK.get();
        for (final Analyzer analyzer : convert.getAnalyzers()) {
            if (analyzer.getExpression() == null) {
                continue;
            }
            s.publish(catalog, name, analyzer.getMetricName(),
                      analyzer.getExpression().debugHolder());
        }
    }
}
