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

package org.apache.skywalking.oap.server.core.source;

import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * Interface for LAL output builders that produce {@link Source} objects from
 * LAL extractor output fields.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} and
 * can be referenced in LAL rule YAML config by their short {@link #name()}
 * (e.g., {@code outputType: SlowSQL}) instead of the fully qualified class name.
 *
 * <p>The LAL compiler validates output field assignments against the builder's
 * setters at compile time. At runtime:
 * <ol>
 *   <li>{@link #init} is called to pre-populate standard fields from
 *       {@code LogData} (metadata carrier) and an optional extra log object
 *       whose type matches what {@code LALSourceTypeProvider#inputType()}
 *       declares for the layer</li>
 *   <li>Output field values are applied via reflection (setter invocation)</li>
 *   <li>{@link #complete} is called to validate, create final Source/Record,
 *       and dispatch via {@link SourceReceiver}</li>
 * </ol>
 */
public interface LALOutputBuilder {
    /**
     * Short name used in LAL rule YAML config {@code outputType} field.
     * Must be unique across all implementations.
     */
    String name();

    /**
     * Pre-populate standard fields before custom output fields are applied.
     * Called once per log entry.
     *
     * @param metadata      uniform metadata (service, layer, timestamp, trace context, etc.)
     * @param input         source-specific input object ({@code LogData} for standard logs,
     *                      {@code HTTPAccessLogEntry} for envoy access logs, etc.)
     * @param moduleManager module manager for resolving services (e.g., NamingControl)
     */
    void init(LogMetadata metadata, Object input, ModuleManager moduleManager);

    /**
     * Validate the builder state and dispatch the final output source(s).
     * Called after all output fields have been set.
     */
    void complete(SourceReceiver sourceReceiver);

}
