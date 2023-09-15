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
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.analysis.management.ManagementData;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.StringUtil;

public abstract class ManagementCRUDEsDAO extends EsDAO {
    private final StorageBuilder<ManagementData> storageBuilder;

    public ManagementCRUDEsDAO(ElasticSearchClient client,
                               StorageBuilder<ManagementData> storageBuilder) {
        super(client);
        this.storageBuilder = storageBuilder;
    }

    /**
     * @param modelName    the name of the model
     * @param managementData the data to create
     * @return true if create success, false if already exist
     * @throws IOException if any IO exception occurred
     */
    public boolean create(String modelName, ManagementData managementData) throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(modelName);

        final ElasticSearchConverter.ToStorage toStorage = new ElasticSearchConverter.ToStorage(modelName);
        storageBuilder.entity2Storage(managementData, toStorage);
        final String docId = IndexController.INSTANCE.generateDocId(modelName, managementData.id().build());
        final boolean exist = getClient().existDoc(modelName, docId);
        if (exist) {
            return false;
        }
        Map<String, Object> source =
            IndexController.INSTANCE.appendTableColumn4ManagementData(modelName, toStorage.obtain());
        getClient().forceInsert(index, docId, source);
        return true;
    }

    /**
     * @param modelName the name of the model
     * @param id       the id of the data
     * @return null if not found
     * @throws IOException if any IO exception occurred
     */
    public ManagementData getById(String modelName, String id) throws IOException {
        if (StringUtil.isEmpty(id)) {
            return null;
        }
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(modelName);
        final String docId = IndexController.INSTANCE.generateDocId(modelName, id);
        final SearchBuilder search =
            Search.builder().query(Query.ids(docId)).size(1);
        final SearchResponse response = getClient().search(index, search.build());

        if (response.getHits().getHits().size() > 0) {

            SearchHit data = response.getHits().getHits().get(0);
            return storageBuilder.storage2Entity(new ElasticSearchConverter.ToEntity(modelName, data.getSource()));
        }
        return null;
    }

    /**
     * @param modelName the name of the model
     * @param managementData the data to update
     * @return true if update success, false if not found
     * @throws IOException if any IO exception occurred
     */
    public boolean update(String modelName, ManagementData managementData) throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(modelName);

        final ElasticSearchConverter.ToStorage toStorage = new ElasticSearchConverter.ToStorage(modelName);
        storageBuilder.entity2Storage(managementData, toStorage);
        final String docId = IndexController.INSTANCE.generateDocId(modelName, managementData.id().build());
        final boolean exist = getClient().existDoc(modelName, docId);
        if (!exist) {
            return false;
        }
        Map<String, Object> source =
            IndexController.INSTANCE.appendTableColumn4ManagementData(modelName, toStorage.obtain());
        getClient().forceUpdate(index, docId, source);
        return true;
    }

    /**
     * @param modelName the name of the model
     * @param id      the id of the data
     * @return true if delete success, false if not found
     * @throws IOException if any IO exception occurred
     */
    public boolean deleteById(String modelName, String id) throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(modelName);
        final String docId = IndexController.INSTANCE.generateDocId(modelName, id);
        final boolean exist = getClient().existDoc(modelName, docId);
        if (!exist) {
            return false;
        }
        getClient().deleteById(index, docId);
        return true;
    }
}
