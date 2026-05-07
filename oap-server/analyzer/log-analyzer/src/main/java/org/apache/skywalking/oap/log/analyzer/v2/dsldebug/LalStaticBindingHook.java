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

package org.apache.skywalking.oap.log.analyzer.v2.dsldebug;

import java.util.concurrent.atomic.AtomicReference;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression;
import org.apache.skywalking.oap.server.core.dsldebug.GateHolder;

/**
 * Process-wide hook that publishes static-loader LAL rule bindings into the
 * dsl-debugging session registry. Same shape as
 * {@code MalStaticBindingHook}: dsl-debugging installs a real sink at boot
 * (no-op default), each LAL static-loader site calls
 * {@link #publish(String, String, LalExpression)} once per compiled rule.
 *
 * <h2>Workflow</h2>
 * <pre>
 *   dsl-debugging prepare()       ─►  LalStaticBindingHook.install(sink)
 *
 *   LogFilterListener.Factory.loadStaticRules:
 *       for each LALConfig c:
 *           CompiledLAL compiled = compile(c);
 *           LalStaticBindingHook.publish(c.getSourceName(), c.getName(), compiled.dsl.getExpression());
 *                                                │                  │                        │
 *                                                fileName           ruleName                 holder
 *                                                                 (matches the runtime-rule LAL key shape)
 * </pre>
 *
 * <p>The registry's RuleKey for LAL is {@code (LAL, fileName, ruleName)};
 * passing the file's {@code sourceName} keeps static and runtime entries
 * keyed identically so a runtime-rule replace simply puts over the static
 * binding without orphaning it.
 */
public final class LalStaticBindingHook {

    /** Sink the hook forwards every bound holder to. */
    @FunctionalInterface
    public interface Sink {
        void publish(String fileName, String ruleName, GateHolder holder);
    }

    private static final Sink NOOP = (fileName, ruleName, holder) -> {
    };

    private static final AtomicReference<Sink> SINK = new AtomicReference<>(NOOP);

    private LalStaticBindingHook() {
    }

    public static void install(final Sink s) {
        SINK.set(s == null ? NOOP : s);
    }

    public static void reset() {
        SINK.set(NOOP);
    }

    /**
     * Publish one LAL rule's debug holder. No-op when any argument is null
     * or the expression carries no holder (defensive — LalExpression guarantees
     * a non-null holder, but the check keeps the static-loader path
     * crash-free against any future refactor).
     */
    public static void publish(final String fileName, final String ruleName,
                               final LalExpression expression) {
        if (fileName == null || ruleName == null || expression == null) {
            return;
        }
        final GateHolder holder = expression.debugHolder();
        if (holder == null) {
            return;
        }
        SINK.get().publish(fileName, ruleName, holder);
    }
}
