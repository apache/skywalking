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
import org.apache.skywalking.banyandb.v1.client.DataPoint;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.RecordCondition;
import org.apache.skywalking.oap.server.core.query.type.Record;
import org.apache.skywalking.oap.server.core.storage.query.IRecordsQueryDAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.util.ByteUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class BanyanDBRecordsQueryDAO extends AbstractBanyanDBDAO implements IRecordsQueryDAO {
    private static final Set<String> TAGS = ImmutableSet.of(TopN.ENTITY_ID, TopN.STATEMENT, TopN.TRACE_ID);

    public BanyanDBRecordsQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<Record> readRecords(RecordCondition condition, String valueColumnName, Duration duration) throws IOException {
        final String modelName = condition.getName();
        final TimestampRange timestampRange = new TimestampRange(duration.getStartTimestamp(), duration.getEndTimestamp());
        MeasureQueryResponse resp = query(modelName, TAGS,
                Collections.singleton(valueColumnName), timestampRange, new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(MeasureQuery query) {
                        if (condition.getParentEntity() != null && condition.getParentEntity().buildId() != null) {
                            query.and(eq(TopN.ENTITY_ID, condition.getParentEntity().buildId()));
                        }
                        if (condition.getOrder() == Order.DES) {
                            query.topN(condition.getTopN(), valueColumnName);
                        } else {
                            query.bottomN(condition.getTopN(), valueColumnName);
                        }
                    }
                });

        if (resp.size() == 0) {
            return Collections.emptyList();
        }

        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findRecordMetadata(modelName);
        if (schema == null) {
            throw new IOException("schema is not registered");
        }

        MetadataRegistry.ColumnSpec spec = schema.getSpec(valueColumnName);
        if (spec == null) {
            throw new IOException("field spec is not registered");
        }

        List<Record> results = new ArrayList<>(condition.getTopN());

        for (final DataPoint dataPoint : resp.getDataPoints()) {
            Record record = new Record();
            final String refId = dataPoint.getTagValue(TopN.TRACE_ID);
            record.setName(dataPoint.getTagValue(TopN.STATEMENT));
            record.setRefId(StringUtil.isEmpty(refId) ? "" : refId);
            record.setId(record.getRefId());
            record.setValue(extractFieldValueAsString(spec, valueColumnName, dataPoint));
            results.add(record);
        }

        return results;
    }

    private String extractFieldValueAsString(MetadataRegistry.ColumnSpec spec, String fieldName, DataPoint dataPoint) throws IOException {
        if (double.class.equals(spec.getColumnClass())) {
            return String.valueOf(ByteUtil.bytes2Double(dataPoint.getFieldValue(fieldName)).longValue());
        } else if (String.class.equals(spec.getColumnClass())) {
            return dataPoint.getFieldValue(fieldName);
        } else {
            return String.valueOf(((Number) dataPoint.getFieldValue(fieldName)).longValue());
        }
    }
}
