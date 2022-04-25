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

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

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
        final QueryBuilder<StreamQuery> query = new QueryBuilder<StreamQuery>() {
            @Override
            public void apply(StreamQuery query) {
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
                        // TODO: check log indexed tags
                        query.appendCondition(eq(tag.getKey(), tag.getValue()));
                    }
                }
            }
        };

        TimestampRange tsRange = null;
        if (startTB > 0 && endTB > 0) {
            tsRange = new TimestampRange(TimeBucket.getTimestamp(startTB), TimeBucket.getTimestamp(endTB));
        }

        StreamQueryResponse resp = query(LogRecord.INDEX_NAME,
                ImmutableSet.of(AbstractLogRecord.SERVICE_ID, AbstractLogRecord.SERVICE_INSTANCE_ID,
                        AbstractLogRecord.ENDPOINT_ID, AbstractLogRecord.TRACE_ID, AbstractLogRecord.TRACE_SEGMENT_ID,
                        AbstractLogRecord.SPAN_ID, AbstractLogRecord.CONTENT_TYPE, AbstractLogRecord.CONTENT,
                        AbstractLogRecord.TAGS_RAW_DATA), tsRange, query);

        Logs logs = new Logs();
        logs.setTotal(resp.size());

        for (final RowEntity rowEntity : resp.getElements()) {
            Log log = new Log();
            log.setServiceId(rowEntity.getTagValue(AbstractLogRecord.SERVICE_ID));
            log.setServiceInstanceId(
                    rowEntity.getTagValue(AbstractLogRecord.SERVICE_INSTANCE_ID));
            log.setEndpointId(
                    rowEntity.getTagValue(AbstractLogRecord.ENDPOINT_ID));
            if (log.getEndpointId() != null) {
                log.setEndpointName(
                        IDManager.EndpointID.analysisId(log.getEndpointId()).getEndpointName());
            }
            log.setTraceId(rowEntity.getTagValue(AbstractLogRecord.TRACE_ID));
            log.setTimestamp(((Number) rowEntity.getTagValue(AbstractLogRecord.TIMESTAMP)).longValue());
            log.setContentType(ContentType.instanceOf(
                    ((Number) rowEntity.getTagValue(AbstractLogRecord.CONTENT_TYPE)).intValue()));
            log.setContent(rowEntity.getTagValue(AbstractLogRecord.CONTENT));
            byte[] dataBinary = rowEntity.getTagValue(AbstractLogRecord.TAGS_RAW_DATA);
            if (dataBinary != null && dataBinary.length > 0) {
                parserDataBinary(dataBinary, log.getTags());
            }
            logs.getLogs().add(log);
        }
        return logs;
    }

    /**
     * Parser the raw tags.
     * TODO: merge default method
     */
    private void parserDataBinary(byte[] dataBinary, List<KeyValue> tags) {
        try {
            LogTags logTags = LogTags.parseFrom(dataBinary);
            logTags.getDataList().forEach(pair -> tags.add(new KeyValue(pair.getKey(), pair.getValue())));
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }
}
