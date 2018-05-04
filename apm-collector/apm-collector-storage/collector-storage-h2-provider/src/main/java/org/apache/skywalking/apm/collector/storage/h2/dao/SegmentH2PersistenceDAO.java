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
import org.apache.skywalking.apm.collector.storage.dao.ISegmentPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.segment.Segment;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class SegmentH2PersistenceDAO extends H2DAO implements ISegmentPersistenceDAO<H2SqlEntity, H2SqlEntity, Segment> {

    private static final Logger logger = LoggerFactory.getLogger(SegmentH2PersistenceDAO.class);

    public SegmentH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override public Segment get(String id) {
        return null;
    }

    @Override public H2SqlEntity prepareBatchInsert(Segment data) {
        Map<String, Object> target = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        target.put(SegmentTable.ID.getName(), data.getId());
        target.put(SegmentTable.DATA_BINARY.getName(), data.getDataBinary());
        target.put(SegmentTable.TIME_BUCKET.getName(), data.getTimeBucket());
        logger.debug("segment source: {}", target.toString());

        String sql = SqlBuilder.buildBatchInsertSql(SegmentTable.TABLE, target.keySet());
        entity.setSql(sql);
        entity.setParams(target.values().toArray(new Object[0]));
        return entity;
    }

    @Override public H2SqlEntity prepareBatchUpdate(Segment data) {
        return null;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
    }
}
