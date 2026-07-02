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

package org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.parser;

import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.AbstractSpec;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * Base of the shared parser specs. Per-rule options such as {@code abortOnFailure} are NOT
 * state on the spec — one spec instance serves every compiled rule concurrently, so the v2
 * compiler bakes each rule's flag into the generated call site (see
 * {@code LALClassGenerator#generateFilterStatement}) and it travels as a method parameter.
 */
public class AbstractParserSpec extends AbstractSpec {
    public AbstractParserSpec(final ModuleManager moduleManager,
                              final LogAnalyzerModuleConfig moduleConfig) {
        super(moduleManager, moduleConfig);
    }
}
