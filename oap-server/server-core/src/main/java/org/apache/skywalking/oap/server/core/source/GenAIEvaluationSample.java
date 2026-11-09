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

package org.apache.skywalking.oap.server.core.source;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.query.enumeration.GenAITraceRefType;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.GEN_AI_EVALUATION_SAMPLE;

/**
 * A GenAI span handed to the {@code ai-evaluation} module, which samples it for its LLM judge; that module's
 * dispatcher takes it, and nothing is stored here. The TAL rules send it with {@code source {}}: a native one names
 * the span by segment and span index, an OTLP one by span id.
 */
@ScopeDeclaration(id = GEN_AI_EVALUATION_SAMPLE, name = "GenAIEvaluationSample")
@Getter
@Setter
public class GenAIEvaluationSample extends Source {
    private static final Gson GSON = new Gson();

    private String traceId;
    private GenAITraceRefType traceRefType;
    private String segmentId;
    /**
     * The native span's id within its segment; null for an OTLP span, which {@link #spanId} names.
     */
    private Integer spanIndex;
    private String spanId;
    private String serviceName;
    private String serviceInstanceName;
    private String operationName;
    private String providerName;
    private String modelName;
    private long startTimeMillis;
    private long endTimeMillis;
    private boolean error;
    private Map<String, String> tags;

    @Override
    public int scope() {
        return GEN_AI_EVALUATION_SAMPLE;
    }

    @Override
    public String getEntityId() {
        return Const.EMPTY_STRING;
    }

    @Override
    public String toJson() {
        final JsonObject obj = new JsonObject();
        obj.addProperty("scope", scope());
        obj.addProperty("timeBucket", getTimeBucket());
        obj.addProperty("traceId", traceId);
        obj.addProperty("traceRefType", traceRefType == null ? null : traceRefType.name());
        obj.addProperty("segmentId", segmentId);
        obj.addProperty("spanIndex", spanIndex);
        obj.addProperty("spanId", spanId);
        obj.addProperty("serviceName", serviceName);
        obj.addProperty("serviceInstanceName", serviceInstanceName);
        obj.addProperty("operationName", operationName);
        obj.addProperty("providerName", providerName);
        obj.addProperty("modelName", modelName);
        obj.addProperty("startTimeMillis", startTimeMillis);
        obj.addProperty("endTimeMillis", endTimeMillis);
        obj.addProperty("error", error);
        obj.add("tags", tags == null ? null : GSON.toJsonTree(tags));
        return obj.toString();
    }
}
