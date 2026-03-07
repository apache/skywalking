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

package org.apache.skywalking.oap.log.analyzer.v2.spi;

import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.source.Source;

/**
 * SPI for receiver plugins to declare the input and default output types for
 * LAL rules on a specific {@link Layer}.
 *
 * <p><b>Input type</b> ({@link #inputType()}): The Java type of the {@code extraLog}
 * passed to LAL via {@code ILogAnalyzerService.doAnalysis(LogData, Message)}.
 * The LAL compiler uses this at compile time to generate optimized direct
 * getter calls for {@code parsed.*} field access. This is per-layer because
 * all rules for a layer share the same input proto type.
 *
 * <p><b>Output type</b> ({@link #outputType()}): The default output type for
 * the layer. Individual rules can override this via the {@code outputType}
 * field in YAML rule config. The compiler validates output field assignments
 * against the resolved output type at compile time.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} and
 * matched by {@link Layer}.
 *
 * <p>Input type resolution order:
 * <ol>
 *   <li>DSL parser ({@code json{}}, {@code yaml{}}, {@code text{}}) wins over all</li>
 *   <li>Explicit {@code inputType} declared in the YAML rule config</li>
 *   <li>This SPI — acts as the default for a layer</li>
 *   <li>{@code LogData.Builder} fallback if none of the above</li>
 * </ol>
 *
 * <p>Output type resolution order:
 * <ol>
 *   <li>Explicit {@code outputType} declared in the YAML rule config (per-rule)</li>
 *   <li>This SPI — acts as the default for a layer</li>
 *   <li>{@code Log} if not specified anywhere</li>
 * </ol>
 *
 * <p>Receiver plugins register implementations in
 * {@code META-INF/services/org.apache.skywalking.oap.log.analyzer.v2.spi.LALSourceTypeProvider}.
 */
public interface LALSourceTypeProvider {
    /**
     * The layer this provider supplies type information for.
     */
    Layer layer();

    /**
     * The Java type passed as {@code extraLog} by the receiver plugin for
     * this layer. The compiler resolves getter chains on this type at
     * compile time.
     */
    Class<?> inputType();

    /**
     * The default {@link Source} subclass that LAL rules on this layer produce.
     * Individual rules can override this via the {@code outputType} YAML config field.
     * Returns {@code null} by default, meaning the standard {@code Log} source is used.
     */
    default Class<? extends Source> outputType() {
        return null;
    }
}
