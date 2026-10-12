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

package org.apache.skywalking.oap.trace.analyzer.v2.spi;

import java.util.Collections;
import java.util.Map;

/**
 * SPI for a receiver plugin to contribute one input kind a TAL rule may declare in {@code inputType}.
 *
 * <p>The compiler resolves a rule's {@code inputType} entry against the registered aliases first and against the
 * declared {@link #inputType()} class name second, then types the {@code span} root to that class and resolves its
 * getter chains by reflection. At runtime the same provider reads the attributes the {@code attr()},
 * {@code scopeAttr()}, {@code event()} and {@code eventAttr()} functions expose, which is why a rule written
 * against {@code meta} and {@code attr()} runs unchanged on every input.
 *
 * <p>The trace-analyzer module registers the OTLP provider, the only input this release ships.
 * Implementations are discovered through {@link java.util.ServiceLoader} from
 * {@code META-INF/services/org.apache.skywalking.oap.trace.analyzer.v2.spi.TALSourceTypeProvider}.
 */
public interface TALSourceTypeProvider {
    /**
     * @return the short name a rule writes in {@code inputType}, such as {@code otlp}
     */
    String alias();

    /**
     * @return the Java type of the object the receiver hands to
     * {@code ITraceAnalyzerService#doAnalysis}, which the {@code span} root is typed to
     */
    Class<?> inputType();

    /**
     * Read the span's own attributes. The runtime indexes them once per execution and serves every {@code attr()}
     * and {@code hasAttr()} of that execution from the index.
     *
     * @param input the span, an instance of {@link #inputType()}
     * @return the attributes with their values rendered as strings; an absent key is absent from the map
     */
    Map<String, String> spanAttributes(Object input);

    /**
     * @param input the span, an instance of {@link #inputType()}
     * @return the instrumentation scope's attributes, empty for an input that has no scope
     */
    default Map<String, String> scopeAttributes(Object input) {
        return Collections.emptyMap();
    }

    /**
     * @param input the span, an instance of {@link #inputType()}
     * @param name  the event name to look for
     * @return the first event with that name, or null; the engine passes it to the rule without reading it
     */
    default Object event(Object input, String name) {
        return null;
    }

    /**
     * @param input     the span, an instance of {@link #inputType()}
     * @param eventName the event to look in
     * @param key       the attribute to read
     * @return the attribute's value as a string, empty when the event or the attribute is absent
     */
    default String eventAttribute(Object input, String eventName, String key) {
        return "";
    }
}
