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
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.AbstractPersistenceShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcSqlEntity;
import org.apache.skywalking.apm.collector.storage.table.segment.Segment;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentTable;

/**
 * @author linjiaqi
 */
public class SegmentShardingjdbcPersistenceDAO extends AbstractPersistenceShardingjdbcDAO<Segment> implements ISegmentPersistenceDAO<ShardingjdbcSqlEntity, ShardingjdbcSqlEntity, Segment> {

    public SegmentShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }
    
    @Override protected String tableName() {
        return SegmentTable.TABLE;
    }
    
    @Override protected Segment shardingjdbcDataToStreamData(ResultSet resultSet) throws SQLException {
        Segment segment = new Segment();
        segment.setDataBinary(Base64.getDecoder().decode(resultSet.getString(SegmentTable.DATA_BINARY.getName())));
        segment.setTimeBucket(resultSet.getLong(SegmentTable.TIME_BUCKET.getName()));
        return segment;
    }

    @Override protected Map<String, Object> streamDataToShardingjdbcData(Segment streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(SegmentTable.DATA_BINARY.getName(), new String(Base64.getEncoder().encode(streamData.getDataBinary())));
        target.put(SegmentTable.TIME_BUCKET.getName(), streamData.getTimeBucket());
        return target;
    }

    @Override protected String timeBucketColumnNameForDelete() {
        return SegmentTable.TIME_BUCKET.getName();
    }

    @GraphComputingMetric(name = "/persistence/get/" + SegmentTable.TABLE)
    @Override public Segment get(String id) {
        return super.get(id);
    }
}
