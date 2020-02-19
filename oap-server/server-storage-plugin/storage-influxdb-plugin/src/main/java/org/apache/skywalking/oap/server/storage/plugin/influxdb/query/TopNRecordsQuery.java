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

package org.apache.skywalking.oap.server.storage.plugin.influxdb.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.query.entity.Order;
import org.apache.skywalking.oap.server.core.query.entity.TopNRecord;
import org.apache.skywalking.oap.server.core.storage.query.ITopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.base.RecordDAO;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.WhereQueryImpl;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.lte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
public class TopNRecordsQuery implements ITopNRecordsQueryDAO {
    private final InfluxClient client;

    public TopNRecordsQuery(InfluxClient client) {
        this.client = client;
    }

    @Override
    public List<TopNRecord> getTopNRecords(long startSecondTB, long endSecondTB, String metricName,
                                           int serviceId, int topN, Order order) throws IOException {
        String function = "bottom";
        // Have to re-sort here. Because the function, top()/bottom(), get the result ordered by the `time`.
        Comparator<TopNRecord> comparator = Comparator.comparingLong(TopNRecord::getLatency);
        if (order.equals(Order.DES)) {
            function = "top";
            comparator = (a, b) -> Long.compare(b.getLatency(), a.getLatency());
        }

        WhereQueryImpl query = select()
            .function(function, TopN.LATENCY, topN)
            .column(TopN.STATEMENT)
            .column(TopN.TRACE_ID)
            .from(client.getDatabase(), metricName)
            .where()
            .and(gte(TopN.TIME_BUCKET, startSecondTB))
            .and(lte(TopN.TIME_BUCKET, endSecondTB));

        if (serviceId != Const.NONE) {
            query.and(eq(RecordDAO.TAG_SERVICE_ID, String.valueOf(serviceId)));
        }

        QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), series);
        }
        if (series == null) {
            return Collections.emptyList();
        }

        final List<TopNRecord> records = new ArrayList<>();
        series.getValues().forEach(values -> {
            TopNRecord record = new TopNRecord();
            record.setLatency((long) values.get(1));
            record.setTraceId((String) values.get(3));
            record.setStatement((String) values.get(2));
            records.add(record);
        });

        Collections.sort(records, comparator); // re-sort by self, because of the result order by time.
        return records;
    }
}
