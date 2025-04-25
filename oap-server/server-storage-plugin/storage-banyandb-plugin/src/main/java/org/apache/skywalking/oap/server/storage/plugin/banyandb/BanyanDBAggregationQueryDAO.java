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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import com.google.common.collect.ImmutableSet;
import org.apache.skywalking.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.banyandb.v1.client.DataPoint;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.banyandb.v1.client.TopNQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.util.ByteUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BanyanDBAggregationQueryDAO extends AbstractBanyanDBDAO implements IAggregationQueryDAO {
    private static final Set<String> TAGS = ImmutableSet.of(Metrics.ENTITY_ID);

    public BanyanDBAggregationQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<SelectedRecord> sortMetrics(TopNCondition condition, String valueColumnName, Duration duration, List<KeyValue> additionalConditions) throws IOException {
        final boolean isColdStage = duration != null && duration.isColdStage();
        final String modelName = condition.getName();
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(modelName, duration.getStep());
        if (schema == null) {
            throw new IOException("schema is not registered");
        }

        MetadataRegistry.ColumnSpec spec = schema.getSpec(valueColumnName);
        if (spec == null) {
            throw new IOException("field spec is not registered");
        }

        // BanyanDB server-side TopN support for metrics pre-aggregation.
        if (schema.getTopNSpec() != null && CollectionUtils.isEmpty(condition.getAttributes())) {
            // 1) no additional conditions
            // 2) additional conditions are all group by tags
            if (CollectionUtils.isEmpty(additionalConditions) ||
                    additionalConditions.stream().map(KeyValue::getKey).collect(Collectors.toSet())
                            .equals(ImmutableSet.copyOf(schema.getTopNSpec().getGroupByTagNamesList()))) {
                return serverSideTopN(isColdStage, condition, schema, spec, getTimestampRange(duration), additionalConditions);
            }
        }

        return directMetricsTopN(isColdStage, condition, schema, valueColumnName, spec, getTimestampRange(duration), additionalConditions);
    }

    //todo: query cold stage
    List<SelectedRecord> serverSideTopN(boolean coldStage, TopNCondition condition, MetadataRegistry.Schema schema, MetadataRegistry.ColumnSpec valueColumnSpec,
                                        TimestampRange timestampRange, List<KeyValue> additionalConditions) throws IOException {
        TopNQueryResponse resp = null;
        if (condition.getOrder() == Order.DES) {
            resp = topNQueryDebuggable(coldStage, schema, timestampRange, condition.getTopN(), AbstractQuery.Sort.DESC, additionalConditions, condition.getAttributes());
        } else {
            resp = topNQueryDebuggable(coldStage, schema, timestampRange, condition.getTopN(), AbstractQuery.Sort.ASC, additionalConditions, condition.getAttributes());
        }
        if (resp.size() == 0) {
            return Collections.emptyList();
        } else if (resp.size() > 1) { // since we have done aggregation, i.e. MEAN
            throw new IOException("invalid TopN response");
        }

        final List<SelectedRecord> topNList = new ArrayList<>();
        for (TopNQueryResponse.Item item : resp.getTopNLists().get(0).getItems()) {
            SelectedRecord record = new SelectedRecord();
            record.setId((String) item.getTagValuesMap().get(Metrics.ENTITY_ID).getValue());
            record.setValue(extractFieldValueAsString(valueColumnSpec, item.getValue()));
            topNList.add(record);
        }

        return topNList;
    }

    List<SelectedRecord> directMetricsTopN(boolean coldStage, TopNCondition condition, MetadataRegistry.Schema schema, String valueColumnName, MetadataRegistry.ColumnSpec valueColumnSpec,
                                           TimestampRange timestampRange, List<KeyValue> additionalConditions) throws IOException {
        MeasureQueryResponse resp = queryDebuggable(coldStage, schema, TAGS, Collections.singleton(valueColumnName),
                timestampRange, new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(MeasureQuery query) {
                        query.meanBy(valueColumnName, ImmutableSet.of(Metrics.ENTITY_ID));
                        if (condition.getOrder() == Order.DES) {
                            query.topN(condition.getTopN(), valueColumnName);
                        } else {
                            query.bottomN(condition.getTopN(), valueColumnName);
                        }
                        if (CollectionUtils.isNotEmpty(additionalConditions)) {
                            additionalConditions.forEach(additionalCondition -> query
                                    .and(eq(
                                            additionalCondition.getKey(),
                                            additionalCondition.getValue()
                                    )));
                        }
                        if (CollectionUtils.isNotEmpty(condition.getAttributes())) {
                            condition.getAttributes().forEach(attr -> {
                                if (attr.isEquals()) {
                                    query.and(eq(attr.getKey(), attr.getValue()));
                                } else {
                                    query.and(ne(attr.getKey(), attr.getValue()));
                                }
                            });
                        }
                    }
                });

        if (resp.size() == 0) {
            return Collections.emptyList();
        }

        final List<SelectedRecord> topNList = new ArrayList<>();
        for (final DataPoint dataPoint : resp.getDataPoints()) {
            final SelectedRecord record = new SelectedRecord();
            record.setId(dataPoint.getTagValue(Metrics.ENTITY_ID));
            record.setValue(extractFieldValueAsString(valueColumnSpec, dataPoint.getFieldValue(valueColumnName)));
            topNList.add(record);
        }

        return topNList;
    }

    private static String extractFieldValueAsString(MetadataRegistry.ColumnSpec spec, Object fieldValue) {
        if (double.class.equals(spec.getColumnClass())) {
            return String.valueOf(ByteUtil.bytes2Double((byte[]) fieldValue).longValue());
        } else if (String.class.equals(spec.getColumnClass())) {
            return (String) fieldValue;
        } else {
            return String.valueOf(((Number) fieldValue).longValue());
        }
    }
}
