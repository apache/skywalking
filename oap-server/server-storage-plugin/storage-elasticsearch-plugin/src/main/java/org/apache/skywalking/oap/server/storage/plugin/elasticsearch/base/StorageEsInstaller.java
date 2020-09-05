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

import com.google.gson.Gson;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.elasticsearch.common.unit.TimeValue;

@Slf4j
public class StorageEsInstaller extends ModelInstaller {
    private final Gson gson = new Gson();

    private final StorageModuleElasticsearchConfig config;
    protected final ColumnTypeEsMapping columnTypeEsMapping;

    public StorageEsInstaller(Client client,
                              ModuleManager moduleManager,
                              final StorageModuleElasticsearchConfig config) {
        super(client, moduleManager);
        this.columnTypeEsMapping = new ColumnTypeEsMapping();
        this.config = config;
    }

    @Override
    protected boolean isExists(Model model) throws StorageException {
        ElasticSearchClient esClient = (ElasticSearchClient) client;
        try {
            if (model.isTimeSeries()) {
                return esClient.isExistsTemplate(model.getName()) && esClient.isExistsIndex(
                    TimeSeriesUtils.latestWriteIndexName(model));
            } else {
                return esClient.isExistsIndex(model.getName());
            }
        } catch (IOException e) {
            throw new StorageException(e.getMessage());
        }
    }

    @Override
    protected void createTable(Model model) throws StorageException {
        ElasticSearchClient esClient = (ElasticSearchClient) client;

        Map<String, Object> settings = createSetting(model);
        Map<String, Object> mapping = createMapping(model);
        log.info("index {}'s columnTypeEsMapping builder str: {}", esClient.formatIndexName(model.getName()), mapping
            .toString());

        try {
            String indexName;
            if (!model.isTimeSeries()) {
                indexName = model.getName();
            } else {
                if (!esClient.isExistsTemplate(model.getName())) {
                    boolean isAcknowledged = esClient.createTemplate(model.getName(), settings, mapping);
                    log.info(
                        "create {} index template finished, isAcknowledged: {}", model.getName(), isAcknowledged);
                    if (!isAcknowledged) {
                        throw new StorageException("create " + model.getName() + " index template failure, ");
                    }
                }
                indexName = TimeSeriesUtils.latestWriteIndexName(model);
            }
            if (!esClient.isExistsIndex(indexName)) {
                boolean isAcknowledged = esClient.createIndex(indexName);
                log.info("create {} index finished, isAcknowledged: {}", indexName, isAcknowledged);
                if (!isAcknowledged) {
                    throw new StorageException("create " + indexName + " time series index failure, ");
                }
            }

        } catch (IOException e) {
            throw new StorageException(e.getMessage());
        }
    }

    protected Map<String, Object> createSetting(Model model) {
        Map<String, Object> setting = new HashMap<>();

        setting.put("index.number_of_replicas", model.isSuperDataset()
            ? config.getSuperDatasetIndexReplicasNumber()
            : config.getIndexReplicasNumber());
        setting.put("index.number_of_shards", model.isSuperDataset()
            ? config.getIndexShardsNumber() * config.getSuperDatasetIndexShardsFactor()
            : config.getIndexShardsNumber());
        setting.put("index.refresh_interval", model.isRecord()
            ? TimeValue.timeValueSeconds(10).toString()
            : TimeValue.timeValueSeconds(config.getFlushInterval()).toString());
        setting.put("analysis.analyzer.oap_analyzer.type", "stop");
        if (!StringUtil.isEmpty(config.getAdvanced())) {
            Map<String, Object> advancedSettings = gson.fromJson(config.getAdvanced(), Map.class);
            advancedSettings.forEach(setting::put);
        }
        return setting;
    }

    protected Map<String, Object> createMapping(Model model) {
        Map<String, Object> mapping = new HashMap<>();
        Map<String, Object> type = new HashMap<>();

        mapping.put(ElasticSearchClient.TYPE, type);

        Map<String, Object> properties = new HashMap<>();
        type.put("properties", properties);

        for (ModelColumn columnDefine : model.getColumns()) {
            if (columnDefine.isMatchQuery()) {
                String matchCName = MatchCNameBuilder.INSTANCE.build(columnDefine.getColumnName().getName());

                Map<String, Object> originalColumn = new HashMap<>();
                originalColumn.put("type", columnTypeEsMapping.transform(columnDefine.getType(), columnDefine.getGenericType()));
                originalColumn.put("copy_to", matchCName);
                properties.put(columnDefine.getColumnName().getName(), originalColumn);

                Map<String, Object> matchColumn = new HashMap<>();
                matchColumn.put("type", "text");
                matchColumn.put("analyzer", "oap_analyzer");
                properties.put(matchCName, matchColumn);
            } else {
                Map<String, Object> column = new HashMap<>();
                column.put("type", columnTypeEsMapping.transform(columnDefine.getType(), columnDefine.getGenericType()));
                if (columnDefine.isStorageOnly()) {
                    column.put("index", false);
                }
                properties.put(columnDefine.getColumnName().getName(), column);
            }
        }

        log.debug("elasticsearch index template setting: {}", mapping.toString());

        return mapping;
    }
}
