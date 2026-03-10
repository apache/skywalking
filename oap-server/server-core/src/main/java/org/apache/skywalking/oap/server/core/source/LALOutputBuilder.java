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

import org.apache.skywalking.oap.server.core.config.NamingControl;

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
 *   <li>{@link #init} is called to pre-populate standard fields from the input
 *       data object whose type matches what
 *       {@code LALSourceTypeProvider#inputType()} declares (e.g.,
 *       {@code LogData} for standard logs,
 *       {@code HTTPAccessLogEntry} for envoy access logs)</li>
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
     * Pre-populate standard fields from the input data before custom output
     * fields are applied. Called once per log entry.
     *
     * <p>The actual type of {@code logData} matches what the
     * {@code LALSourceTypeProvider#inputType()} declares for the layer.
     * For standard logs this is {@code LogData}; for envoy access logs
     * this is {@code HTTPAccessLogEntry}, etc. Each builder casts directly
     * to its expected type.
     */
    void init(Object logData, NamingControl namingControl);

    /**
     * Validate the builder state and dispatch the final output source(s).
     * Called after all output fields have been set.
     */
    void complete(SourceReceiver sourceReceiver);

}
