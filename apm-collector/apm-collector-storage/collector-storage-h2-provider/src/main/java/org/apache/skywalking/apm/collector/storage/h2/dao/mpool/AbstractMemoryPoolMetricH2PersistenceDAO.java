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

package org.apache.skywalking.apm.collector.storage.h2.dao.mpool;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetric;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractMemoryPoolMetricH2PersistenceDAO extends AbstractPersistenceH2DAO<MemoryPoolMetric> {

    public AbstractMemoryPoolMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final MemoryPoolMetric h2DataToStreamData(ResultSet resultSet) throws SQLException {
        MemoryPoolMetric memoryPoolMetric = new MemoryPoolMetric();
        memoryPoolMetric.setId(resultSet.getString(MemoryPoolMetricTable.COLUMN_ID));
        memoryPoolMetric.setMetricId(resultSet.getString(MemoryPoolMetricTable.COLUMN_METRIC_ID));

        memoryPoolMetric.setInstanceId(resultSet.getInt(MemoryPoolMetricTable.COLUMN_INSTANCE_ID));
        memoryPoolMetric.setPoolType(resultSet.getInt(MemoryPoolMetricTable.COLUMN_POOL_TYPE));

        memoryPoolMetric.setInit(resultSet.getLong(MemoryPoolMetricTable.COLUMN_INIT));
        memoryPoolMetric.setMax(resultSet.getLong(MemoryPoolMetricTable.COLUMN_MAX));
        memoryPoolMetric.setUsed(resultSet.getLong(MemoryPoolMetricTable.COLUMN_USED));
        memoryPoolMetric.setCommitted(resultSet.getLong(MemoryPoolMetricTable.COLUMN_COMMITTED));
        memoryPoolMetric.setTimes(resultSet.getLong(MemoryPoolMetricTable.COLUMN_TIMES));

        memoryPoolMetric.setTimeBucket(resultSet.getLong(MemoryPoolMetricTable.COLUMN_TIME_BUCKET));
        return memoryPoolMetric;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(MemoryPoolMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(MemoryPoolMetricTable.COLUMN_ID, streamData.getId());
        source.put(MemoryPoolMetricTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(MemoryPoolMetricTable.COLUMN_INSTANCE_ID, streamData.getInstanceId());
        source.put(MemoryPoolMetricTable.COLUMN_POOL_TYPE, streamData.getPoolType());
        source.put(MemoryPoolMetricTable.COLUMN_INIT, streamData.getInit());
        source.put(MemoryPoolMetricTable.COLUMN_MAX, streamData.getMax());
        source.put(MemoryPoolMetricTable.COLUMN_USED, streamData.getUsed());
        source.put(MemoryPoolMetricTable.COLUMN_COMMITTED, streamData.getCommitted());
        source.put(MemoryPoolMetricTable.COLUMN_TIMES, streamData.getTimes());
        source.put(MemoryPoolMetricTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
