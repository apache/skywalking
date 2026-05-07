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

package org.apache.skywalking.oap.server.core.zipkin.source;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.source.Source;

@ScopeDeclaration(id = DefaultScopeDefine.ZIPKIN_SPAN, name = "ZipkinSpan")
public class ZipkinSpan extends Source {

    @Override
    public int scope() {
        return DefaultScopeDefine.ZIPKIN_SPAN;
    }

    @Override
    public String getEntityId() {
        return spanId + Const.LINE + kind;
    }

    @Setter
    @Getter
    private String traceId;
    @Setter
    @Getter
    private String parentId;
    @Setter
    @Getter
    private String name;
    @Setter
    @Getter
    private long duration; //microseconds
    @Setter
    @Getter
    private String spanId;
    @Setter
    @Getter
    private String kind;
    @Setter
    @Getter
    private long timestampMillis;
    @Setter
    @Getter
    private long timestamp;
    @Setter
    @Getter
    private String localEndpointServiceName;
    @Setter
    @Getter
    private String localEndpointIPV4;
    @Setter
    @Getter
    private String localEndpointIPV6;
    @Setter
    @Getter
    private int localEndpointPort;
    @Setter
    @Getter
    private String remoteEndpointServiceName;
    @Setter
    @Getter
    private String remoteEndpointIPV4;
    @Setter
    @Getter
    private String remoteEndpointIPV6;
    @Setter
    @Getter
    private int remoteEndpointPort;
    @Setter
    @Getter
    private JsonObject annotations;
    @Setter
    @Getter
    private JsonObject tags;
    @Setter
    @Getter
    private Boolean debug;
    @Setter
    @Getter
    private Boolean shared;
    @Setter
    @Getter
    private List<String> query = new ArrayList<>();

    @Override
    public String toJson() {
        final JsonObject obj = new JsonObject();
        obj.addProperty("scope", scope());
        obj.addProperty("entityId", getEntityId());
        obj.addProperty("timeBucket", getTimeBucket());
        obj.addProperty("traceId", traceId);
        obj.addProperty("parentId", parentId);
        obj.addProperty("name", name);
        obj.addProperty("duration", duration);
        obj.addProperty("spanId", spanId);
        obj.addProperty("kind", kind);
        obj.addProperty("timestampMillis", timestampMillis);
        obj.addProperty("timestamp", timestamp);
        obj.addProperty("localEndpointServiceName", localEndpointServiceName);
        obj.addProperty("localEndpointIPV4", localEndpointIPV4);
        obj.addProperty("localEndpointIPV6", localEndpointIPV6);
        obj.addProperty("localEndpointPort", localEndpointPort);
        obj.addProperty("remoteEndpointServiceName", remoteEndpointServiceName);
        obj.addProperty("remoteEndpointIPV4", remoteEndpointIPV4);
        obj.addProperty("remoteEndpointIPV6", remoteEndpointIPV6);
        obj.addProperty("remoteEndpointPort", remoteEndpointPort);
        obj.addProperty("debug", debug);
        obj.addProperty("shared", shared);
        return obj.toString();
    }
}
