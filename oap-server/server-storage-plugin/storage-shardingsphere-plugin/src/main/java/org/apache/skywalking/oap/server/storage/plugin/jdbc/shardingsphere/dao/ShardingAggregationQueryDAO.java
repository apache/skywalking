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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao;

import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCAggregationQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.DurationWithinTTL;

import java.util.List;

public class ShardingAggregationQueryDAO extends JDBCAggregationQueryDAO {

    public ShardingAggregationQueryDAO(JDBCClient h2Client, TableHelper tableHelper) {
        super(h2Client, tableHelper);
    }

    @Override
    public List<SelectedRecord> sortMetrics(final TopNCondition metrics,
                                            final String valueColumnName,
                                            final Duration duration,
                                            List<KeyValue> additionalConditions) {

        return super.sortMetrics(
            metrics,
            valueColumnName,
            DurationWithinTTL.INSTANCE.getMetricDurationWithinTTL(duration),
            additionalConditions
        );
    }
}
