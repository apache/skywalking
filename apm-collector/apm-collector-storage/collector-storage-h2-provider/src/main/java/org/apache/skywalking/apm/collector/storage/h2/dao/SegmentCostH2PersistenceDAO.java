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
import org.apache.skywalking.apm.collector.storage.dao.ISegmentCostPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentCost;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentCostTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class SegmentCostH2PersistenceDAO extends H2DAO implements ISegmentCostPersistenceDAO<H2SqlEntity, H2SqlEntity, SegmentCost> {

    private final Logger logger = LoggerFactory.getLogger(SegmentCostH2PersistenceDAO.class);

    public SegmentCostH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override public SegmentCost get(String id) {
        return null;
    }

    @Override public H2SqlEntity prepareBatchInsert(SegmentCost data) {
        logger.debug("segment cost prepareBatchInsert, getId: {}", data.getId());
        H2SqlEntity entity = new H2SqlEntity();
        Map<String, Object> source = new HashMap<>();
        source.put(SegmentCostTable.COLUMN_ID, data.getId());
        source.put(SegmentCostTable.COLUMN_SEGMENT_ID, data.getSegmentId());
        source.put(SegmentCostTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(SegmentCostTable.COLUMN_SERVICE_NAME, data.getServiceName());
        source.put(SegmentCostTable.COLUMN_COST, data.getCost());
        source.put(SegmentCostTable.COLUMN_START_TIME, data.getStartTime());
        source.put(SegmentCostTable.COLUMN_END_TIME, data.getEndTime());
        source.put(SegmentCostTable.COLUMN_IS_ERROR, data.getIsError());
        source.put(SegmentCostTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        logger.debug("segment cost source: {}", source.toString());

        String sql = SqlBuilder.buildBatchInsertSql(SegmentCostTable.TABLE, source.keySet());
        entity.setSql(sql);
        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override public H2SqlEntity prepareBatchUpdate(SegmentCost data) {
        return null;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
    }
}
