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
import org.apache.skywalking.library.banyandb.v1.client.RowEntity;
import org.apache.skywalking.library.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * {@link org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord} is a stream
 */
public class BanyanDBLogQueryDAO extends AbstractBanyanDBDAO implements ILogQueryDAO {
    private static final Set<String> TAGS = ImmutableSet.of(AbstractLogRecord.SERVICE_ID,
            AbstractLogRecord.SERVICE_INSTANCE_ID,
            AbstractLogRecord.ENDPOINT_ID,
            AbstractLogRecord.TRACE_ID,
            AbstractLogRecord.TRACE_SEGMENT_ID,
            AbstractLogRecord.SPAN_ID,
            AbstractLogRecord.TIMESTAMP,
            AbstractLogRecord.CONTENT_TYPE,
            AbstractLogRecord.CONTENT,
            AbstractLogRecord.TAGS,
            AbstractLogRecord.TAGS_RAW_DATA);

    public BanyanDBLogQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public Logs queryLogs(String serviceId, String serviceInstanceId, String endpointId,
                          TraceScopeCondition relatedTrace, Order queryOrder, int from, int limit,
                          Duration duration, List<Tag> tags, List<String> keywordsOfContent,
                          List<String> excludingKeywordsOfContent) throws IOException {
        final boolean isColdStage = duration != null && duration.isColdStage();
        final Conditions where = Conditions.create();
        if (StringUtil.isNotEmpty(serviceId)) {
            where.eq(AbstractLogRecord.SERVICE_ID, serviceId);
        }
        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            if (StringUtil.isEmpty(serviceId)) {
                IDManager.ServiceInstanceID.InstanceIDDefinition instanceIDDefinition = IDManager.ServiceInstanceID.analysisId(
                    serviceInstanceId);
                where.eq(AbstractLogRecord.SERVICE_ID, instanceIDDefinition.getServiceId());
            }
            where.eq(AbstractLogRecord.SERVICE_INSTANCE_ID, serviceInstanceId);
        }
        if (StringUtil.isNotEmpty(endpointId)) {
            if (StringUtil.isEmpty(serviceId)) {
                IDManager.EndpointID.EndpointIDDefinition endpointIDDefinition = IDManager.EndpointID.analysisId(
                    endpointId);
                where.eq(AbstractLogRecord.SERVICE_ID, endpointIDDefinition.getServiceId());
            }
            where.eq(AbstractLogRecord.ENDPOINT_ID, endpointId);
        }
        if (Objects.nonNull(relatedTrace)) {
            if (StringUtil.isNotEmpty(relatedTrace.getTraceId())) {
                where.eq(AbstractLogRecord.TRACE_ID, relatedTrace.getTraceId());
            }
            if (StringUtil.isNotEmpty(relatedTrace.getSegmentId())) {
                where.eq(AbstractLogRecord.TRACE_SEGMENT_ID, relatedTrace.getSegmentId());
            }
            if (Objects.nonNull(relatedTrace.getSpanId())) {
                where.eq(AbstractLogRecord.SPAN_ID, (long) relatedTrace.getSpanId());
            }
        }
        if (CollectionUtils.isNotEmpty(tags)) {
            List<String> tagsConditions = new ArrayList<>(tags.size());
            for (final Tag tag : tags) {
                tagsConditions.add(tag.toString());
            }
            where.having(LogRecord.TAGS, tagsConditions);
        }
        if (queryOrder == Order.ASC) {
            where.orderByAsc();
        } else {
            where.orderByDesc();
        }
        where.limit(limit).offset(from);

        StreamQueryResponse resp = queryDebuggable(isColdStage, LogRecord.INDEX_NAME, TAGS,
                getTimestampRange(duration), where);

        Logs logs = new Logs();

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
}
