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

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.library.module.Service;

import static org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext.TRACE_CONTEXT;

public interface ILogQueryDAO extends Service {

    default boolean supportQueryLogsByKeywords() {
        return false;
    }

    default Logs queryLogsDebuggable(String serviceId,
                                     String serviceInstanceId,
                                     String endpointId,
                                     TraceScopeCondition relatedTrace,
                                     Order queryOrder,
                                     int from,
                                     int limit,
                                     final Duration duration,
                                     final List<Tag> tags,
                                     final List<String> keywordsOfContent,
                                     final List<String> excludingKeywordsOfContent) throws IOException {
        DebuggingTraceContext traceContext = TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: queryLogs");
                StringBuilder msg = new StringBuilder();
                msg.append("ServiceId: ").append(serviceId)
                   .append(", ServiceInstanceId: ").append(serviceInstanceId)
                   .append(", EndpointId: ").append(endpointId)
                   .append(", RelatedTrace: ").append(relatedTrace)
                   .append(", QueryOrder: ").append(queryOrder)
                   .append(", From: ").append(from)
                   .append(", Limit: ").append(limit)
                   .append(", Duration: ").append(duration)
                   .append(", Tags: ").append(tags)
                   .append(", KeywordsOfContent: ").append(keywordsOfContent)
                   .append(", ExcludingKeywordsOfContent: ").append(excludingKeywordsOfContent);
                span.setMsg(msg.toString());
            }
            return queryLogs(
                serviceId, serviceInstanceId, endpointId, relatedTrace, queryOrder, from, limit, duration, tags,
                keywordsOfContent, excludingKeywordsOfContent
            );
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    Logs queryLogs(String serviceId,
                   String serviceInstanceId,
                   String endpointId,
                   TraceScopeCondition relatedTrace,
                   Order queryOrder,
                   int from,
                   int limit,
                   final Duration duration,
                   final List<Tag> tags,
                   final List<String> keywordsOfContent,
                   final List<String> excludingKeywordsOfContent) throws IOException;

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
