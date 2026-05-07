/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.core.browser.source;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.source.Source;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.BROWSER_ERROR_LOG;

/**
 * Browser error log raw data
 */
@ScopeDeclaration(id = BROWSER_ERROR_LOG, name = "BrowserErrorLog")
public class BrowserErrorLog extends Source {
    @Override
    public int scope() {
        return BROWSER_ERROR_LOG;
    }

    @Override
    public String getEntityId() {
        return uniqueId;
    }

    @Getter
    @Setter
    private String uniqueId;
    @Getter
    @Setter
    private String serviceId;
    @Getter
    @Setter
    private String serviceVersionId;
    @Getter
    @Setter
    private String pagePathId;
    @Getter
    @Setter
    private long timestamp;
    @Getter
    @Setter
    private BrowserErrorCategory errorCategory;
    @Getter
    @Setter
    private byte[] dataBinary;

    @Override
    public String toJson() {
        final JsonObject obj = new JsonObject();
        obj.addProperty("scope", scope());
        obj.addProperty("entityId", getEntityId());
        obj.addProperty("timeBucket", getTimeBucket());
        obj.addProperty("uniqueId", uniqueId);
        obj.addProperty("serviceId", serviceId);
        obj.addProperty("serviceVersionId", serviceVersionId);
        obj.addProperty("pagePathId", pagePathId);
        obj.addProperty("timestamp", timestamp);
        obj.addProperty("errorCategory", errorCategory == null ? null : errorCategory.name());
        return obj.toString();
    }
}
