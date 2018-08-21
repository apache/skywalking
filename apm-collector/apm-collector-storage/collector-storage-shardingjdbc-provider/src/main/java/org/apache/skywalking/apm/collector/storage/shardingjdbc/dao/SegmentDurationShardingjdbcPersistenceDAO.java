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

import java.util.*;

import com.google.gson.Gson;
import org.apache.skywalking.apm.collector.client.shardingjdbc.*;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentDurationPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcSqlEntity;
import org.apache.skywalking.apm.collector.storage.table.segment.*;
import org.slf4j.*;

/**
 * @author linjiaqi
 */
public class SegmentDurationShardingjdbcPersistenceDAO extends ShardingjdbcDAO implements ISegmentDurationPersistenceDAO<ShardingjdbcSqlEntity, ShardingjdbcSqlEntity, SegmentDuration> {

    private static final Logger logger = LoggerFactory.getLogger(SegmentDurationShardingjdbcPersistenceDAO.class);

    private final Gson gson = new Gson();

    public SegmentDurationShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override public SegmentDuration get(String id) {
        return null;
    }

    @Override public ShardingjdbcSqlEntity prepareBatchInsert(SegmentDuration data) {
        logger.debug("segment cost prepareBatchInsert, getApplicationId: {}", data.getId());
        ShardingjdbcSqlEntity entity = new ShardingjdbcSqlEntity();
        Map<String, Object> target = new HashMap<>();
        target.put(SegmentDurationTable.ID.getName(), data.getId());
        target.put(SegmentDurationTable.SEGMENT_ID.getName(), data.getSegmentId());
        target.put(SegmentDurationTable.APPLICATION_ID.getName(), data.getApplicationId());
        target.put(SegmentDurationTable.SERVICE_NAME.getName(), gson.toJson(data.getServiceName()));
        target.put(SegmentDurationTable.DURATION.getName(), data.getDuration());
        target.put(SegmentDurationTable.START_TIME.getName(), data.getStartTime());
        target.put(SegmentDurationTable.END_TIME.getName(), data.getEndTime());
        target.put(SegmentDurationTable.IS_ERROR.getName(), data.getIsError());
        target.put(SegmentDurationTable.TIME_BUCKET.getName(), data.getTimeBucket());
        logger.debug("segment cost source: {}", target.toString());

        String sql = SqlBuilder.buildBatchInsertSql(SegmentDurationTable.TABLE, target.keySet());
        entity.setSql(sql);
        entity.setParams(target.values().toArray(new Object[0]));
        return entity;
    }

    @Override public ShardingjdbcSqlEntity prepareBatchUpdate(SegmentDuration data) {
        return null;
    }

    @Override public void deleteHistory(Long timeBucketBefore) {
        ShardingjdbcClient client = getClient();
        
        String dynamicSql = "delete from {0} where {1} <= ?";
        String sql = SqlBuilder.buildSql(dynamicSql, SegmentDurationTable.TABLE, SegmentDurationTable.TIME_BUCKET.getName());
        
        Object[] params = new Object[] {timeBucketBefore};
        
        try {
            client.execute(sql, params);
            logger.info("Deleted history rows from {} table.", SegmentDurationTable.TABLE);
        } catch (ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
