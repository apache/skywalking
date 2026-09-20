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

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.source.Source;

@ScopeDeclaration(id = DefaultScopeDefine.OTLP_SERVICE_SPAN, name = "OTLPServiceSpan")
public class OTLPServiceSpan extends Source {
    @Override
    public int scope() {
        return DefaultScopeDefine.OTLP_SERVICE_SPAN;
    }

    @Override
    public String getEntityId() {
        return serviceName + Const.ID_CONNECTOR + spanName;
    }

    @Setter
    @Getter
    private String serviceName;
    @Setter
    @Getter
    private String spanName;

    @Override
    public String toJson() {
        final JsonObject obj = new JsonObject();
        obj.addProperty("scope", scope());
        obj.addProperty("entityId", getEntityId());
        obj.addProperty("timeBucket", getTimeBucket());
        obj.addProperty("serviceName", serviceName);
        obj.addProperty("spanName", spanName);
        return obj.toString();
    }
}
