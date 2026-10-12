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

package org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener;

import org.apache.skywalking.oap.server.core.analysis.manual.database.TopNDatabaseStatement;
import org.apache.skywalking.oap.server.core.source.DatabaseSlowStatement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseSlowStatementBuilderTest {
    @Test
    void aStatementLongerThanTheColumnIsCut() {
        final DatabaseSlowStatementBuilder builder = new DatabaseSlowStatementBuilder();
        builder.setServiceName("db:5432");
        builder.setStatement("x".repeat(TopNDatabaseStatement.STATEMENT_MAX_LENGTH + 1));
        final DatabaseSlowStatement record = builder.toDatabaseSlowStatement();
        assertEquals(TopNDatabaseStatement.STATEMENT_MAX_LENGTH, record.getStatement().length());

        builder.setStatement("SELECT 1");
        assertEquals("SELECT 1", builder.toDatabaseSlowStatement().getStatement());
    }
}
