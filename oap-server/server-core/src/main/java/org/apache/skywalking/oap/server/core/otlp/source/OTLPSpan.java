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

package org.apache.skywalking.oap.server.core.otlp.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.source.Source;

/**
 * One OTLP span on its way to {@code otlp_span}. The fields are the index columns; {@link #dataBinary} is the
 * single-span {@code ResourceSpans} message that is stored verbatim.
 */
@ScopeDeclaration(id = DefaultScopeDefine.OTLP_SPAN, name = "OTLPSpan")
public class OTLPSpan extends Source {
    @Override
    public int scope() {
        return DefaultScopeDefine.OTLP_SPAN;
    }

    @Override
    public String getEntityId() {
        return spanId;
    }

    @Setter
    @Getter
    private String traceId;
    @Setter
    @Getter
    private String spanId;
    @Setter
    @Getter
    private String parentSpanId;
    @Setter
    @Getter
    private String serviceName;
    @Setter
    @Getter
    private String serviceInstance;
    @Setter
    @Getter
    private String scopeName;
    @Setter
    @Getter
    private String name;
    @Setter
    @Getter
    private int kind;
    @Setter
    @Getter
    private int statusCode;
    @Setter
    @Getter
    private String peerService;
    @Setter
    @Getter
    private long startTime;
    @Setter
    @Getter
    private long duration;
    @Setter
    @Getter
    private List<String> tags = new ArrayList<>();
    @Setter
    @Getter
    private byte[] dataBinary;

    @Override
    public String toJson() {
        final JsonObject obj = new JsonObject();
        obj.addProperty("scope", scope());
        obj.addProperty("entityId", getEntityId());
        obj.addProperty("timeBucket", getTimeBucket());
        obj.addProperty("traceId", traceId);
        obj.addProperty("spanId", spanId);
        obj.addProperty("parentSpanId", parentSpanId);
        obj.addProperty("serviceName", serviceName);
        obj.addProperty("serviceInstance", serviceInstance);
        obj.addProperty("scopeName", scopeName);
        obj.addProperty("name", name);
        obj.addProperty("kind", kind);
        obj.addProperty("statusCode", statusCode);
        obj.addProperty("peerService", peerService);
        obj.addProperty("startTime", startTime);
        obj.addProperty("duration", duration);
        final JsonArray tagArray = new JsonArray();
        if (tags != null) {
            tags.forEach(tagArray::add);
        }
        obj.add("tags", tagArray);
        obj.addProperty("dataBinaryLength", dataBinary == null ? 0 : dataBinary.length);
        return obj.toString();
    }
}
