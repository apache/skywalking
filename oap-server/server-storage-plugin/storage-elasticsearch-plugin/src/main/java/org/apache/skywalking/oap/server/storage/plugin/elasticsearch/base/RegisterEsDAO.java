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
import java.util.HashMap;
import java.util.Map;

import org.apache.skywalking.oap.server.core.register.RegisterSource;
import org.apache.skywalking.oap.server.core.storage.IRegisterDAO;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

/**
 * @author peng-yongsheng
 */
public class RegisterEsDAO extends EsDAO implements IRegisterDAO<IndexRequest,UpdateRequest> {

    private final StorageBuilder<RegisterSource> storageBuilder;

    RegisterEsDAO(ElasticSearchClient client, StorageBuilder<RegisterSource> storageBuilder) {
        super(client);
        this.storageBuilder = storageBuilder;
    }

    @Override public RegisterSource get(String modelName, String id) throws IOException {
        GetResponse response = getClient().get(modelName, id);
        if (response.isExists()) {
            return storageBuilder.map2Data(response.getSource());
        } else {
            return null;
        }
    }
    @Override public Map<String, RegisterSource> batchGet(String modelName, String... ids) throws IOException {
        Map<String, RegisterSource> resultMap = new HashMap<>(ids.length);
        Map<String, Map<String, Object>> map = getClient().ids(modelName, ids);
        map.forEach((key, value) -> resultMap.put(key, storageBuilder.map2Data(value)));
        return resultMap;
    }


    @Override public void forceInsert(String modelName, RegisterSource source) throws IOException {
        XContentBuilder builder = map2builder(storageBuilder.data2Map(source));
        getClient().forceInsert(modelName, source.id(), builder);
    }

    @Override public void forceUpdate(String modelName, RegisterSource source) throws IOException {
        XContentBuilder builder = map2builder(storageBuilder.data2Map(source));
        getClient().forceUpdate(modelName, source.id(), builder);
    }
    @Override public IndexRequest prepareBatchInsert(String modelName, RegisterSource source) throws IOException {

        XContentBuilder builder = build(source);
        return getClient().prepareInsert(modelName, source.id(), builder);
    }

    @Override public UpdateRequest prepareBatchUpdate(String modelName, RegisterSource source) throws IOException {

        XContentBuilder builder = build(source);
        return getClient().prepareUpdate(modelName, source.id(), builder);
    }
    private XContentBuilder build(RegisterSource source) throws IOException {
        Map<String, Object> objectMap = storageBuilder.data2Map(source);

        XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
        for (String key : objectMap.keySet()) {
            builder.field(key, objectMap.get(key));
        }
        builder.endObject();
        
        return builder;
    }
}
