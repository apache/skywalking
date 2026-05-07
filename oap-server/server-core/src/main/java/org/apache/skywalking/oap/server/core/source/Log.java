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

package org.apache.skywalking.oap.server.core.source;

import com.google.gson.JsonObject;
import org.apache.skywalking.oap.server.core.query.type.ContentType;

@ScopeDeclaration(id = DefaultScopeDefine.LOG, name = "Log")
public class Log extends AbstractLog {

    @Override
    public int scope() {
        return DefaultScopeDefine.LOG;
    }

    @Override
    public String toJson() {
        final ContentType contentType = getContentType();
        final JsonObject obj = new JsonObject();
        obj.addProperty("scope", scope());
        obj.addProperty("entityId", getEntityId());
        obj.addProperty("timeBucket", getTimeBucket());
        obj.addProperty("uniqueId", getUniqueId());
        obj.addProperty("timestamp", getTimestamp());
        obj.addProperty("serviceId", getServiceId());
        obj.addProperty("serviceInstanceId", getServiceInstanceId());
        obj.addProperty("endpointId", getEndpointId());
        obj.addProperty("traceId", getTraceId());
        obj.addProperty("traceSegmentId", getTraceSegmentId());
        obj.addProperty("spanId", getSpanId());
        obj.addProperty("contentType", contentType == null ? null : contentType.name());
        obj.addProperty("content", getContent());
        obj.addProperty("error", isError());
        return obj.toString();
    }
}
