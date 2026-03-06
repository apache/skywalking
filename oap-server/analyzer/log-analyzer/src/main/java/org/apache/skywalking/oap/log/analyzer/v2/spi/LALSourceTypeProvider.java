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

/**
 * SPI for receiver plugins to declare the Java type of the {@code extraLog}
 * they pass to LAL via {@code ILogAnalyzerService.doAnalysis(LogData, Message)}.
 *
 * <p>The LAL compiler uses this at compile time to generate optimized direct
 * getter calls instead of runtime reflection. Implementations are discovered
 * via {@link java.util.ServiceLoader} and matched by {@link Layer}.
 *
 * <p>Per-rule type resolution order:
 * <ol>
 *   <li>DSL parser ({@code json{}}, {@code yaml{}}, {@code text{}}) — parser wins</li>
 *   <li>Explicit {@code extraLogType} declared in the YAML rule config</li>
 *   <li>This SPI — acts as the default {@code extraLogType} for a layer</li>
 *   <li>Compile error if none of the above and the rule accesses {@code parsed.*}</li>
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
    Class<?> extraLogType();
}
