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

package org.apache.skywalking.oap.log.analyzer.dsl.spec.parser;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.skywalking.oap.log.analyzer.dsl.spec.AbstractSpec;
import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

@Accessors(fluent = true)
public class AbstractParserSpec extends AbstractSpec {
    /**
     * Whether the filter chain should abort when parsing the logs failed.
     *
     * Failing to parse the logs means either parsing throws exceptions or the logs not matching the desired patterns.
     */
    @Getter
    @Setter
    private boolean abortOnFailure = true;

    public AbstractParserSpec(final ModuleManager moduleManager,
                              final LogAnalyzerModuleConfig moduleConfig) {
        super(moduleManager, moduleConfig);
    }
}
