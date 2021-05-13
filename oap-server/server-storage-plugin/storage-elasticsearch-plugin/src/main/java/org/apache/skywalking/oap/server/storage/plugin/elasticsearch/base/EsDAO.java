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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import java.io.IOException;
import java.util.Map;

import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

public abstract class EsDAO extends AbstractDAO<ElasticSearchClient> {

    public EsDAO(ElasticSearchClient client) {
        super(client);
    }

    protected XContentBuilder map2builder(Map<String, Object> objectMap) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
        for (Map.Entry<String, Object> entries: objectMap.entrySet()) {
            Object value = entries.getValue();
            String key = entries.getKey();
            if (value instanceof StorageDataComplexObject) {
                builder.field(key, ((StorageDataComplexObject) value).toStorageData());
            } else {
                builder.field(key, value);
            }
        }
        builder.endObject();

        return builder;
    }
}
