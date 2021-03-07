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
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.RunningMode;
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
    private final Map<String, Map<String, Object>> tables;

    public StorageEsInstaller(Client client,
                              ModuleManager moduleManager,
                              StorageModuleElasticsearchConfig config) throws StorageException {
        super(client, moduleManager);
        this.columnTypeEsMapping = new ColumnTypeEsMapping();
        this.config = config;
        this.tables = new HashMap<>();
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
            if (RunningMode.isInitMode() && IndexController.INSTANCE.isAggregationMode(model)) {
                appendTemplateMapping(tableName, (Map<String, Object>) esClient.getTemplate(tableName).get("mappings"));
                exist = exist && isTemplateMappingCompatible(tableName, createMapping(model));
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
        IndexController.LogicIndicesRegister.registerRelation(model.getName(), tableName);
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
        IndexController.LogicIndicesRegister.registerRelation(model.getName(), tableName);
        Map<String, Object> settings = createSetting(model);
        Map<String, Object> mapping = createMapping(model);
        String indexName = TimeSeriesUtils.latestWriteIndexName(model);
        try {
            boolean updateTemplate = !esClient.isExistsTemplate(tableName);
            if (!updateTemplate) {
                appendTemplateMapping(tableName, (Map<String, Object>) esClient.getTemplate(tableName)
                                                                               .get("mappings"));
                updateTemplate = !isTemplateMappingCompatible(tableName, mapping);
            }
            if (updateTemplate) {
                Map<String, Object> templateMapping = appendTemplateMapping(tableName, mapping);
                boolean isAcknowledged = esClient.createOrUpdateTemplate(tableName, settings, templateMapping);
                log.info("create {} index template finished, isAcknowledged: {}", tableName, isAcknowledged);
                if (!isAcknowledged) {
                    throw new IOException("create " + tableName + " index template failure, ");
                }

                if (esClient.isExistsIndex(indexName)) {
                    Map<String, Object> historyMapping = (Map<String, Object>) esClient.getIndex(indexName)
                                                                                       .get("mappings");
                    Map<String, Object> appendMapping = extractAppendMapping(templateMapping, historyMapping);
                    if (appendMapping.size() > 0) {
                        esClient.updateIndexMapping(indexName, appendMapping);
                    }
                } else {
                    isAcknowledged = esClient.createIndex(indexName);
                    log.info("create {} index finished, isAcknowledged: {}", indexName, isAcknowledged);
                    if (!isAcknowledged) {
                        throw new StorageException("create " + indexName + " time series index failure, ");
                    }
                }
            }
        } catch (IOException e) {
            throw new StorageException("cannot create " + tableName + " index template", e);
        }
    }

    private Map<String, Object> extractAppendMapping(final Map<String, Object> latestMapping,
                                                     final Map<String, Object> historyMapping) {
        Map<String, Object> checkingFields = getColumnProperties(latestMapping);
        Map<String, Object> existFields = getColumnProperties(historyMapping);
        Map<String, Object> newFields = checkingFields.entrySet()
                                                      .stream()
                                                      .filter(item -> !existFields.containsKey(item.getKey()))
                                                      .collect(Collectors.toMap(
                                                          Map.Entry::getKey, Map.Entry::getValue));
        Map<String, Object> mapping = createEmptyMapping();
        Map<String, Object> properties = getColumnProperties(mapping);
        newFields.forEach(properties::put);
        return mapping;
    }

    /**
     * Append the mapping to the tables with the same table name key.
     */
    private Map<String, Object> appendTemplateMapping(String tableName, Map<String, Object> mapping) {
        if (Objects.isNull(mapping) || mapping.size() == 0) {
            return Optional.ofNullable(tables.get(tableName)).orElse(new HashMap<>());
        }
        if (!tables.containsKey(tableName)) {
            tables.put(tableName, mapping);
            return mapping;
        }
        Map<String, Object> existMapping = tables.get(tableName);
        Map<String, Object> appendMapping = extractAppendMapping(mapping, existMapping);
        Map<String, Object> newColumns = getColumnProperties(appendMapping);
        Map<String, Object> existFields = getColumnProperties(existMapping);
        newColumns.forEach(existFields::put);
        return existMapping;
    }

    protected Map<String, Object> getColumnProperties(Map<String, Object> mapping) {
        if (Objects.isNull(mapping) || mapping.size() == 0) {
            return new HashMap<>();
        }
        return (Map<String, Object>) ((Map<String, Object>) mapping.get(ElasticSearchClient.TYPE)).get("properties");
    }

    /**
     * Whether the tables contains the input mapping with the same table name key.
     */
    private boolean isTemplateMappingCompatible(String tableName, Map<String, Object> mapping) {
        if (!tables.containsKey(tableName)) {
            return false;
        }
        Map<String, Object> existMapping = tables.get(tableName);
        Map<String, Object> existFields = getColumnProperties(existMapping);
        Map<String, Object> checkingFields = getColumnProperties(mapping);
        return checkingFields.entrySet()
                             .stream().allMatch(item -> existFields.containsKey(item.getKey()));
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

    protected Map<String, Object> createEmptyMapping() {
        Map<String, Object> mapping = new HashMap<>();
        Map<String, Object> type = new HashMap<>();
        mapping.put(ElasticSearchClient.TYPE, type);
        Map<String, Object> properties = new HashMap<>();
        type.put("properties", properties);
        return mapping;
    }

    protected Map<String, Object> createMapping(Model model) {
        Map<String, Object> mapping = createEmptyMapping();
        Map<String, Object> properties = getColumnProperties(mapping);

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

        if (IndexController.INSTANCE.isAggregationMode(model)) {
            Map<String, Object> column = new HashMap<>();
            column.put("type", "keyword");
            properties.put(IndexController.LogicIndicesRegister.LOGIC_TABLE_NAME, column);
        }

        log.debug("elasticsearch index template setting: {}", mapping.toString());

        return mapping;
    }
}
