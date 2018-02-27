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

    public AbstractGCMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final GCMetric h2DataToStreamData(ResultSet resultSet) throws SQLException {
        GCMetric gcMetric = new GCMetric();
        gcMetric.setId(resultSet.getString(GCMetricTable.COLUMN_ID));
        gcMetric.setMetricId(resultSet.getString(GCMetricTable.COLUMN_METRIC_ID));

        gcMetric.setInstanceId(resultSet.getInt(GCMetricTable.COLUMN_INSTANCE_ID));
        gcMetric.setPhrase(resultSet.getInt(GCMetricTable.COLUMN_PHRASE));

        gcMetric.setCount(resultSet.getLong(GCMetricTable.COLUMN_COUNT));
        gcMetric.setTimes(resultSet.getLong(GCMetricTable.COLUMN_TIMES));

        gcMetric.setTimeBucket(resultSet.getLong(GCMetricTable.COLUMN_TIME_BUCKET));

        return gcMetric;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(GCMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(GCMetricTable.COLUMN_ID, streamData.getId());
        source.put(GCMetricTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(GCMetricTable.COLUMN_INSTANCE_ID, streamData.getInstanceId());
        source.put(GCMetricTable.COLUMN_PHRASE, streamData.getPhrase());
        source.put(GCMetricTable.COLUMN_COUNT, streamData.getCount());
        source.put(GCMetricTable.COLUMN_TIMES, streamData.getTimes());
        source.put(GCMetricTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
