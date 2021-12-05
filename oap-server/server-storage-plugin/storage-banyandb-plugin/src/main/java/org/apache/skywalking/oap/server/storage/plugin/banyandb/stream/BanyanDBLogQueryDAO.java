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

import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
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
                if (StringUtil.isNotEmpty(serviceId)) {
                    query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", AbstractLogRecord.SERVICE_ID, serviceId));
                }

                if (StringUtil.isNotEmpty(serviceInstanceId)) {
                    query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", AbstractLogRecord.SERVICE_INSTANCE_ID, serviceInstanceId));
                }
                if (StringUtil.isNotEmpty(endpointId)) {
                    query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", AbstractLogRecord.ENDPOINT_ID, endpointId));
                }
                if (Objects.nonNull(relatedTrace)) {
                    if (StringUtil.isNotEmpty(relatedTrace.getTraceId())) {
                        query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", AbstractLogRecord.TRACE_ID, relatedTrace.getTraceId()));
                    }
                    if (StringUtil.isNotEmpty(relatedTrace.getSegmentId())) {
                        query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", AbstractLogRecord.TRACE_SEGMENT_ID, relatedTrace.getSegmentId()));
                    }
                    if (Objects.nonNull(relatedTrace.getSpanId())) {
                        query.appendCondition(PairQueryCondition.LongQueryCondition.eq("searchable", AbstractLogRecord.SPAN_ID, (long) relatedTrace.getSpanId()));
                    }
                }

                if (CollectionUtils.isNotEmpty(tags)) {
                    for (final Tag tag : tags) {
                        if (LogRecordBuilder.INDEXED_TAGS.contains(tag.getKey())) {
                            query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", tag.getKey(), tag.getValue()));
                        }
                    }
                }
            }
        };

        final List<Log> entities;
        if (startTB != 0 && endTB != 0) {
            entities = query(Log.class, query, TimeBucket.getTimestamp(startTB), TimeBucket.getTimestamp(endTB));
        } else {
            entities = query(Log.class, query);
        }
        Logs logs = new Logs();
        logs.getLogs().addAll(entities);
        logs.setTotal(entities.size());

        return logs;
    }
}
