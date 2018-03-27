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

package org.apache.skywalking.apm.collector.storage.h2.dao.memory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryMetric;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractMemoryMetricH2PersistenceDAO extends AbstractPersistenceH2DAO<MemoryMetric> {

    public AbstractMemoryMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final MemoryMetric h2DataToStreamData(ResultSet resultSet) throws SQLException {
        MemoryMetric memoryMetric = new MemoryMetric();
        memoryMetric.setId(resultSet.getString(MemoryMetricTable.COLUMN_ID));
        memoryMetric.setMetricId(resultSet.getString(MemoryMetricTable.COLUMN_METRIC_ID));

        memoryMetric.setInstanceId(resultSet.getInt(MemoryMetricTable.COLUMN_INSTANCE_ID));
        memoryMetric.setIsHeap(resultSet.getInt(MemoryMetricTable.COLUMN_IS_HEAP));

        memoryMetric.setInit(resultSet.getLong(MemoryMetricTable.COLUMN_INIT));
        memoryMetric.setMax(resultSet.getLong(MemoryMetricTable.COLUMN_MAX));
        memoryMetric.setUsed(resultSet.getLong(MemoryMetricTable.COLUMN_USED));
        memoryMetric.setCommitted(resultSet.getLong(MemoryMetricTable.COLUMN_COMMITTED));
        memoryMetric.setTimes(resultSet.getLong(MemoryMetricTable.COLUMN_TIMES));

        memoryMetric.setTimeBucket(resultSet.getLong(MemoryMetricTable.COLUMN_TIME_BUCKET));
        return memoryMetric;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(MemoryMetric streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(MemoryMetricTable.COLUMN_ID, streamData.getId());
        source.put(MemoryMetricTable.COLUMN_METRIC_ID, streamData.getMetricId());

        source.put(MemoryMetricTable.COLUMN_INSTANCE_ID, streamData.getInstanceId());
        source.put(MemoryMetricTable.COLUMN_IS_HEAP, streamData.getIsHeap());
        source.put(MemoryMetricTable.COLUMN_INIT, streamData.getInit());
        source.put(MemoryMetricTable.COLUMN_MAX, streamData.getMax());
        source.put(MemoryMetricTable.COLUMN_USED, streamData.getUsed());
        source.put(MemoryMetricTable.COLUMN_COMMITTED, streamData.getCommitted());
        source.put(MemoryMetricTable.COLUMN_TIMES, streamData.getTimes());
        source.put(MemoryMetricTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());

        return source;
    }
}
