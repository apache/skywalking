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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.pool.SessionDataSetWrapper;
import org.apache.iotdb.session.pool.SessionPool;
import org.apache.iotdb.tsfile.read.common.Field;
import org.apache.iotdb.tsfile.read.common.RowRecord;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.storage.query.ITopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

@Slf4j
@RequiredArgsConstructor
public class IoTDBTopNRecordsQueryDAO implements ITopNRecordsQueryDAO {
    private final IoTDBClient client;

    @Override
    public List<SelectedRecord> readSampledRecords(TopNCondition condition, String valueColumnName, Duration duration) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select ").append(TopN.STATEMENT).append(", ").append(TopN.TRACE_ID).append(", ").append(valueColumnName)
                .append(" from ");
        query = client.addModelPath(query, condition.getName());
        Map<String, String> indexAndValueMap = new HashMap<>();
        if (StringUtil.isNotEmpty(condition.getParentService())) {
            final String serviceId = IDManager.ServiceID.buildId(condition.getParentService(), condition.isNormal());
            indexAndValueMap.put(IoTDBClient.SERVICE_ID_IDX, serviceId);
        }
        query = client.addQueryIndexValue(condition.getName(), query, indexAndValueMap);
        query.append(" where 1=1");
        if (Objects.nonNull(duration)) {
            query.append(" and ").append(IoTDBClient.TIME).append(" >= ").append(TimeBucket.getTimestamp(duration.getStartTimeBucketInSec()));
            query.append(" and ").append(IoTDBClient.TIME).append(" <= ").append(TimeBucket.getTimestamp(duration.getEndTimeBucketInSec()));
        }
        query.append(IoTDBClient.ALIGN_BY_DEVICE);
        // IoTDB doesn't support the query contains "1=1" and "*" at the meantime.
        String queryString = query.toString();
        queryString = queryString.replace("1=1 and ", "");

        SessionPool sessionPool = client.getSessionPool();
        SessionDataSetWrapper wrapper = null;
        List<SelectedRecord> records = new ArrayList<>();
        try {
            wrapper = sessionPool.executeQueryStatement(queryString);
            if (log.isDebugEnabled()) {
                log.debug("SQL: {}, columnNames: {}", queryString, wrapper.getColumnNames());
            }

            while (wrapper.hasNext()) {
                SelectedRecord record = new SelectedRecord();
                RowRecord rowRecord = wrapper.next();
                List<Field> fields = rowRecord.getFields();
                record.setName(fields.get(1).getStringValue());
                record.setRefId(fields.get(2).getStringValue());
                record.setId(record.getId());
                record.setValue(String.valueOf(fields.get(3).getObjectValue(fields.get(1).getDataType())));
                records.add(record);
            }
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            throw new IOException(e.getMessage() + System.lineSeparator() + "SQL Statement: " + queryString, e);
        } finally {
            sessionPool.closeResultSet(wrapper);
        }

        // resort by self, because of the select query result order by time.
        if (Order.DES.equals(condition.getOrder())) {
            records.sort((SelectedRecord s1, SelectedRecord s2) ->
                    Long.compare(Long.parseLong(s2.getValue()), Long.parseLong(s1.getValue())));
        } else {
            records.sort(Comparator.comparingLong((SelectedRecord s) -> Long.parseLong(s.getValue())));
        }
        return records;
    }
}
