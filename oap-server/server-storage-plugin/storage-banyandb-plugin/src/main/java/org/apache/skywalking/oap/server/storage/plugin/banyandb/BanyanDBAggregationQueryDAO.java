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
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.banyandb.v1.client.DataPoint;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.banyandb.v1.client.TopNQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.input.AttrCondition;
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

@Slf4j
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
        // The query tags are the additional conditions and attributes defined in the TopN condition.
        // The query tags is the key to find the TopN aggregation in the schema.
        // If the TopN aggregation is defined in the schema, it will be used to perform the query.
        // The server-side TopN only support the rules config in bydb-topn.yaml.
        ImmutableSet.Builder<String> queryTags = ImmutableSet.builder();
        if (condition.getAttributes() != null) {
            for (AttrCondition attr : condition.getAttributes()) {
                if (!attr.isEquals()) {
                    // Not equal attr condition should add full String key!=value
                    queryTags.add(attr.toString());
                } else {
                    queryTags.add(attr.getKey());
                }
            }
        }
        if (additionalConditions != null) {
            additionalConditions.forEach(additionalCondition -> queryTags.add(additionalCondition.getKey()));
        }
        if (schema.getTopNSpecs() != null) {
            BanyandbDatabase.TopNAggregation topNAggregation = schema.getTopNSpecs().get(queryTags.build());
            if (topNAggregation != null) {
                BanyandbModel.Sort sort = topNAggregation.getFieldValueSort();
                // If the TopN aggregation is defined in the schema, use it.
                switch (condition.getOrder()) {
                    case DES:
                        if (sort == BanyandbModel.Sort.SORT_DESC || sort == BanyandbModel.Sort.SORT_UNSPECIFIED) {
                            return serverSideTopN(
                                isColdStage, condition, schema, spec, getTimestampRange(duration), additionalConditions,
                                topNAggregation.getMetadata().getName(), AbstractQuery.Sort.DESC
                            );
                        }
                        break;
                    case ASC:
                        if (sort == BanyandbModel.Sort.SORT_ASC || sort == BanyandbModel.Sort.SORT_UNSPECIFIED) {
                            return serverSideTopN(
                                isColdStage, condition, schema, spec, getTimestampRange(duration), additionalConditions,
                                topNAggregation.getMetadata().getName(), AbstractQuery.Sort.ASC
                            );
                        }
                        break;
                    default:
                        throw new IOException("Unsupported order: " + condition.getOrder());
                }
            }
        }

        return directMetricsTopN(isColdStage, condition, schema, valueColumnName, spec, getTimestampRange(duration), additionalConditions);
    }

    List<SelectedRecord> serverSideTopN(boolean isColdStage, TopNCondition condition, MetadataRegistry.Schema schema, MetadataRegistry.ColumnSpec valueColumnSpec,
                                        TimestampRange timestampRange, List<KeyValue> additionalConditions, String topNRuleName, AbstractQuery.Sort sort) throws IOException {
        TopNQueryResponse resp;
        resp = topNQueryDebuggable(isColdStage, schema, timestampRange, condition.getTopN(), sort, additionalConditions, condition.getAttributes(), topNRuleName);
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

    List<SelectedRecord> directMetricsTopN(boolean isColdStage, TopNCondition condition, MetadataRegistry.Schema schema, String valueColumnName, MetadataRegistry.ColumnSpec valueColumnSpec,
                                           TimestampRange timestampRange, List<KeyValue> additionalConditions) throws IOException {
        if (log.isDebugEnabled()
            && condition.getScope().equals(Scope.Endpoint)) {
            log.debug("Endpoint direct TopN query, TopNCondition: {}, AdditionalConditions: {}, TimestampRange: {}",
            condition, additionalConditions, timestampRange);
        }
        MeasureQueryResponse resp = queryDebuggable(isColdStage, schema, TAGS, Collections.singleton(valueColumnName),
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
