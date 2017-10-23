/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.storage.elasticsearch.define;

import java.io.IOException;
import java.util.List;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.IndexNotFoundException;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.storage.ColumnDefine;
import org.skywalking.apm.collector.core.storage.StorageInstaller;
import org.skywalking.apm.collector.core.storage.TableDefine;
import org.skywalking.apm.collector.storage.elasticsearch.StorageElasticSearchConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ElasticSearchStorageInstaller extends StorageInstaller {

    private final Logger logger = LoggerFactory.getLogger(ElasticSearchStorageInstaller.class);

    @Override protected void defineFilter(List<TableDefine> tableDefines) {
        int size = tableDefines.size();
        for (int i = size - 1; i >= 0; i--) {
            if (!(tableDefines.get(i) instanceof ElasticSearchTableDefine)) {
                tableDefines.remove(i);
            }
        }
    }

    @Override protected boolean createTable(Client client, TableDefine tableDefine) {
        ElasticSearchClient esClient = (ElasticSearchClient)client;
        ElasticSearchTableDefine esTableDefine = (ElasticSearchTableDefine)tableDefine;
        // mapping
        XContentBuilder mappingBuilder = null;

        Settings settings = createSettingBuilder(esTableDefine);
        try {
            mappingBuilder = createMappingBuilder(esTableDefine);
            logger.info("mapping builder str: {}", mappingBuilder.string());
        } catch (Exception e) {
            logger.error("create {} index mapping builder error", esTableDefine.getName());
        }

        boolean isAcknowledged = esClient.createIndex(esTableDefine.getName(), esTableDefine.type(), settings, mappingBuilder);
        logger.info("create {} index with type of {} finished, isAcknowledged: {}", esTableDefine.getName(), esTableDefine.type(), isAcknowledged);
        return isAcknowledged;
    }

    private Settings createSettingBuilder(ElasticSearchTableDefine tableDefine) {
        return Settings.builder()
            .put("index.number_of_shards", StorageElasticSearchConfig.INDEX_SHARDS_NUMBER)
            .put("index.number_of_replicas", StorageElasticSearchConfig.INDEX_REPLICAS_NUMBER)
            .put("index.refresh_interval", String.valueOf(tableDefine.refreshInterval()) + "s")

            .put("analysis.analyzer.collector_analyzer.tokenizer", "collector_tokenizer")
            .put("analysis.tokenizer.collector_tokenizer.type", "standard")
            .put("analysis.tokenizer.collector_tokenizer.max_token_length", 5)
            .build();
    }

    private XContentBuilder createMappingBuilder(ElasticSearchTableDefine tableDefine) throws IOException {
        XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
            .startObject()
            .startObject("properties");

        for (ColumnDefine columnDefine : tableDefine.getColumnDefines()) {
            ElasticSearchColumnDefine elasticSearchColumnDefine = (ElasticSearchColumnDefine)columnDefine;

            if (ElasticSearchColumnDefine.Type.Text.name().toLowerCase().equals(elasticSearchColumnDefine.getType().toLowerCase())) {
                mappingBuilder
                    .startObject(elasticSearchColumnDefine.getName())
                    .field("type", elasticSearchColumnDefine.getType().toLowerCase())
                    .field("fielddata", true)
                    .endObject();
            } else {
                mappingBuilder
                    .startObject(elasticSearchColumnDefine.getName())
                    .field("type", elasticSearchColumnDefine.getType().toLowerCase())
                    .endObject();
            }
        }

        mappingBuilder
            .endObject()
            .endObject();
        logger.debug("create elasticsearch index: {}", mappingBuilder.string());
        return mappingBuilder;
    }

    @Override protected boolean deleteTable(Client client, TableDefine tableDefine) {
        ElasticSearchClient esClient = (ElasticSearchClient)client;
        try {
            return esClient.deleteIndex(tableDefine.getName());
        } catch (IndexNotFoundException e) {
            logger.info("{} index not found", tableDefine.getName());
        }
        return false;
    }

    @Override protected boolean isExists(Client client, TableDefine tableDefine) {
        ElasticSearchClient esClient = (ElasticSearchClient)client;
        return esClient.isExistsIndex(tableDefine.getName());
    }
}
