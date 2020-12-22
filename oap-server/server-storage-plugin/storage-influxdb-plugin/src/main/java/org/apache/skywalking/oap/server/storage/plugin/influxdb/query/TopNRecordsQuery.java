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
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.storage.query.ITopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
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
    public List<SelectedRecord> readSampledRecords(final TopNCondition condition,
                                                   final String valueColumnName,
                                                   final Duration duration) throws IOException {
        String function = InfluxConstants.SORT_ASC;
        // Have to re-sort here. Because the function, top()/bottom(), get the result ordered by the `time`.
        Comparator<SelectedRecord> comparator = ASCENDING;
        if (condition.getOrder().equals(Order.DES)) {
            function = InfluxConstants.SORT_DES;
            comparator = DESCENDING;
        }

        final WhereQueryImpl<SelectQueryImpl> query = select()
            .function(function, valueColumnName, condition.getTopN())
            .column(TopN.STATEMENT)
            .column(TopN.TRACE_ID)
            .from(client.getDatabase(), condition.getName())
            .where();

        query.and(gte(TopN.TIME_BUCKET, duration.getStartTimeBucketInSec()))
            .and(lte(TopN.TIME_BUCKET, duration.getEndTimeBucketInSec()));

        if (StringUtil.isNotEmpty(condition.getParentService())) {
            final String serviceId = IDManager.ServiceID.buildId(condition.getParentService(), condition.isNormal());
            query.and(eq(InfluxConstants.TagName.SERVICE_ID, serviceId));
        }

        QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), series);
        }
        if (series == null) {
            return Collections.emptyList();
        }

        final List<SelectedRecord> records = new ArrayList<>();
        series.getValues().forEach(values -> {
            SelectedRecord record = new SelectedRecord();
            record.setValue(String.valueOf(values.get(1)));
            record.setRefId((String) values.get(3));
            record.setId(record.getRefId());
            record.setName((String) values.get(2));
            records.add(record);
        });

        records.sort(comparator); // re-sort by self, because of the result order by time.
        return records;
    }

    private static final Comparator<SelectedRecord> ASCENDING = Comparator.comparingLong(
        a -> Long.parseLong(a.getValue()));

    private static final Comparator<SelectedRecord> DESCENDING = (a, b) -> Long.compare(
        Long.parseLong(b.getValue()), Long.parseLong(a.getValue()));
}
