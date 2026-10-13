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

package org.apache.skywalking.oap.server.core.source;

import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * The span-side counterpart of {@link LALOutputBuilder}: what a TAL rule writes when it declares an
 * {@code outputType}, such as a slow database statement from a span that carried {@code db.query.text}.
 *
 * <p>Implementations are discovered through {@link java.util.ServiceLoader} and referenced in a rule's
 * {@code outputType} by their short {@link #name()} or by class name. The compiler validates every
 * output-field statement against a setter at compile time. At runtime the generated code creates the builder,
 * binds the span to it, the extractor's field statements set its values, and the sink stage runs
 * {@link #init} and {@link #complete}.
 *
 * <p>A builder that serves both DSLs implements this and {@link LALOutputBuilder}; the two lifecycles differ
 * only in which metadata carrier they take.
 */
public interface TALOutputBuilder {
    /**
     * @return the short name a rule writes in {@code outputType}; unique across implementations
     */
    String name();

    /**
     * Cache the typed span, before the extractor runs, so a debug probe sees a builder whose state already
     * reflects the input.
     *
     * @param meta  the span's metadata view
     * @param input the wire object
     */
    default void bindInput(SpanMetadata meta, Object input) {
    }

    /**
     * Populate the standard fields from {@code meta} and resolve any services the builder needs. Called at sink
     * time, after the extractor has set the rule's own fields.
     *
     * @param meta          the span's metadata view, carrying whatever the extractor overwrote
     * @param input         the wire object
     * @param moduleManager for resolving services such as {@code NamingControl}
     */
    void init(SpanMetadata meta, Object input, ModuleManager moduleManager);

    /**
     * Validate the builder's state and dispatch the record it built.
     *
     * @param sourceReceiver where the record is sent
     */
    void complete(SourceReceiver sourceReceiver);

    /**
     * @return the record as JSON, for the debugging session's output payload
     */
    String outputToJson();
}
