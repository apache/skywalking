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

package org.apache.skywalking.oap.log.analyzer.dsl.spec.extractor.slowsql;

import org.apache.skywalking.oap.log.analyzer.dsl.spec.AbstractSpec;

import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.DatabaseSlowStatementBuilder;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import static java.util.Objects.nonNull;

public class SlowSqlSpec extends AbstractSpec {

    public SlowSqlSpec(final ModuleManager moduleManager,
                       final LogAnalyzerModuleConfig moduleConfig) {
        super(moduleManager, moduleConfig);
    }

    public void latency(final Long latency) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        if (nonNull(latency)) {
            final DatabaseSlowStatementBuilder databaseSlowStatementBuilder = BINDING.get().databaseSlowStatement();
            databaseSlowStatementBuilder.setLatency(latency);
        }
    }

    public void statement(final String statement) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        if (nonNull(statement)) {
            final DatabaseSlowStatementBuilder databaseSlowStatementBuilder = BINDING.get().databaseSlowStatement();
            databaseSlowStatementBuilder.setStatement(statement);
        }
    }

    public void id(final String id) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        if (nonNull(id)) {
            final DatabaseSlowStatementBuilder databaseSlowStatementBuilder = BINDING.get().databaseSlowStatement();
            databaseSlowStatementBuilder.setId(id);
        }
    }
}
