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

package org.apache.skywalking.apm.collector.storage.h2.dao.gcmp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.GCMetric;
import org.apache.skywalking.apm.collector.storage.table.jvm.GCMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractGCMetricH2PersistenceDAO extends AbstractPersistenceH2DAO<GCMetric> {

    AbstractGCMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final GCMetric h2DataToStreamData(ResultSet resultSet) throws SQLException {
        GCMetric gcMetric = new GCMetric();
        gcMetric.setId(resultSet.getString(GCMetricTable.ID.getName()));
        gcMetric.setMetricId(resultSet.getString(GCMetricTable.METRIC_ID.getName()));

        gcMetric.setInstanceId(resultSet.getInt(GCMetricTable.INSTANCE_ID.getName()));
        gcMetric.setPhrase(resultSet.getInt(GCMetricTable.PHRASE.getName()));

        gcMetric.setCount(resultSet.getLong(GCMetricTable.COUNT.getName()));
        gcMetric.setTimes(resultSet.getLong(GCMetricTable.TIMES.getName()));

        gcMetric.setTimeBucket(resultSet.getLong(GCMetricTable.TIME_BUCKET.getName()));

        return gcMetric;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(GCMetric streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(GCMetricTable.ID.getName(), streamData.getId());
        target.put(GCMetricTable.METRIC_ID.getName(), streamData.getMetricId());

        target.put(GCMetricTable.INSTANCE_ID.getName(), streamData.getInstanceId());
        target.put(GCMetricTable.PHRASE.getName(), streamData.getPhrase());
        target.put(GCMetricTable.COUNT.getName(), streamData.getCount());
        target.put(GCMetricTable.TIMES.getName(), streamData.getTimes());
        target.put(GCMetricTable.TIME_BUCKET.getName(), streamData.getTimeBucket());

        return target;
    }
}
