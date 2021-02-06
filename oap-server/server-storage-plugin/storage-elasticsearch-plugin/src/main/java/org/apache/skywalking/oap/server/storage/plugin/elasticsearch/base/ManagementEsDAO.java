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
import org.apache.skywalking.oap.server.core.analysis.management.ManagementData;
import org.apache.skywalking.oap.server.core.storage.IManagementDAO;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;

public class ManagementEsDAO extends EsDAO implements IManagementDAO {
    private final StorageHashMapBuilder<ManagementData> storageBuilder;

    public ManagementEsDAO(ElasticSearchClient client, StorageHashMapBuilder<ManagementData> storageBuilder) {
        super(client);
        this.storageBuilder = storageBuilder;
    }

    @Override
    public void insert(Model model, ManagementData managementData) throws IOException {
        String modelName = model.getName();
        final String id = managementData.id();
        final GetResponse response = getClient().get(modelName, id);
        if (response.isExists()) {
            return;
        }

        XContentBuilder builder = map2builder(storageBuilder.entity2Storage(managementData));
        getClient().forceInsert(modelName, id, builder);
    }
}