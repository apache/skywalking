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
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataType;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class IndicatorEsDAO extends EsDAO implements IIndicatorDAO<IndexRequest, UpdateRequest> {

    private static final Logger logger = LoggerFactory.getLogger(IndicatorEsDAO.class);

    private final StorageBuilder<Indicator> storageBuilder;

    public IndicatorEsDAO(ElasticSearchClient client, StorageBuilder<Indicator> storageBuilder) {
        super(client);
        this.storageBuilder = storageBuilder;
    }

    @Override public Indicator get(String modelName, Indicator indicator) throws IOException {
        GetResponse response = getClient().get(modelName, indicator.id());
        if (response.isExists()) {
            return storageBuilder.map2Data(response.getSource());
        } else {
            return null;
        }
    }

    @Override public IndexRequest prepareBatchInsert(String modelName, Indicator indicator) throws IOException {
        Map<String, Object> objectMap = storageBuilder.data2Map(indicator);

        XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
        for (String key : objectMap.keySet()) {
            Object value = objectMap.get(key);
            if (value instanceof StorageDataType) {
                builder.field(key, ((StorageDataType)value).toStorageData());
            } else {
                builder.field(key, value);
            }
        }
        builder.endObject();
        return getClient().prepareInsert(modelName, indicator.id(), builder);
    }

    @Override public UpdateRequest prepareBatchUpdate(String modelName, Indicator indicator) throws IOException {
        Map<String, Object> objectMap = storageBuilder.data2Map(indicator);

        XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
        for (String key : objectMap.keySet()) {
            Object value = objectMap.get(key);
            if (value instanceof StorageDataType) {
                builder.field(key, ((StorageDataType)value).toStorageData());
            } else {
                builder.field(key, value);
            }
        }
        builder.endObject();
        return getClient().prepareUpdate(modelName, indicator.id(), builder);
    }
}
