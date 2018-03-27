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
import org.apache.skywalking.apm.collector.storage.dao.ISegmentDurationPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentDuration;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentDurationTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class SegmentDurationH2PersistenceDAO extends H2DAO implements ISegmentDurationPersistenceDAO<H2SqlEntity, H2SqlEntity, SegmentDuration> {

    private final Logger logger = LoggerFactory.getLogger(SegmentDurationH2PersistenceDAO.class);

    public SegmentDurationH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override public SegmentDuration get(String id) {
        return null;
    }

    @Override public H2SqlEntity prepareBatchInsert(SegmentDuration data) {
        logger.debug("segment cost prepareBatchInsert, getApplicationId: {}", data.getId());
        H2SqlEntity entity = new H2SqlEntity();
        Map<String, Object> source = new HashMap<>();
        source.put(SegmentDurationTable.COLUMN_ID, data.getId());
        source.put(SegmentDurationTable.COLUMN_SEGMENT_ID, data.getSegmentId());
        source.put(SegmentDurationTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(SegmentDurationTable.COLUMN_SERVICE_NAME, data.getServiceName());
        source.put(SegmentDurationTable.COLUMN_DURATION, data.getDuration());
        source.put(SegmentDurationTable.COLUMN_START_TIME, data.getStartTime());
        source.put(SegmentDurationTable.COLUMN_END_TIME, data.getEndTime());
        source.put(SegmentDurationTable.COLUMN_IS_ERROR, data.getIsError());
        source.put(SegmentDurationTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        logger.debug("segment cost source: {}", source.toString());

        String sql = SqlBuilder.buildBatchInsertSql(SegmentDurationTable.TABLE, source.keySet());
        entity.setSql(sql);
        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override public H2SqlEntity prepareBatchUpdate(SegmentDuration data) {
        return null;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
    }
}
