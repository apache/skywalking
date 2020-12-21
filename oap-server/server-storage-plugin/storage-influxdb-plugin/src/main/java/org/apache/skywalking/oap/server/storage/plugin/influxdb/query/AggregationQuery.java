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

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.SelectSubQueryImpl;
import org.influxdb.querybuilder.WhereSubQueryImpl;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.lte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
public class AggregationQuery implements IAggregationQueryDAO {
    private static final Comparator<SelectedRecord> ASCENDING =
        Comparator.comparingLong(a -> Long.parseLong(a.getValue()));
    private static final Comparator<SelectedRecord> DESCENDING = (a, b) ->
        Long.compare(Long.parseLong(b.getValue()), Long.parseLong(a.getValue()));
    private final InfluxClient client;

    public AggregationQuery(InfluxClient client) {
        this.client = client;
    }

    @Override
    public List<SelectedRecord> sortMetrics(final TopNCondition condition,
                                            final String valueColumnName,
                                            final Duration duration,
                                            final List<KeyValue> additionalConditions) throws IOException {
        String measurement = condition.getName();

        // Have to re-sort here. Because the function, top()/bottom(), get the result ordered by the `time`.
        Comparator<SelectedRecord> comparator = DESCENDING;
        String functionName = InfluxConstants.SORT_DES;
        if (condition.getOrder().equals(Order.ASC)) {
            functionName = InfluxConstants.SORT_ASC;
            comparator = ASCENDING;
        }

        SelectQueryImpl query = select().function(functionName, "mean", condition.getTopN()).as("value")
                                        .column(InfluxConstants.TagName.ENTITY_ID)
                                        .from(client.getDatabase(), measurement);

        WhereSubQueryImpl<SelectSubQueryImpl<SelectQueryImpl>, SelectQueryImpl> where = select()
            .fromSubQuery(client.getDatabase())
            .mean(valueColumnName)
            .from(condition.getName())
            .where();
        if (additionalConditions != null) {
            additionalConditions.forEach(moreCondition ->
                                             where.and(eq(moreCondition.getKey(), moreCondition.getValue()))
            );
        }
        final SelectSubQueryImpl<SelectQueryImpl> subQuery = where
            .and(gte(InfluxClient.TIME, InfluxClient.timeIntervalTS(duration.getStartTimestamp())))
            .and(lte(InfluxClient.TIME, InfluxClient.timeIntervalTS(duration.getEndTimestamp())))
            .groupBy(InfluxConstants.TagName.ENTITY_ID);

        query.setSubQuery(subQuery);

        List<QueryResult.Series> series = client.queryForSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result set: {}", query.getCommand(), series);
        }
        if (series == null || series.isEmpty()) {
            return Collections.emptyList();
        }

        List<List<Object>> dataset = series.get(0).getValues();
        List<SelectedRecord> entities = Lists.newArrayListWithCapacity(dataset.size());
        dataset.forEach(values -> {
            final SelectedRecord entity = new SelectedRecord();
            entity.setId((String) values.get(2));
            entity.setValue(((Double) values.get(1)).longValue() + "");
            entities.add(entity);
        });

        entities.sort(comparator); // re-sort by self, because of the result order by time.
        return entities;
    }

}
