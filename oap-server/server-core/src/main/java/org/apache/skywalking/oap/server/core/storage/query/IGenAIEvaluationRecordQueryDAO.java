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

package org.apache.skywalking.oap.server.core.storage.query;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.GenAIEvaluationRecords;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.library.module.Service;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import static org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext.TRACE_CONTEXT;

public interface IGenAIEvaluationRecordQueryDAO extends Service {

    default boolean supportQueryGenAIEvaluationRecordByKeywords() {
        return false;
    }

    default GenAIEvaluationRecords queryGenAIEvaluationRecordDebuggable(String serviceId,
                                                      String serviceInstanceId,
                                                      TraceScopeCondition relatedTrace,
                                                      Order queryOrder,
                                                      int from,
                                                      int limit,
                                                      final Duration duration,
                                                      final List<Tag> tags) throws IOException {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: queryGenAIEvaluationRecord");
                StringBuilder msg = new StringBuilder();
                msg.append("ServiceId: ").append(serviceId)
                   .append(", ServiceInstanceId: ").append(serviceInstanceId)
                   .append(", RelatedTrace: ").append(relatedTrace)
                   .append(", QueryOrder: ").append(queryOrder)
                   .append(", From: ").append(from)
                   .append(", Limit: ").append(limit)
                   .append(", Duration: ").append(duration)
                   .append(", Tags: ").append(tags);
                span.setMsg(msg.toString());
            }
            return queryGenAIEvaluationRecord(
                serviceId, serviceInstanceId, relatedTrace, queryOrder, from, limit, duration, tags
            );
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    GenAIEvaluationRecords queryGenAIEvaluationRecord(String serviceId,
                                                      String serviceInstanceId,
                                                      TraceScopeCondition relatedTrace,
                                                      Order queryOrder,
                                                      int from,
                                                      int limit,
                                                      final Duration duration,
                                                      final List<Tag> tags) throws IOException;

    /**
     * Parse the raw tags with base64 representation of data binary
     */
    default void parserDataBinary(String dataBinaryBase64, List<KeyValue> tags) {
        parserDataBinary(Base64.getDecoder().decode(dataBinaryBase64), tags);
    }

    default void parserDataBinary(byte[] dataBinary, List<KeyValue> tags) {
        try {
            LogTags logTags = LogTags.parseFrom(dataBinary);
            logTags.getDataList().forEach(pair -> tags.add(new KeyValue(pair.getKey(), pair.getValue())));
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }
}
