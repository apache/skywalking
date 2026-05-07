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

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.CACHE_SLOW_ACCESS;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_CATALOG_NAME;

@ScopeDeclaration(id = CACHE_SLOW_ACCESS, name = "VirtualCacheSlowAccess", catalog = SERVICE_CATALOG_NAME)
public class CacheSlowAccess extends Source {
    @Getter
    @Setter
    private String id;
    @Getter
    @Setter
    private String cacheServiceId;
    @Getter
    @Setter
    private String command;
    @Getter
    @Setter
    private String key;
    @Getter
    @Setter
    private long latency;
    @Getter
    @Setter
    private String traceId;

    @Getter
    @Setter
    private boolean status;

    @Getter
    @Setter
    private VirtualCacheOperation operation;
    @Getter
    @Setter
    private long timestamp;

    @Override
    public int scope() {
        return DefaultScopeDefine.CACHE_SLOW_ACCESS;
    }

    @Override
    public String getEntityId() {
        return Const.EMPTY_STRING;
    }

    @Override
    public String toJson() {
        final JsonObject obj = new JsonObject();
        obj.addProperty("scope", scope());
        obj.addProperty("entityId", getEntityId());
        obj.addProperty("timeBucket", getTimeBucket());
        obj.addProperty("id", id);
        obj.addProperty("cacheServiceId", cacheServiceId);
        obj.addProperty("command", command);
        obj.addProperty("key", key);
        obj.addProperty("latency", latency);
        obj.addProperty("traceId", traceId);
        obj.addProperty("status", status);
        obj.addProperty("operation", operation == null ? null : operation.name());
        obj.addProperty("timestamp", timestamp);
        return obj.toString();
    }

}
