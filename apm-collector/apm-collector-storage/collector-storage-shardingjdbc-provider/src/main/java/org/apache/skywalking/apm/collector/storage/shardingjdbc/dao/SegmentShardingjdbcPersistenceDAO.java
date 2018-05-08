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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao;

import java.util.HashMap;
import java.util.Map;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcSqlEntity;
import org.apache.skywalking.apm.collector.storage.table.segment.Segment;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class SegmentShardingjdbcPersistenceDAO extends ShardingjdbcDAO implements ISegmentPersistenceDAO<ShardingjdbcSqlEntity, ShardingjdbcSqlEntity, Segment> {

    private static final Logger logger = LoggerFactory.getLogger(SegmentShardingjdbcPersistenceDAO.class);

    public SegmentShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override public Segment get(String id) {
        return null;
    }

    @Override public ShardingjdbcSqlEntity prepareBatchInsert(Segment data) {
        Map<String, Object> target = new HashMap<>();
        ShardingjdbcSqlEntity entity = new ShardingjdbcSqlEntity();
        target.put(SegmentTable.ID.getName(), data.getId());
        target.put(SegmentTable.DATA_BINARY.getName(), data.getDataBinary());
        target.put(SegmentTable.TIME_BUCKET.getName(), data.getTimeBucket());
        logger.debug("segment source: {}", target.toString());

        String sql = SqlBuilder.buildBatchInsertSql(SegmentTable.TABLE, target.keySet());
        entity.setSql(sql);
        entity.setParams(target.values().toArray(new Object[0]));
        return entity;
    }

    @Override public ShardingjdbcSqlEntity prepareBatchUpdate(Segment data) {
        return null;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        ShardingjdbcClient client = getClient();
        
        String dynamicSql = "delete from {0} where {1} >= ? and {1} <= ?";
        String sql = SqlBuilder.buildSql(dynamicSql, SegmentTable.TABLE, SegmentTable.TIME_BUCKET.getName());
        
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        Object[] params = new Object[] {startTimeBucket, endTimeBucket};
        
        try {
            client.execute(sql, params);
            logger.info("Deleted history rows from {} table.", SegmentTable.TABLE);
        } catch (ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
