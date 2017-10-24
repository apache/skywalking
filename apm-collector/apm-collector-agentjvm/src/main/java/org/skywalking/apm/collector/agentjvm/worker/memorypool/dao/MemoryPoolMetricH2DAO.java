/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agentjvm.worker.memorypool.dao;

import java.util.HashMap;
import java.util.Map;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.jvm.MemoryPoolMetricTable;
import org.skywalking.apm.collector.storage.h2.SqlBuilder;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.collector.storage.h2.define.H2SqlEntity;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;

/**
 * @author peng-yongsheng, clevertension
 */
public class MemoryPoolMetricH2DAO extends H2DAO implements IMemoryPoolMetricDAO, IPersistenceDAO<H2SqlEntity, H2SqlEntity> {
    @Override public Data get(String id, DataDefine dataDefine) {
        return null;
    }

    @Override public H2SqlEntity prepareBatchInsert(Data data) {
        H2SqlEntity entity = new H2SqlEntity();
        Map<String, Object> source = new HashMap<>();
        source.put(MemoryPoolMetricTable.COLUMN_ID, data.getDataString(0));
        source.put(MemoryPoolMetricTable.COLUMN_INSTANCE_ID, data.getDataInteger(0));
        source.put(MemoryPoolMetricTable.COLUMN_POOL_TYPE, data.getDataInteger(1));
        source.put(MemoryPoolMetricTable.COLUMN_INIT, data.getDataLong(0));
        source.put(MemoryPoolMetricTable.COLUMN_MAX, data.getDataLong(1));
        source.put(MemoryPoolMetricTable.COLUMN_USED, data.getDataLong(2));
        source.put(MemoryPoolMetricTable.COLUMN_COMMITTED, data.getDataLong(3));
        source.put(MemoryPoolMetricTable.COLUMN_TIME_BUCKET, data.getDataLong(4));

        String sql = SqlBuilder.buildBatchInsertSql(MemoryPoolMetricTable.TABLE, source.keySet());
        entity.setSql(sql);
        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override public H2SqlEntity prepareBatchUpdate(Data data) {
        return null;
    }
}
