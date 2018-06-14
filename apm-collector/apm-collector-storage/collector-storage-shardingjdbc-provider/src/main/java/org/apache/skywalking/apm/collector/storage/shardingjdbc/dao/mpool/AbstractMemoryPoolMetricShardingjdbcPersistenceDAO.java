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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.mpool;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.AbstractPersistenceShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetric;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetricTable;

/**
 * @author linjiaqi
 */
public abstract class AbstractMemoryPoolMetricShardingjdbcPersistenceDAO extends AbstractPersistenceShardingjdbcDAO<MemoryPoolMetric> {

    AbstractMemoryPoolMetricShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }
    
    @Override
    protected final String timeBucketColumnNameForDelete() {
        return MemoryPoolMetricTable.TIME_BUCKET.getName();
    }

    @Override protected final MemoryPoolMetric shardingjdbcDataToStreamData(ResultSet resultSet) throws SQLException {
        MemoryPoolMetric memoryPoolMetric = new MemoryPoolMetric();
        memoryPoolMetric.setId(resultSet.getString(MemoryPoolMetricTable.ID.getName()));
        memoryPoolMetric.setMetricId(resultSet.getString(MemoryPoolMetricTable.METRIC_ID.getName()));

        memoryPoolMetric.setInstanceId(resultSet.getInt(MemoryPoolMetricTable.INSTANCE_ID.getName()));
        memoryPoolMetric.setPoolType(resultSet.getInt(MemoryPoolMetricTable.POOL_TYPE.getName()));

        memoryPoolMetric.setInit(resultSet.getLong(MemoryPoolMetricTable.INIT.getName()));
        memoryPoolMetric.setMax(resultSet.getLong(MemoryPoolMetricTable.MAX.getName()));
        memoryPoolMetric.setUsed(resultSet.getLong(MemoryPoolMetricTable.USED.getName()));
        memoryPoolMetric.setCommitted(resultSet.getLong(MemoryPoolMetricTable.COMMITTED.getName()));
        memoryPoolMetric.setTimes(resultSet.getLong(MemoryPoolMetricTable.TIMES.getName()));

        memoryPoolMetric.setTimeBucket(resultSet.getLong(MemoryPoolMetricTable.TIME_BUCKET.getName()));
        return memoryPoolMetric;
    }

    @Override protected final Map<String, Object> streamDataToShardingjdbcData(MemoryPoolMetric streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(MemoryPoolMetricTable.ID.getName(), streamData.getId());
        target.put(MemoryPoolMetricTable.METRIC_ID.getName(), streamData.getMetricId());

        target.put(MemoryPoolMetricTable.INSTANCE_ID.getName(), streamData.getInstanceId());
        target.put(MemoryPoolMetricTable.POOL_TYPE.getName(), streamData.getPoolType());
        target.put(MemoryPoolMetricTable.INIT.getName(), streamData.getInit());
        target.put(MemoryPoolMetricTable.MAX.getName(), streamData.getMax());
        target.put(MemoryPoolMetricTable.USED.getName(), streamData.getUsed());
        target.put(MemoryPoolMetricTable.COMMITTED.getName(), streamData.getCommitted());
        target.put(MemoryPoolMetricTable.TIMES.getName(), streamData.getTimes());
        target.put(MemoryPoolMetricTable.TIME_BUCKET.getName(), streamData.getTimeBucket());

        return target;
    }
    
    @GraphComputingMetric(name = "/persistence/get/" + MemoryPoolMetricTable.TABLE)
    @Override public final MemoryPoolMetric get(String id) {
        return super.get(id);
    }
}
