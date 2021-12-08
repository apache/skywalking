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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.schema.LogRecordBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * {@link org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord} is a stream
 */
public class BanyanDBLogQueryDAO extends AbstractBanyanDBDAO implements ILogQueryDAO {
    public BanyanDBLogQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public Logs queryLogs(String serviceId, String serviceInstanceId, String endpointId,
                          TraceScopeCondition relatedTrace, Order queryOrder, int from, int limit,
                          long startTB, long endTB, List<Tag> tags, List<String> keywordsOfContent,
                          List<String> excludingKeywordsOfContent) throws IOException {
        final QueryBuilder query = new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                query.setDataProjections(ImmutableList.of(AbstractLogRecord.CONTENT_TYPE, AbstractLogRecord.CONTENT, AbstractLogRecord.TAGS_RAW_DATA));

                if (StringUtil.isNotEmpty(serviceId)) {
                    query.appendCondition(eq(AbstractLogRecord.SERVICE_ID, serviceId));
                }

                if (StringUtil.isNotEmpty(serviceInstanceId)) {
                    query.appendCondition(eq(AbstractLogRecord.SERVICE_INSTANCE_ID, serviceInstanceId));
                }
                if (StringUtil.isNotEmpty(endpointId)) {
                    query.appendCondition(eq(AbstractLogRecord.ENDPOINT_ID, endpointId));
                }
                if (Objects.nonNull(relatedTrace)) {
                    if (StringUtil.isNotEmpty(relatedTrace.getTraceId())) {
                        query.appendCondition(eq(AbstractLogRecord.TRACE_ID, relatedTrace.getTraceId()));
                    }
                    if (StringUtil.isNotEmpty(relatedTrace.getSegmentId())) {
                        query.appendCondition(eq(AbstractLogRecord.TRACE_SEGMENT_ID, relatedTrace.getSegmentId()));
                    }
                    if (Objects.nonNull(relatedTrace.getSpanId())) {
                        query.appendCondition(eq(AbstractLogRecord.SPAN_ID, (long) relatedTrace.getSpanId()));
                    }
                }

                if (CollectionUtils.isNotEmpty(tags)) {
                    for (final Tag tag : tags) {
                        if (LogRecordBuilder.INDEXED_TAGS.contains(tag.getKey())) {
                            query.appendCondition(eq(tag.getKey(), tag.getValue()));
                        }
                    }
                }
            }
        };

        TimestampRange tsRange = null;
        if (startTB > 0 && endTB > 0) {
            tsRange = new TimestampRange(TimeBucket.getTimestamp(startTB), TimeBucket.getTimestamp(endTB));
        }

        StreamQueryResponse resp = query(LogRecord.INDEX_NAME,
                ImmutableList.of(AbstractLogRecord.SERVICE_ID, AbstractLogRecord.SERVICE_INSTANCE_ID,
                        AbstractLogRecord.ENDPOINT_ID, AbstractLogRecord.TRACE_ID, AbstractLogRecord.TRACE_SEGMENT_ID,
                        AbstractLogRecord.SPAN_ID), tsRange, query);

        List<Log> logEntities = resp.getElements().stream().map(new LogDeserializer()).collect(Collectors.toList());

        Logs logs = new Logs();
        logs.getLogs().addAll(logEntities);
        logs.setTotal(logEntities.size());

        return logs;
    }

    public static class LogDeserializer implements RowEntityDeserializer<Log> {
        @Override
        public Log apply(RowEntity row) {
            Log log = new Log();
            final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
            log.setServiceId((String) searchable.get(0).getValue());
            log.setServiceInstanceId((String) searchable.get(1).getValue());
            log.setEndpointId((String) searchable.get(2).getValue());
            log.setTraceId((String) searchable.get(3).getValue());
            log.setTimestamp(row.getTimestamp());
            final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
            if (data.get(2).getValue() == null || ((ByteString) data.get(2).getValue()).isEmpty()) {
                log.setContent("");
            } else {
                try {
                    // Don't read the tags as they have been in the data binary already.
                    LogTags logTags = LogTags.parseFrom((ByteString) data.get(2).getValue());
                    for (final KeyStringValuePair pair : logTags.getDataList()) {
                        log.getTags().add(new KeyValue(pair.getKey(), pair.getValue()));
                    }
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException(e);
                }
            }
            return log;
        }
    }
}
