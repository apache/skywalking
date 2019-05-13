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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.lock;

import com.google.gson.JsonObject;
import java.io.IOException;
import org.apache.skywalking.oap.server.core.register.worker.InventoryProcess;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.annotation.StorageEntityAnnotationUtils;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.common.xcontent.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class RegisterLockInstaller {

    private static final Logger logger = LoggerFactory.getLogger(RegisterLockInstaller.class);

    private final ElasticSearchClient client;

    public RegisterLockInstaller(ElasticSearchClient client) {
        this.client = client;
    }

    public void install() throws StorageException {
        boolean debug = System.getProperty("debug") != null;

        try {
            if (!client.isExistsIndex(RegisterLockIndex.NAME)) {
                logger.info("table: {} does not exist", RegisterLockIndex.NAME);
                createIndex();
            } else if (debug) {
                logger.info("table: {} exists", RegisterLockIndex.NAME);
                deleteIndex();
                createIndex();
            }

            for (Class registerSource : InventoryProcess.INSTANCE.getAllRegisterSources()) {
                // TODO  pengys
//                int sourceScopeId = StorageEntityAnnotationUtils.getSourceScope(registerSource);
                putIfAbsent(11);
            }
        } catch (IOException e) {
            throw new StorageException(e.getMessage());
        }
    }

    private void deleteIndex() throws IOException {
        client.deleteIndex(RegisterLockIndex.NAME);
    }

    private void createIndex() throws IOException {
        JsonObject settings = new JsonObject();
        settings.addProperty("index.number_of_shards", 1);
        settings.addProperty("index.number_of_replicas", 0);
        settings.addProperty("index.refresh_interval", "1s");

        JsonObject mapping = new JsonObject();
        mapping.add(ElasticSearchClient.TYPE, new JsonObject());

        JsonObject type = mapping.get(ElasticSearchClient.TYPE).getAsJsonObject();

        JsonObject properties = new JsonObject();
        type.add("properties", properties);

        JsonObject column = new JsonObject();
        column.addProperty("type", "integer");
        properties.add(RegisterLockIndex.COLUMN_SEQUENCE, column);

        client.createIndex(RegisterLockIndex.NAME, settings, mapping);
    }

    private void putIfAbsent(int scopeId) throws IOException {
        GetResponse response = client.get(RegisterLockIndex.NAME, String.valueOf(scopeId));
        if (!response.isExists()) {
            XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
            builder.field(RegisterLockIndex.COLUMN_SEQUENCE, 1);
            builder.endObject();

            client.forceInsert(RegisterLockIndex.NAME, String.valueOf(scopeId), builder);
        }
    }
}
