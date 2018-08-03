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
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.storage.IPersistenceDAO;
import org.apache.skywalking.oap.server.library.client.NameSpace;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.*;

/**
 * @author peng-yongsheng
 */
public class PersistenceEsDAO implements IPersistenceDAO<IndexRequest, UpdateRequest, Indicator> {

    private final ElasticSearchClient client;
    private final NameSpace nameSpace;

    public PersistenceEsDAO(ElasticSearchClient client, NameSpace nameSpace) {
        this.client = client;
        this.nameSpace = nameSpace;
    }

    @Override public Indicator get(Indicator input) throws IOException {
        GetResponse response = client.get(nameSpace.getNameSpace() + "_" + input.name(), input.id());
        if (response.isExists()) {
            return input.newOne(response.getSource());
        } else {
            return null;
        }
    }

    @Override public IndexRequest prepareBatchInsert(Indicator input) throws IOException {
        Map<String, Object> objectMap = input.toMap();

        XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
        for (String key : objectMap.keySet()) {
            builder.field(key, objectMap.get(key));
        }
        builder.endObject();
        return client.prepareInsert(nameSpace.getNameSpace() + "_" + input.name(), input.id(), builder);
    }

    @Override public UpdateRequest prepareBatchUpdate(Indicator input) throws IOException {
        Map<String, Object> objectMap = input.toMap();

        XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
        for (String key : objectMap.keySet()) {
            builder.field(key, objectMap.get(key));
        }
        builder.endObject();
        return client.prepareUpdate(nameSpace.getNameSpace() + "_" + input.name(), input.id(), builder);
    }

    @Override public void deleteHistory(Long timeBucketBefore) {

    }
}
