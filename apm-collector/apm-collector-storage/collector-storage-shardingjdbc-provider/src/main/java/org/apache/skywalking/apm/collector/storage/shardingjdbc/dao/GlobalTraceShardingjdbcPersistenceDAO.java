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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.dao.IGlobalTracePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.AbstractPersistenceShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcSqlEntity;
import org.apache.skywalking.apm.collector.storage.table.global.GlobalTrace;
import org.apache.skywalking.apm.collector.storage.table.global.GlobalTraceTable;

/**
 * @author linjiaqi
 */
public class GlobalTraceShardingjdbcPersistenceDAO extends AbstractPersistenceShardingjdbcDAO<GlobalTrace> implements IGlobalTracePersistenceDAO<ShardingjdbcSqlEntity, ShardingjdbcSqlEntity, GlobalTrace> {

    public GlobalTraceShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }
    
    @Override protected String tableName() {
        return GlobalTraceTable.TABLE;
    }
    
    @Override protected GlobalTrace shardingjdbcDataToStreamData(ResultSet resultSet) throws SQLException {
        GlobalTrace globalTrace = new GlobalTrace();
        globalTrace.setSegmentId(resultSet.getString(GlobalTraceTable.SEGMENT_ID.getName()));
        globalTrace.setTraceId(resultSet.getString(GlobalTraceTable.TRACE_ID.getName()));
        globalTrace.setTimeBucket(resultSet.getLong(GlobalTraceTable.TIME_BUCKET.getName()));
        return globalTrace;
    }

    @Override protected Map<String, Object> streamDataToShardingjdbcData(GlobalTrace streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(GlobalTraceTable.SEGMENT_ID.getName(), streamData.getSegmentId());
        target.put(GlobalTraceTable.TRACE_ID.getName(), streamData.getTraceId());
        target.put(GlobalTraceTable.TIME_BUCKET.getName(), streamData.getTimeBucket());
        return target;
    }

    @Override protected String timeBucketColumnNameForDelete() {
        return GlobalTraceTable.TIME_BUCKET.getName();
    }

    @GraphComputingMetric(name = "/persistence/get/" + GlobalTraceTable.TABLE)
    @Override public GlobalTrace get(String id) {
        return super.get(id);
    }
}
