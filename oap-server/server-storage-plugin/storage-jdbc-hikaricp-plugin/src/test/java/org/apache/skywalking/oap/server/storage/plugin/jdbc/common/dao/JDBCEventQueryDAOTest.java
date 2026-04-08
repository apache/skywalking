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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import io.vavr.Tuple2;
import org.apache.skywalking.oap.server.core.analysis.record.Event;
import org.apache.skywalking.oap.server.core.query.type.event.EventQueryCondition;
import org.apache.skywalking.oap.server.core.query.type.event.EventType;
import org.apache.skywalking.oap.server.core.query.type.event.Source;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JDBCEventQueryDAOTest {

    @Mock
    private JDBCClient jdbcClient;
    @Mock
    private TableHelper tableHelper;

    private JDBCEventQueryDAO dao;

    @BeforeEach
    void setUp() {
        dao = new JDBCEventQueryDAO(jdbcClient, tableHelper);
    }

    @Test
    void buildQuery_shouldAlwaysContainTableColumnCondition() {
        final EventQueryCondition condition = new EventQueryCondition();

        final Tuple2<Stream<String>, Stream<Object>> result = dao.buildQuery(condition);
        final List<String> conditions = result._1().collect(Collectors.toList());

        assertThat(conditions).contains(JDBCTableInstaller.TABLE_COLUMN + " = ?");
    }

    @Test
    void buildQuery_withNoOptionalConditions_shouldProduceOnlyTableColumnCondition() {
        final EventQueryCondition condition = new EventQueryCondition();

        final Tuple2<Stream<String>, Stream<Object>> result = dao.buildQuery(condition);
        final List<String> conditions = result._1().collect(Collectors.toList());
        final List<Object> parameters = result._2().collect(Collectors.toList());

        assertThat(conditions).hasSize(1);
        assertThat(conditions.get(0)).isEqualTo(JDBCTableInstaller.TABLE_COLUMN + " = ?");
        assertThat(parameters).containsExactly(Event.INDEX_NAME);
    }

    @Test
    void buildQuery_withUuid_shouldIncludeUuidCondition() {
        final EventQueryCondition condition = new EventQueryCondition();
        condition.setUuid("test-uuid");

        final Tuple2<Stream<String>, Stream<Object>> result = dao.buildQuery(condition);
        final List<String> conditions = result._1().collect(Collectors.toList());
        final List<Object> parameters = result._2().collect(Collectors.toList());

        assertThat(conditions).contains(Event.UUID + "=?");
        assertThat(parameters).contains("test-uuid");
    }

    @Test
    void buildQuery_withSource_shouldIncludeServiceConditions() {
        final EventQueryCondition condition = new EventQueryCondition();
        final Source source = new Source();
        source.setService("order-service");
        source.setServiceInstance("instance-1");
        source.setEndpoint("/orders");
        condition.setSource(source);

        final Tuple2<Stream<String>, Stream<Object>> result = dao.buildQuery(condition);
        final List<String> conditions = result._1().collect(Collectors.toList());
        final List<Object> parameters = result._2().collect(Collectors.toList());

        assertThat(conditions).contains(Event.SERVICE + "=?");
        assertThat(conditions).contains(Event.SERVICE_INSTANCE + "=?");
        assertThat(conditions).contains(Event.ENDPOINT + "=?");
        assertThat(parameters).contains("order-service", "instance-1", "/orders");
    }

    @Test
    void buildQuery_withEventType_shouldIncludeTypeCondition() {
        final EventQueryCondition condition = new EventQueryCondition();
        condition.setType(EventType.Normal);

        final Tuple2<Stream<String>, Stream<Object>> result = dao.buildQuery(condition);
        final List<String> conditions = result._1().collect(Collectors.toList());
        final List<Object> parameters = result._2().collect(Collectors.toList());

        assertThat(conditions).contains(Event.TYPE + "=?");
        assertThat(parameters).contains(EventType.Normal.name());
    }

    @Test
    void buildQuery_withTableColumnConditionOnlyOnce() {
        final EventQueryCondition condition = new EventQueryCondition();
        condition.setUuid("uuid-1");

        final Tuple2<Stream<String>, Stream<Object>> result = dao.buildQuery(condition);
        final List<String> conditions = result._1().collect(Collectors.toList());

        final long tableColumnCount = conditions.stream()
            .filter(c -> c.equals(JDBCTableInstaller.TABLE_COLUMN + " = ?"))
            .count();
        assertThat(tableColumnCount).as("TABLE_COLUMN condition should appear exactly once").isEqualTo(1);
    }
}
