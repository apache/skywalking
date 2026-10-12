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

package org.apache.skywalking.oap.trace.analyzer.v2.provider;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import java.util.List;
import lombok.Data;

@Data
public class TALConfig {
    /**
     * The layer value of a rule that decides the layer itself. Such a rule sees the spans whose resource carries
     * no {@code service.layer}, and its extractor must set {@code layer} or its output is discarded.
     */
    public static final String LAYER_AUTO = "auto";

    /**
     * The input kind a rule reads when it names none: the OTLP span.
     */
    public static final String DEFAULT_INPUT_TYPE = "otlp";

    private String name;

    /**
     * 1-based line of this rule's entry in its source YAML; 0 when unresolved.
     */
    private int lineNo;

    private String dsl;

    private String layer;

    /**
     * The input kinds this rule reads, comma separated: each is an alias a {@code TALSourceTypeProvider} registers
     * or a class name one declares. Empty means {@link #DEFAULT_INPUT_TYPE}.
     */
    private String inputType;

    /**
     * @return the declared input kinds, or the default when the rule names none
     */
    public List<String> inputTypes() {
        final List<String> declared = Splitter.on(",").omitEmptyStrings().trimResults()
                                              .splitToList(Strings.nullToEmpty(inputType));
        return declared.isEmpty() ? List.of(DEFAULT_INPUT_TYPE) : declared;
    }

    /**
     * The record this rule writes, named by a {@code TALOutputBuilder}'s name or by a class name. Absent means the
     * rule writes no record, and a {@code sink {}} in it is a compile error.
     */
    private String outputType;

    /**
     * Source YAML file name, set while loading. The middle component of the dsl-debugging
     * {@code RuleKey (TAL, sourceName, ruleName)}: the boot loader and the runtime-rule applier must produce the
     * same value for one file or a hot update registers a second binding instead of replacing the static one.
     */
    private transient String sourceName;

    /**
     * Catalog-qualified path to the rule file, {@code tal/otlp-default.yaml}, which a generated class names in its
     * {@code SourceFile}.
     */
    private transient String sourcePath;

    /**
     * The prefix every metric of this rule's file carries, from the file's {@code metricPrefix}.
     */
    private transient String metricPrefix;
}
