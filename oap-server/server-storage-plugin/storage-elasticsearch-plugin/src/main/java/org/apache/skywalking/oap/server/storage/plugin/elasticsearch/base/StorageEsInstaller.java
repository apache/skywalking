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
import java.util.List;
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

    /**
     * The mappings of the template .
     */
    private final IndexStructures structures;

    public StorageEsInstaller(Client client,
                              ModuleManager moduleManager,
                              StorageModuleElasticsearchConfig config) throws StorageException {
        super(client, moduleManager);
        this.columnTypeEsMapping = new ColumnTypeEsMapping();
        this.config = config;
        this.structures = getStructures();
    }

    protected IndexStructures getStructures() {
        return new IndexStructures();
    }

    @Override
    protected boolean isExists(Model model) throws StorageException {
        ElasticSearchClient esClient = (ElasticSearchClient) client;
        String tableName = IndexController.INSTANCE.getTableName(model);
        IndexController.LogicIndicesRegister.registerRelation(model.getName(), tableName);
        try {
            if (!model.isTimeSeries()) {
                return esClient.isExistsIndex(tableName);
            }
            boolean exist = esClient.isExistsTemplate(tableName)
                && esClient.isExistsIndex(TimeSeriesUtils.latestWriteIndexName(model));
            if (exist && IndexController.INSTANCE.isMetricModel(model)) {
                structures.putStructure(
                    tableName, (Map<String, Object>) esClient.getTemplate(tableName).get("mappings")
                );
                exist = structures.containsStructure(tableName, createMapping(model));
            }
            return exist;
        } catch (IOException e) {
            throw new StorageException(e.getMessage());
        }
    }

    @Override
    protected void createTable(Model model) throws StorageException {
        if (model.isTimeSeries()) {
            createTimeSeriesTable(model);
        } else {
            createNormalTable(model);
        }
    }

    private void createNormalTable(Model model) throws StorageException {
        ElasticSearchClient esClient = (ElasticSearchClient) client;
        String tableName = IndexController.INSTANCE.getTableName(model);
        try {
            if (!esClient.isExistsIndex(tableName)) {
                boolean isAcknowledged = esClient.createIndex(tableName);
                log.info("create {} index finished, isAcknowledged: {}", tableName, isAcknowledged);
                if (!isAcknowledged) {
                    throw new StorageException("create " + tableName + " time series index failure, ");
                }
            }
        } catch (IOException e) {
            throw new StorageException("cannot create the normal index", e);
        }
    }

    private void createTimeSeriesTable(Model model) throws StorageException {
        ElasticSearchClient esClient = (ElasticSearchClient) client;
        String tableName = IndexController.INSTANCE.getTableName(model);
        Map<String, Object> settings = createSetting(model);
        Map<String, Object> mapping = createMapping(model);
        String indexName = TimeSeriesUtils.latestWriteIndexName(model);
        try {
            boolean shouldUpdateTemplate = !esClient.isExistsTemplate(tableName);
            if (IndexController.INSTANCE.isMetricModel(model)) {
                shouldUpdateTemplate = shouldUpdateTemplate || !structures.containsStructure(tableName, mapping);
            }
            if (shouldUpdateTemplate) {
                structures.putStructure(tableName, mapping);
                boolean isAcknowledged = esClient.createOrUpdateTemplate(
                    tableName, settings, structures.getMapping(tableName));
                log.info("create {} index template finished, isAcknowledged: {}", tableName, isAcknowledged);
                if (!isAcknowledged) {
                    throw new IOException("create " + tableName + " index template failure, ");
                }

                if (esClient.isExistsIndex(indexName)) {
                    Map<String, Object> historyMapping = (Map<String, Object>) esClient.getIndex(indexName)
                                                                                       .get("mappings");
                    Map<String, Object> appendMapping = structures.diffStructure(tableName, historyMapping);
                    if (!appendMapping.isEmpty()) {
                        isAcknowledged = esClient.updateIndexMapping(indexName, appendMapping);
                        log.info("update {} index finished, isAcknowledged: {}, append mappings: {}", indexName,
                                 isAcknowledged, appendMapping.toString()
                        );
                        if (!isAcknowledged) {
                            throw new StorageException("update " + indexName + " time series index failure");
                        }
                    }
                } else {
                    isAcknowledged = esClient.createIndex(indexName);
                    log.info("create {} index finished, isAcknowledged: {}", indexName, isAcknowledged);
                    if (!isAcknowledged) {
                        throw new StorageException("create " + indexName + " time series index failure");
                    }
                }
            }
        } catch (IOException e) {
            throw new StorageException("cannot create " + tableName + " index template", e);
        }
    }

    protected Map<String, Object> createSetting(Model model) throws StorageException {
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
        setting.put("analysis", getAnalyzerSetting(model.getColumns()));
        if (!StringUtil.isEmpty(config.getAdvanced())) {
            Map<String, Object> advancedSettings = gson.fromJson(config.getAdvanced(), Map.class);
            advancedSettings.forEach(setting::put);
        }
        return setting;
    }

    private Map getAnalyzerSetting(List<ModelColumn> analyzerTypes) throws StorageException {
        AnalyzerSetting analyzerSetting = new AnalyzerSetting();
        for (final ModelColumn column : analyzerTypes) {
            AnalyzerSetting setting = AnalyzerSetting.Generator.getGenerator(column.getAnalyzer())
                                                               .getGenerateFunc()
                                                               .generate(config);
            analyzerSetting.combine(setting);
        }
        return gson.fromJson(gson.toJson(analyzerSetting), Map.class);
    }

    protected Map<String, Object> createMapping(Model model) {
        Map<String, Object> properties = new HashMap<>();
        for (ModelColumn columnDefine : model.getColumns()) {
            if (columnDefine.isMatchQuery()) {
                String matchCName = MatchCNameBuilder.INSTANCE.build(columnDefine.getColumnName().getName());

                Map<String, Object> originalColumn = new HashMap<>();
                originalColumn.put(
                    "type", columnTypeEsMapping.transform(columnDefine.getType(), columnDefine.getGenericType()));
                originalColumn.put("copy_to", matchCName);
                properties.put(columnDefine.getColumnName().getName(), originalColumn);

                Map<String, Object> matchColumn = new HashMap<>();
                matchColumn.put("type", "text");
                matchColumn.put("analyzer", columnDefine.getAnalyzer().getName());
                properties.put(matchCName, matchColumn);
            } else {
                Map<String, Object> column = new HashMap<>();
                column.put(
                    "type", columnTypeEsMapping.transform(columnDefine.getType(), columnDefine.getGenericType()));
                if (columnDefine.isStorageOnly()) {
                    column.put("index", false);
                }
                properties.put(columnDefine.getColumnName().getName(), column);
            }
        }

        if (IndexController.INSTANCE.isMetricModel(model)) {
            Map<String, Object> column = new HashMap<>();
            column.put("type", "keyword");
            properties.put(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME, column);
        }
        Map<String, Object> mappings = this.structures.getWrapper().wrapper(properties);
        log.debug("elasticsearch index template setting: {}", mappings.toString());

        return mappings;
    }
}
