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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.database.TopNDatabaseStatement;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.input.RecordCondition;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Reads sampled records — the slowest database statements — the way the records query does ({@code readRecords}), on
 * the real {@code top_n_database_statement} stream: the top N of one database by latency, either way, wherever they
 * sit in time, and none of another database's.
 */
@Testcontainers
public class BanyanDBRecordsQueryIT {
    private static final int STATEMENTS = 30;
    private static final String DATABASE = "records-it-db";
    private static final String OTHER_DATABASE = "records-it-db-other";

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBRecordsQueryDAO QUERY_DAO;
    private static Duration DURATION;

    @BeforeAll
    public static void writeStatements() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        final long first = System.currentTimeMillis() - STATEMENTS * 1000L;
        final List<TopNDatabaseStatement> statements = new ArrayList<>();
        for (int i = 0; i < STATEMENTS; i++) {
            // Latency order is not time order: statement i is (7i mod 30) + 1 tens of milliseconds.
            statements.add(statement(DATABASE, i, ((7 * i) % STATEMENTS + 1) * 10L, first + i * 1000L));
            if (i < 5) {
                statements.add(statement(OTHER_DATABASE, i, 10_000L + i, first + i * 1000L));
            }
        }
        SUPPORT.write(SUPPORT.install(TopNDatabaseStatement.class, DefaultScopeDefine.DATABASE_SLOW_STATEMENT,
                                      TopNDatabaseStatement.INDEX_NAME),
                      new TopNDatabaseStatement.Builder(), statements);
        QUERY_DAO = new BanyanDBRecordsQueryDAO(SUPPORT.getClient());
        DURATION = BanyanDBQueryITSupport.around(first, first + STATEMENTS * 1000L);
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(3, read(Order.DES, 3).size()));
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void readsTheSlowestStatementsOfADatabase() throws IOException {
        assertEquals(Arrays.asList("300", "290", "280"), values(read(Order.DES, 3)));
    }

    @Test
    public void readsTheFastestStatementsOfADatabase() throws IOException {
        assertEquals(Arrays.asList("10", "20", "30"), values(read(Order.ASC, 3)));
    }

    @Test
    public void readsEachRecordWhole() throws IOException {
        // Latency 300 is (7i mod 30) = 29, i.e. statement 17.
        final org.apache.skywalking.oap.server.core.query.type.Record slowest = read(Order.DES, 1).get(0);
        assertEquals("select 17|trace-17|300", slowest.getName() + "|" + slowest.getRefId() + "|" + slowest.getValue());
    }

    private static List<org.apache.skywalking.oap.server.core.query.type.Record> read(Order order, int topN)
        throws IOException {
        final Entity database = new Entity();
        database.setScope(Scope.Service);
        database.setServiceName(DATABASE);
        database.setNormal(false);
        final RecordCondition condition = new RecordCondition();
        condition.setName(TopNDatabaseStatement.INDEX_NAME);
        condition.setParentEntity(database);
        condition.setTopN(topN);
        condition.setOrder(order);
        return QUERY_DAO.readRecords(condition, TopN.LATENCY, DURATION);
    }

    private static List<String> values(List<org.apache.skywalking.oap.server.core.query.type.Record> records) {
        return records.stream().map(org.apache.skywalking.oap.server.core.query.type.Record::getValue)
                      .collect(Collectors.toList());
    }

    private static TopNDatabaseStatement statement(String database, int i, long latency, long timestamp) {
        final TopNDatabaseStatement statement = new TopNDatabaseStatement();
        statement.setId(database + "-" + i);
        statement.setStatement("select " + i);
        statement.setLatency(latency);
        statement.setTraceId("trace-" + i);
        statement.setEntityId(IDManager.ServiceID.buildId(database, false));
        statement.setTimestamp(timestamp);
        statement.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
        return statement;
    }
}
