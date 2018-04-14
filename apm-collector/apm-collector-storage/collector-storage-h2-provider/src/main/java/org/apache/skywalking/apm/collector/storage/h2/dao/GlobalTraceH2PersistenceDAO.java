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
import org.apache.skywalking.apm.collector.core.UnexpectedException;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.IGlobalTracePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.global.GlobalTrace;
import org.apache.skywalking.apm.collector.storage.table.global.GlobalTraceTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class GlobalTraceH2PersistenceDAO extends H2DAO implements IGlobalTracePersistenceDAO<H2SqlEntity, H2SqlEntity, GlobalTrace> {

    private static final Logger logger = LoggerFactory.getLogger(GlobalTraceH2PersistenceDAO.class);

    public GlobalTraceH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override public GlobalTrace get(String id) {
        throw new UnexpectedException("There is no need to merge stream data with database data.");
    }

    @Override public H2SqlEntity prepareBatchUpdate(GlobalTrace data) {
        throw new UnexpectedException("There is no need to merge stream data with database data.");
    }

    @Override public H2SqlEntity prepareBatchInsert(GlobalTrace data) {
        Map<String, Object> target = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        target.put(GlobalTraceTable.ID.getName(), data.getId());
        target.put(GlobalTraceTable.SEGMENT_ID.getName(), data.getSegmentId());
        target.put(GlobalTraceTable.TRACE_ID.getName(), data.getGlobalTraceId());
        target.put(GlobalTraceTable.TIME_BUCKET.getName(), data.getTimeBucket());
        logger.debug("global trace source: {}", target.toString());

        String sql = SqlBuilder.buildBatchInsertSql(GlobalTraceTable.TABLE, target.keySet());
        entity.setSql(sql);
        entity.setParams(target.values().toArray(new Object[0]));
        return entity;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
    }
}
