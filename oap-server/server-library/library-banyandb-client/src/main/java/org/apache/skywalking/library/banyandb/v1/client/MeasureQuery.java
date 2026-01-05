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

package org.apache.skywalking.library.banyandb.v1.client;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.skywalking.banyandb.measure.v1.BanyandbMeasure;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;

/**
 * MeasureQuery is the high-level query API for the measure model.
 */
@Setter
public class MeasureQuery extends AbstractQuery<BanyandbMeasure.QueryRequest> {
    /**
     * field_projection can be used to select fields of the data points in the response
     */
    private final Set<String> fieldProjections;

    private Aggregation aggregation;

    private TopN topN;

    private Integer limit;

    private int offset;

    private OrderBy orderBy;

    private Set<String> stages;

    public MeasureQuery(final List<String> groups,
                        final String name,
                        final Map<String/*tagName*/, String/*tagFamilyName*/> tagProjections,
                        final Set<String/*tagName*/> fieldProjections) {
        this(groups, name, null, tagProjections, fieldProjections);
    }

    public MeasureQuery(final List<String> groups,
                        final String name,
                        final TimestampRange timestampRange,
                        final Map<String/*tagName*/, String/*tagFamilyName*/> tagProjections,
                        final Set<String/*tagName*/> fieldProjections) {
        super(groups, name, timestampRange, tagProjections);
        this.fieldProjections = fieldProjections;
    }

    @Override
    public MeasureQuery and(PairQueryCondition<?> condition) {
        return (MeasureQuery) super.and(condition);
    }

    @Override
    public MeasureQuery or(PairQueryCondition<?> condition) {
        return (MeasureQuery) super.or(condition);
    }

    public MeasureQuery groupBy(Set<String> groupByKeys) {
        Preconditions.checkArgument(tagProjections.keySet().containsAll(groupByKeys), "groupBy tags should be selected first");
        this.aggregation = new Aggregation(null, Aggregation.Type.UNSPECIFIED, groupByKeys);
        return this;
    }

    public MeasureQuery maxBy(String field, Set<String> groupByKeys) {
        Preconditions.checkArgument(fieldProjections.contains(field), "field should be selected first");
        Preconditions.checkArgument(tagProjections.keySet().containsAll(groupByKeys), "groupBy tags should be selected first");
        this.aggregation = new Aggregation(field, Aggregation.Type.MAX, groupByKeys);
        return this;
    }

    public MeasureQuery minBy(String field, Set<String> groupByKeys) {
        Preconditions.checkArgument(fieldProjections.contains(field), "field should be selected first");
        Preconditions.checkArgument(tagProjections.keySet().containsAll(groupByKeys), "groupBy tags should be selected first");
        Preconditions.checkState(this.aggregation == null, "aggregation should only be set once");
        this.aggregation = new Aggregation(field, Aggregation.Type.MIN, groupByKeys);
        return this;
    }

    public MeasureQuery meanBy(String field, Set<String> groupByKeys) {
        Preconditions.checkArgument(fieldProjections.contains(field), "field should be selected first");
        Preconditions.checkArgument(tagProjections.keySet().containsAll(groupByKeys), "groupBy tags should be selected first");
        Preconditions.checkState(this.aggregation == null, "aggregation should only be set once");
        this.aggregation = new Aggregation(field, Aggregation.Type.MEAN, groupByKeys);
        return this;
    }

    public MeasureQuery countBy(String field, Set<String> groupByKeys) {
        Preconditions.checkArgument(fieldProjections.contains(field), "field should be selected first");
        Preconditions.checkArgument(tagProjections.keySet().containsAll(groupByKeys), "groupBy tags should be selected first");
        Preconditions.checkState(this.aggregation == null, "aggregation should only be set once");
        this.aggregation = new Aggregation(field, Aggregation.Type.COUNT, groupByKeys);
        return this;
    }

    public MeasureQuery sumBy(String field, Set<String> groupByKeys) {
        Preconditions.checkArgument(fieldProjections.contains(field), "field should be selected first");
        Preconditions.checkArgument(tagProjections.keySet().containsAll(groupByKeys), "groupBy tags should be selected first");
        Preconditions.checkState(this.aggregation == null, "aggregation should only be set once");
        this.aggregation = new Aggregation(field, Aggregation.Type.SUM, groupByKeys);
        return this;
    }

    public MeasureQuery topN(int number, String field) {
        Preconditions.checkArgument(fieldProjections.contains(field), "field should be selected first");
        this.topN = new TopN(field, number, Sort.DESC);
        return this;
    }

    public MeasureQuery bottomN(int number, String field) {
        Preconditions.checkArgument(fieldProjections.contains(field), "field should be selected first");
        this.topN = new TopN(field, number, Sort.ASC);
        return this;
    }

    public MeasureQuery limit(int limit) {
        this.limit = limit;
        return this;
    }

    public MeasureQuery offset(int offset) {
        this.offset = offset;
        return this;
    }

    public MeasureQuery stages(Set<String> stages) {
        this.stages = stages;
        return this;
    }

    /**
     * @return QueryRequest for gRPC level query.
     */
    @Override
    BanyandbMeasure.QueryRequest build() throws BanyanDBException {
        final BanyandbMeasure.QueryRequest.Builder builder = BanyandbMeasure.QueryRequest.newBuilder();
        builder.setName(this.name);
        builder.addAllGroups(this.groups);
        if (timestampRange != null) {
            builder.setTimeRange(timestampRange.build());
        } else {
            builder.setTimeRange(TimestampRange.MAX_RANGE);
        }
        BanyandbModel.TagProjection tagProjections = buildTagProjections();
        if (tagProjections.getTagFamiliesCount() > 0) {
            builder.setTagProjection(buildTagProjections());
        }
        if (!fieldProjections.isEmpty()) {
            builder.setFieldProjection(BanyandbMeasure.QueryRequest.FieldProjection.newBuilder()
                    .addAllNames(fieldProjections)
                    .build());
        }
        if (stages != null && !stages.isEmpty()) {
            builder.addAllStages(stages);
        }
        if (this.aggregation != null) {
            BanyandbMeasure.QueryRequest.GroupBy.Builder groupByBuilder = BanyandbMeasure.QueryRequest.GroupBy.newBuilder()
                    .setTagProjection(buildTagProjections(this.aggregation.groupByTagsProjection));
            if (Strings.isNullOrEmpty(this.aggregation.fieldName)) {
                if (this.aggregation.aggregationType != Aggregation.Type.UNSPECIFIED) {
                    throw new IllegalArgumentException("field name cannot be null or empty if aggregation is specified");
                }
            } else {
                builder.setGroupBy(groupByBuilder.build());
                groupByBuilder.setFieldName(this.aggregation.fieldName);
                BanyandbMeasure.QueryRequest.Aggregation aggr = BanyandbMeasure.QueryRequest.Aggregation.newBuilder()
                        .setFunction(this.aggregation.aggregationType.function)
                        .setFieldName(this.aggregation.fieldName)
                        .build();
                builder.setGroupBy(groupByBuilder.build()).setAgg(aggr);
            }
        }
        if (this.topN != null) {
            BanyandbMeasure.QueryRequest.Top top = BanyandbMeasure.QueryRequest.Top.newBuilder()
                    .setFieldName(this.topN.fieldName)
                    .setNumber(this.topN.number)
                    .setFieldValueSort(Sort.DESC.equals(this.topN.sort) ? BanyandbModel.Sort.SORT_DESC : BanyandbModel.Sort.SORT_ASC)
                    .build();
            builder.setTop(top);
        }
        builder.setOffset(this.offset);
        if (this.limit != null) {
            builder.setLimit(this.limit);
        }
        if (this.orderBy != null) {
            builder.setOrderBy(orderBy.build());
        }
        // add all criteria
        buildCriteria().ifPresent(builder::setCriteria);
        builder.setTrace(this.trace);
        return builder.build();
    }

    @RequiredArgsConstructor
    public static class TopN {
        private final String fieldName;
        private final int number;
        private final AbstractQuery.Sort sort;
    }

    @RequiredArgsConstructor
    public static class Aggregation {
        private final String fieldName;
        private final Type aggregationType;
        private final Set<String> groupByTagsProjection;

        @RequiredArgsConstructor
        public enum Type {
            UNSPECIFIED(BanyandbModel.AggregationFunction.AGGREGATION_FUNCTION_UNSPECIFIED),
            MEAN(BanyandbModel.AggregationFunction.AGGREGATION_FUNCTION_MEAN),
            MAX(BanyandbModel.AggregationFunction.AGGREGATION_FUNCTION_MAX),
            MIN(BanyandbModel.AggregationFunction.AGGREGATION_FUNCTION_MIN),
            COUNT(BanyandbModel.AggregationFunction.AGGREGATION_FUNCTION_COUNT),
            SUM(BanyandbModel.AggregationFunction.AGGREGATION_FUNCTION_SUM);
            final BanyandbModel.AggregationFunction function;
        }
    }
}
