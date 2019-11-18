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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.lock;

import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.lock.RegisterLockIndex;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.lock.RegisterLockInstaller;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author peng-yongsheng
 * @author kezhenxu94
 */
public class RegisterLockEs7Installer extends RegisterLockInstaller {

    public RegisterLockEs7Installer(final ElasticSearchClient client) {
        super(client);
    }

    @Override
    protected void createIndex() throws IOException {
        Map<String, Object> settings = new HashMap<>();
        settings.put("index.number_of_shards", 1);
        settings.put("index.number_of_replicas", 0);
        settings.put("index.refresh_interval", "1s");

        Map<String, Object> mapping = new HashMap<>();

        Map<String, Object> properties = new HashMap<>();
        mapping.put("properties", properties);

        Map<String, Object> column = new HashMap<>();
        column.put("type", "integer");

        properties.put(RegisterLockIndex.COLUMN_SEQUENCE, column);

        client.createIndex(RegisterLockIndex.NAME, settings, mapping);
    }
}
