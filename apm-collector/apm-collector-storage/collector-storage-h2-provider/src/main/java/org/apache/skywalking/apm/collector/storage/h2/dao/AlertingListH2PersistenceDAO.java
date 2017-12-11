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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.storage.dao.IAlertingListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.alerting.AlertingList;
import org.apache.skywalking.apm.collector.storage.table.alerting.AlertingListTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class AlertingListH2PersistenceDAO extends H2DAO implements IAlertingListPersistenceDAO<H2SqlEntity, H2SqlEntity, AlertingList> {

    private final Logger logger = LoggerFactory.getLogger(AlertingListH2PersistenceDAO.class);

    private static final String GET_SQL = "select * from {0} where {1} = ?";

    public AlertingListH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override public AlertingList get(String id) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SQL, AlertingListTable.TABLE, AlertingListTable.COLUMN_ID);
        Object[] params = new Object[] {id};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                AlertingList alertingList = new AlertingList(id);
                alertingList.setLayer(rs.getInt(AlertingListTable.COLUMN_LAYER));
                alertingList.setLayerId(rs.getInt(AlertingListTable.COLUMN_LAYER_ID));
                alertingList.setFirstTimeBucket(rs.getLong(AlertingListTable.COLUMN_FIRST_TIME_BUCKET));
                alertingList.setLastTimeBucket(rs.getLong(AlertingListTable.COLUMN_LAST_TIME_BUCKET));
                alertingList.setExpected(rs.getInt(AlertingListTable.COLUMN_EXPECTED));
                alertingList.setActual(rs.getInt(AlertingListTable.COLUMN_ACTUAL));
                alertingList.setValid(rs.getBoolean(AlertingListTable.COLUMN_VALID));

                return alertingList;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override public H2SqlEntity prepareBatchInsert(AlertingList data) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(AlertingListTable.COLUMN_LAYER, data.getLayer());
        source.put(AlertingListTable.COLUMN_LAYER_ID, data.getLayerId());
        source.put(AlertingListTable.COLUMN_FIRST_TIME_BUCKET, data.getFirstTimeBucket());
        source.put(AlertingListTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());
        source.put(AlertingListTable.COLUMN_EXPECTED, data.getExpected());
        source.put(AlertingListTable.COLUMN_ACTUAL, data.getActual());
        source.put(AlertingListTable.COLUMN_VALID, data.getValid());

        String sql = SqlBuilder.buildBatchInsertSql(AlertingListTable.TABLE, source.keySet());
        entity.setSql(sql);
        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override public H2SqlEntity prepareBatchUpdate(AlertingList data) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(AlertingListTable.COLUMN_LAYER, data.getLayer());
        source.put(AlertingListTable.COLUMN_LAYER_ID, data.getLayerId());
        source.put(AlertingListTable.COLUMN_FIRST_TIME_BUCKET, data.getFirstTimeBucket());
        source.put(AlertingListTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());
        source.put(AlertingListTable.COLUMN_EXPECTED, data.getExpected());
        source.put(AlertingListTable.COLUMN_ACTUAL, data.getActual());
        source.put(AlertingListTable.COLUMN_VALID, data.getValid());
        String sql = SqlBuilder.buildBatchUpdateSql(AlertingListTable.TABLE, source.keySet(), AlertingListTable.COLUMN_ID);
        entity.setSql(sql);
        List<Object> values = new ArrayList<>(source.values());
        values.add(data.getId());
        entity.setParams(values.toArray(new Object[0]));
        return entity;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {

    }
}
