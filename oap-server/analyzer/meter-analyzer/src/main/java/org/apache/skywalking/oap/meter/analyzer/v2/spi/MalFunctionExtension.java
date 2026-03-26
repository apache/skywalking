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

package org.apache.skywalking.oap.meter.analyzer.v2.spi;

/**
 * SPI for extending MAL with custom functions callable via the {@code namespace::method()} syntax.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader}. Each extension provides a
 * namespace and one or more methods annotated with {@link MALContextFunction}. The first parameter
 * of each annotated method must be {@code SampleFamily} (auto-bound to the current chain value).
 * <p>
 * Example usage in MAL script: {@code metric.sum(['svc']).genai::estimateCost()}
 */
public interface MalFunctionExtension {
    /**
     * The namespace used in MAL scripts: {@code .namespace::method(...)}.
     * Must be unique across all registered extensions.
     */
    String name();
}
