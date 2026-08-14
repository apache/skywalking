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
    /**
     * Special layer value indicating the layer is determined by the LAL script at runtime.
     * Rules with {@code layer: auto} match logs where {@code service.layer} is absent.
     * The script is expected to set the layer in the extractor; if not set, the log is dropped.
     */
    public static final String LAYER_AUTO = "auto";
    private String name;

    /** 1-based line of this rule's entry in its source YAML; 0 when unresolved. */
    private int lineNo;

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
     * Source YAML file name, set during loading by {@link LALConfigs}.
     *
     * <p>This is the rule file's <b>identity</b>: it is the middle component of the
     * dsl-debugging {@code RuleKey (LAL, sourceName, ruleName)}, and the boot loader and the
     * runtime-rule applier must produce the same value for the same file or a hot update
     * registers a second binding instead of replacing the static one.
     */
    private transient String sourceName;

    /**
     * Catalog-qualified path to the rule file, e.g. {@code lal/default.yaml} — what a generated
     * class names in its {@code SourceFile}.
     *
     * <p>Separate from {@link #sourceName} on purpose: attribution wants the path an operator can
     * open, the debug registry wants a stable identity, and one field cannot change to suit the
     * first without silently re-keying the second.
     */
    private transient String sourcePath;
}
