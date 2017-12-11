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


package org.apache.skywalking.apm.collector.storage.h2.dao;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.IMemoryPoolMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetric;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetricTable;

/**
 * @author peng-yongsheng, clevertension
 */
public class MemoryPoolMetricH2PersistenceDAO extends H2DAO implements IMemoryPoolMetricPersistenceDAO<H2SqlEntity, H2SqlEntity, MemoryPoolMetric> {

    public MemoryPoolMetricH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override public MemoryPoolMetric get(String id) {
        return null;
    }

    @Override public H2SqlEntity prepareBatchInsert(MemoryPoolMetric data) {
        H2SqlEntity entity = new H2SqlEntity();
        Map<String, Object> source = new HashMap<>();
        source.put(MemoryPoolMetricTable.COLUMN_ID, data.getId());
        source.put(MemoryPoolMetricTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(MemoryPoolMetricTable.COLUMN_POOL_TYPE, data.getPoolType());
        source.put(MemoryPoolMetricTable.COLUMN_INIT, data.getInit());
        source.put(MemoryPoolMetricTable.COLUMN_MAX, data.getMax());
        source.put(MemoryPoolMetricTable.COLUMN_USED, data.getUsed());
        source.put(MemoryPoolMetricTable.COLUMN_COMMITTED, data.getCommitted());
        source.put(MemoryPoolMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        String sql = SqlBuilder.buildBatchInsertSql(MemoryPoolMetricTable.TABLE, source.keySet());
        entity.setSql(sql);
        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override public H2SqlEntity prepareBatchUpdate(MemoryPoolMetric data) {
        return null;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
    }
}
