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
 */

package org.apache.skywalking.oap.server.testing.dsl.lal;

import java.util.Map;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.JSONLog;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.apm.network.logging.v3.TextLog;
import org.apache.skywalking.apm.network.logging.v3.TraceContext;

/**
 * Builds {@link LogData} and proto extraLog from test input data maps.
 * Shared by LAL execution tests and v1-v2 comparison checker.
 */
public final class LalLogDataBuilder {

    private LalLogDataBuilder() {
    }

    /**
     * Builds a {@link LogData.Builder} from a test input map.
     *
     * <p>Supported keys: {@code service}, {@code instance}, {@code trace-id},
     * {@code timestamp}, {@code body-type} ({@code json}/{@code text}),
     * {@code body}, {@code tags} (Map).
     */
    @SuppressWarnings("unchecked")
    public static LogData.Builder buildLogData(final Map<String, Object> input) {
        final LogData.Builder builder = LogData.newBuilder();

        final String service = (String) input.get("service");
        if (service != null) {
            builder.setService(service);
        }

        final String instance = (String) input.get("instance");
        if (instance != null) {
            builder.setServiceInstance(instance);
        }

        final String traceId = (String) input.get("trace-id");
        if (traceId != null) {
            builder.setTraceContext(
                TraceContext.newBuilder().setTraceId(traceId));
        }

        final Object tsObj = input.get("timestamp");
        if (tsObj != null) {
            builder.setTimestamp(Long.parseLong(String.valueOf(tsObj)));
        }

        final String bodyType = (String) input.get("body-type");
        final String body = (String) input.get("body");

        if ("json".equals(bodyType) && body != null) {
            builder.setBody(LogDataBody.newBuilder()
                .setJson(JSONLog.newBuilder().setJson(body)));
        } else if ("text".equals(bodyType) && body != null) {
            builder.setBody(LogDataBody.newBuilder()
                .setText(TextLog.newBuilder().setText(body)));
        }

        final Map<String, String> tags =
            (Map<String, String>) input.get("tags");
        if (tags != null && !tags.isEmpty()) {
            final LogTags.Builder tagsBuilder = LogTags.newBuilder();
            for (final Map.Entry<String, String> tag : tags.entrySet()) {
                tagsBuilder.addData(KeyStringValuePair.newBuilder()
                    .setKey(tag.getKey())
                    .setValue(tag.getValue()));
            }
            builder.setTags(tagsBuilder);
        }

        return builder;
    }

    /**
     * Builds a synthetic {@link LogData} from a DSL string.
     * Used when no explicit input data is provided.
     */
    public static LogData buildSyntheticLogData(final String dsl) {
        final LogData.Builder builder = LogData.newBuilder()
            .setService("test-service")
            .setServiceInstance("test-instance")
            .setTimestamp(System.currentTimeMillis())
            .setTraceContext(TraceContext.newBuilder()
                .setTraceId("test-trace-id-123")
                .setTraceSegmentId("test-segment-id-456")
                .setSpanId(1));

        if (dsl.contains("json")) {
            builder.setBody(LogDataBody.newBuilder()
                .setJson(JSONLog.newBuilder()
                    .setJson("{\"level\":\"ERROR\",\"msg\":\"test\","
                        + "\"layer\":\"GENERAL\",\"service\":\"test-svc\","
                        + "\"instance\":\"test-inst\",\"endpoint\":\"test-ep\","
                        + "\"time\":\"1234567890\","
                        + "\"id\":\"slow-1\",\"statement\":\"SELECT 1\","
                        + "\"query_time\":500,\"code\":200,"
                        + "\"env\":\"prod\",\"region\":\"us-east\","
                        + "\"skip\":\"false\","
                        + "\"data\":{\"name\":\"test-value\"},"
                        + "\"latency\":100,\"uri\":\"/api/test\","
                        + "\"reason\":\"SLOW\",\"pid\":\"proc-1\","
                        + "\"dpid\":\"proc-2\",\"dp\":\"CLIENT\"}")));
        }

        if (dsl.contains("LOG_KIND")) {
            builder.setTags(LogTags.newBuilder()
                .addData(KeyStringValuePair.newBuilder()
                    .setKey("LOG_KIND").setValue("SLOW_SQL")));
        }

        return builder.build();
    }

    /**
     * Builds a proto {@link Message} for extraLog from a test input map.
     *
     * <p>The input map should contain an {@code extra-log} sub-map with
     * {@code proto-class} (FQCN) and {@code proto-json} (JSON string).
     *
     * @return the parsed Message, or {@code null} if no extraLog data
     */
    @SuppressWarnings("unchecked")
    public static Message buildExtraLog(
            final Map<String, Object> input) throws Exception {
        final Map<String, String> extraLog =
            (Map<String, String>) input.get("extra-log");
        if (extraLog == null) {
            return null;
        }

        final String protoClass = extraLog.get("proto-class");
        final String protoJson = extraLog.get("proto-json");
        if (protoClass == null || protoJson == null) {
            return null;
        }

        final Class<?> clazz = Class.forName(protoClass);
        final Message.Builder msgBuilder = (Message.Builder)
            clazz.getMethod("newBuilder").invoke(null);
        JsonFormat.parser()
            .ignoringUnknownFields()
            .merge(protoJson, msgBuilder);
        return msgBuilder.build();
    }
}
