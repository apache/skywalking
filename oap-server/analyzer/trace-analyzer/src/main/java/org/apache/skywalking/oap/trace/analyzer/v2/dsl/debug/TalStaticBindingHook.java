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

package org.apache.skywalking.oap.trace.analyzer.v2.dsl.debug;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.dsl.debug.GateHolder;

/**
 * Publishes every loaded TAL rule to dsl-debugging, which installs the sink at boot; without it the hook is a no-op.
 * Same shape as {@code LalStaticBindingHook}, plus what the read-only rule listing shows: each rule is published
 * whether or not the debug probes were compiled in, and then carries no holder.
 *
 * <p>TAL rules load once, at boot, from {@code SpanFilterListener.Factory}: no runtime-rule engine claims the
 * {@code tal} catalog, so a binding is never replaced or withdrawn.
 */
public final class TalStaticBindingHook {

    public static final String LIST_TAL_FILES = "talFiles";
    public static final String LIST_NATIVE_TAL_FILES = "nativeTalFiles";

    /**
     * One loaded rule.
     */
    @Getter
    @RequiredArgsConstructor
    public static final class Binding {
        /**
         * The rule file as loaded, such as {@code otlp-general.yaml}.
         */
        private final String file;
        /**
         * The name the loader files the rule file under, its name without the extension, such as
         * {@code otlp-general}; a debug session and the rule listing name the file by it.
         */
        private final String name;
        private final String ruleName;
        /**
         * The input alias the rule declares: {@code otlp} or {@code sw-native}.
         */
        private final String inputType;
        private final String layer;
        /**
         * The setting that loaded the file: {@link #LIST_TAL_FILES} or {@link #LIST_NATIVE_TAL_FILES}.
         */
        private final String list;
        /**
         * The rule's position in the order the native rules run; -1 for a rule of {@code talFiles}.
         */
        private final int order;
        /**
         * The rule's line in its file.
         */
        private final int line;
        /**
         * The rule's {@code segmentDef} names, in slot order.
         */
        private final String[] segmentDefNames;
        /**
         * The rule's capture binding; null when the probes were not compiled in.
         */
        private final GateHolder holder;
    }

    @FunctionalInterface
    public interface Sink {
        void publish(Binding binding);
    }

    private static final Sink NOOP = binding -> {
    };

    private static final AtomicReference<Sink> SINK = new AtomicReference<>(NOOP);

    private TalStaticBindingHook() {
    }

    public static void install(final Sink sink) {
        SINK.set(sink == null ? NOOP : sink);
    }

    public static void reset() {
        SINK.set(NOOP);
    }

    /**
     * Stamp the rule's coordinates on its holder, which every record of a session on it carries, then publish it.
     */
    public static void publish(final Binding binding) {
        if (binding == null || binding.getFile() == null || binding.getRuleName() == null) {
            return;
        }
        final GateHolder holder = binding.getHolder();
        if (holder != null) {
            final Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("ruleName", binding.getRuleName());
            metadata.put("file", binding.getFile());
            metadata.put("inputType", binding.getInputType());
            metadata.put("layer", binding.getLayer());
            metadata.put("sourceLine", Integer.toString(binding.getLine()));
            if (binding.getSegmentDefNames().length > 0) {
                metadata.put("segmentDefs", String.join(",", binding.getSegmentDefNames()));
            }
            holder.setMetadata(metadata);
        }
        SINK.get().publish(binding);
    }
}
