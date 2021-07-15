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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.query;

import lombok.extern.slf4j.Slf4j;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.pool.SessionDataSetWrapper;
import org.apache.iotdb.session.pool.SessionPool;
import org.apache.iotdb.tsfile.read.common.Field;
import org.apache.iotdb.tsfile.read.common.RowRecord;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.storage.query.ITopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
public class IoTDBTopNRecordsQueryDAO implements ITopNRecordsQueryDAO {
    private final IoTDBClient client;

    public IoTDBTopNRecordsQueryDAO(IoTDBClient client) {
        this.client = client;
    }

    @Override
    public List<SelectedRecord> readSampledRecords(TopNCondition condition, String valueColumnName, Duration duration) throws IOException {
        StringBuilder query = new StringBuilder();
        String selectFunc;
        Comparator<SelectedRecord> comparator;
        if (condition.getOrder().equals(Order.DES)) {
            selectFunc = "top_k";
            comparator = DESCENDING;
        } else {
            selectFunc = "bottom_k";
            comparator = ASCENDING;
        }
        query.append(String.format("select %s(%s, 'k'='%d')", selectFunc, valueColumnName, condition.getTopN()))
                .append(" from ").append(client.getStorageGroup()).append(IoTDBClient.DOT).append(condition.getName())
                .append(" where");
        if (StringUtil.isNotEmpty(condition.getParentService())) {
            final String serviceId = IDManager.ServiceID.buildId(condition.getParentService(), condition.isNormal());
            query.append(" service_id = '").append(serviceId).append("' and");
        }
        query.append(String.format(" %s >= %d and %s<= %d",
                TopN.TIME_BUCKET, duration.getStartTimeBucketInSec(), TopN.TIME_BUCKET, duration.getEndTimeBucketInSec()));
        List<Long> valueList = client.queryWithSelect(condition.getName(), query.toString());

        query = new StringBuilder();
        query.append(String.format("select %s, %s, %s from", TopN.STATEMENT, TopN.TRACE_ID, valueColumnName))
                .append(client.getStorageGroup()).append(IoTDBClient.DOT).append(condition.getName())
                .append(" where ").append(valueColumnName).append(" in (");
        for (int i = 0; i < valueList.size(); i++) {
            if (i == 0) {
                query.append(valueList.get(i));
            } else {
                query.append(", ").append(valueList.get(i));
            }
        }
        query.append(") and");
        if (StringUtil.isNotEmpty(condition.getParentService())) {
            final String serviceId = IDManager.ServiceID.buildId(condition.getParentService(), condition.isNormal());
            query.append(" service_id = '").append(serviceId).append("' and");
        }
        query.append(String.format(" %s >= %d and %s<= %d",
                TopN.TIME_BUCKET, duration.getStartTimeBucketInSec(), TopN.TIME_BUCKET, duration.getEndTimeBucketInSec()));

        List<SelectedRecord> records = new ArrayList<>();
        SessionPool sessionPool = client.getSessionPool();
        SessionDataSetWrapper wrapper = null;
        try {
            if (sessionPool.checkTimeseriesExists(client.getStorageGroup() + IoTDBClient.DOT + condition.getName())) {
                return records;
            }
            wrapper = sessionPool.executeQueryStatement(query.toString());
            if (log.isDebugEnabled()) {
                log.debug("SQL: {} result: {}", query, wrapper);
            }
            while (wrapper.hasNext()) {
                RowRecord rowRecord = wrapper.next();
                List<Field> fields = rowRecord.getFields();
                SelectedRecord record = new SelectedRecord();
                record.setName(fields.get(1).getStringValue());
                record.setRefId(fields.get(2).getStringValue());
                record.setId(record.getRefId());
                record.setValue(fields.get(3).getStringValue());
                records.add(record);
            }
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            throw new IOException(e);
        } finally {
            sessionPool.closeResultSet(wrapper);
        }
        // resort by self, because of the select query result order by time.
        records.sort(comparator);
        return records;
    }

    private static final Comparator<SelectedRecord> ASCENDING = Comparator.comparingLong(
            a -> ((Number) Double.parseDouble(a.getValue())).longValue());

    private static final Comparator<SelectedRecord> DESCENDING = (a, b) -> Long.compare(
            ((Number) Double.parseDouble(b.getValue())).longValue(), ((Number) Double.parseDouble(a.getValue())).longValue());
}
