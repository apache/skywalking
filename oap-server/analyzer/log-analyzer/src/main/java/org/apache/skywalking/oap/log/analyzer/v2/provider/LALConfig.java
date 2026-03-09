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

package org.apache.skywalking.oap.log.analyzer.v2.provider;

import lombok.Data;

@Data
public class LALConfig {
    private String name;

    private String dsl;

    private String layer;

    /**
     * Fully qualified class name of the input type (the extra log proto/POJO)
     * for compile-time {@code parsed.*} getter resolution.
     */
    private String inputType;

    /**
     * Fully qualified class name of the output {@link org.apache.skywalking.oap.server.core.source.Source}
     * subclass that the LAL sink should produce.
     * Defaults to {@link org.apache.skywalking.oap.server.core.source.Log} when not specified.
     */
    private String outputType;

    /**
     * Source YAML file name (without extension), set during loading by
     * {@link LALConfigs}. Used for informative stack traces in generated code.
     */
    private transient String sourceName;
}
