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

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.elasticsearch.response.Index;
import org.apache.skywalking.library.elasticsearch.response.IndexTemplate;
import org.apache.skywalking.library.elasticsearch.response.Mappings;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class StorageEsInstaller extends ModelInstaller {
    private final Gson gson = new Gson();
    private final StorageModuleElasticsearchConfig config;
    protected final ColumnTypeEsMapping columnTypeEsMapping;
    private final Map<String, Map<String, Object>> specificIndexesSettings;
    @Setter
    private int indexRefreshInterval = 30;

    /**
     * The mappings of the template .
     */
    private final IndexStructures structures;

    public StorageEsInstaller(Client client,
                              ModuleManager moduleManager,
                              StorageModuleElasticsearchConfig config) {
        super(client, moduleManager);
        this.columnTypeEsMapping = new ColumnTypeEsMapping();
        this.config = config;
        this.structures = getStructures();
        if (StringUtil.isNotEmpty(config.getSpecificIndexSettings())) {
            this.specificIndexesSettings = gson.fromJson(
                config.getSpecificIndexSettings(), new TypeReference<Map<String, Map<String, Object>>>() {
                }.getType());
        } else {
            this.specificIndexesSettings = Collections.emptyMap();
        }
    }

    protected IndexStructures getStructures() {
        return new IndexStructures();
    }

    @Override
    public boolean isExists(Model model) throws StorageException {
        ElasticSearchClient esClient = (ElasticSearchClient) client;
        String tableName = IndexController.INSTANCE.getTableName(model);
        IndexController.LogicIndicesRegister.registerRelation(model, tableName);
        if (!model.isTimeSeries()) {
            boolean exist = esClient.isExistsIndex(tableName);
            if (exist) {
                Optional<Index> index = esClient.getIndex(tableName);
                Mappings historyMapping = index.map(Index::getMappings).orElseGet(Mappings::new);
                structures.putStructure(tableName, historyMapping, index.map(Index::getSettings).orElseGet(HashMap::new));
                boolean containsMapping = structures.containsMapping(tableName, createMapping(model));
                // Do not check index settings in the "no-init mode",
                // because the no-init mode OAP server doesn't take responsibility for index settings.
                if (RunningMode.isNoInitMode()) {
                    exist = containsMapping;
                } else {
                    exist = containsMapping && structures.compareIndexSetting(tableName, createSetting(model));
                }
            }
            return exist;
        }

        boolean templateExists = esClient.isExistsTemplate(tableName);
        final Optional<IndexTemplate> template = esClient.getTemplate(tableName);

        if ((templateExists && !template.isPresent()) || (!templateExists && template.isPresent())) {
            throw new Error("[Bug warning] ElasticSearch client query template result is not consistent. " +
                                "Please file an issue to Apache SkyWalking.(https://github.com/apache/skywalking/issues)");
        }

        boolean exist = templateExists;

        if (exist) {
            structures.putStructure(
                tableName, template.get().getMappings(), template.get().getSettings()
            );
            boolean containsMapping = structures.containsMapping(tableName, createMapping(model));
            // Do not check index settings in the "no-init mode",
            // because the no-init mode OAP server doesn't take responsibility for index settings.
            if (RunningMode.isNoInitMode()) {
                exist = containsMapping;
            } else {
                exist = containsMapping && structures.compareIndexSetting(tableName, createSetting(model));
            }
        }
        return exist;
    }

    @Override
    public void createTable(Model model) throws StorageException {
        if (model.isTimeSeries()) {
            createTimeSeriesTable(model);
        } else {
            createNormalTable(model);
        }
    }

    private void createNormalTable(Model model) throws StorageException {
        ElasticSearchClient esClient = (ElasticSearchClient) client;
        String tableName = IndexController.INSTANCE.getTableName(model);
        Mappings mapping = createMapping(model);
        Map<String, Object> settings = createSetting(model);
        if (!esClient.isExistsIndex(tableName)) {
            boolean isAcknowledged = esClient.createIndex(tableName, mapping, settings);
            log.info("create {} index finished, isAcknowledged: {}", tableName, isAcknowledged);
            if (!isAcknowledged) {
                throw new StorageException("create " + tableName + " index failure");
            }
        } else {
            Mappings historyMapping = esClient.getIndex(tableName)
                                              .map(Index::getMappings)
                                              .orElseGet(Mappings::new);
            structures.putStructure(tableName, mapping, settings);
            Mappings appendMapping = structures.diffMappings(tableName, historyMapping);
            //update mapping
            if (appendMapping.getProperties() != null && !appendMapping.getProperties().isEmpty()) {
                boolean isAcknowledged = esClient.updateIndexMapping(tableName, appendMapping);
                log.info("update {} index mapping finished, isAcknowledged: {}, append mapping: {}", tableName,
                         isAcknowledged, appendMapping
                );
                if (!isAcknowledged) {
                    throw new StorageException("update " + tableName + " index mapping failure");
                }
            }
            //needs to update settings
            if (!structures.compareIndexSetting(tableName, settings)) {
                log.warn(
                    "index {} settings configuration has been updated to {}, please remove it before OAP starts",
                    tableName, settings
                );
            }
        }
    }

    private void createTimeSeriesTable(Model model) throws StorageException {
        ElasticSearchClient esClient = (ElasticSearchClient) client;
        String tableName = IndexController.INSTANCE.getTableName(model);
        Map<String, Object> settings = createSetting(model);
        Mappings mapping = createMapping(model);
        String indexName = TimeSeriesUtils.latestWriteIndexName(model);
        try {
            boolean shouldUpdateTemplate = !esClient.isExistsTemplate(tableName);
            shouldUpdateTemplate = shouldUpdateTemplate
                || !structures.containsMapping(tableName, mapping)
                || !structures.compareIndexSetting(tableName, settings);
            if (shouldUpdateTemplate) {
                structures.putStructure(tableName, mapping, settings);
                boolean isAcknowledged = esClient.createOrUpdateTemplate(
                    tableName, settings, structures.getMapping(tableName), config.getIndexTemplateOrder());
                log.info("create {} index template finished, isAcknowledged: {}", tableName, isAcknowledged);
                if (!isAcknowledged) {
                    throw new IOException("create " + tableName + " index template failure");
                }
            }

            if (esClient.isExistsIndex(indexName)) {
                Mappings historyMapping = esClient.getIndex(indexName)
                                                  .map(Index::getMappings)
                                                  .orElseGet(Mappings::new);
                Mappings appendMapping = structures.diffMappings(tableName, historyMapping);
                //update mapping
                if (appendMapping.getProperties() != null && !appendMapping.getProperties().isEmpty()) {
                    boolean isAcknowledged = esClient.updateIndexMapping(indexName, appendMapping);
                    log.info("update {} index finished, isAcknowledged: {}, append mappings: {}", indexName,
                             isAcknowledged, appendMapping
                    );
                    if (!isAcknowledged) {
                        throw new StorageException("update " + indexName + " time series index failure");
                    }
                }

                //needs to update settings
                if (!structures.compareIndexSetting(tableName, settings)) {
                    log.info(
                        "index template {} settings configuration has been updated to {}, it will applied on new index",
                        tableName, settings
                    );
                }
            } else {
                boolean isAcknowledged = esClient.createIndex(indexName);
                log.info("create {} index finished, isAcknowledged: {}", indexName, isAcknowledged);
                if (!isAcknowledged) {
                    throw new StorageException("create " + indexName + " time series index failure");
                }
            }
        } catch (IOException e) {
            throw new StorageException("cannot create " + tableName + " index template", e);
        }
    }

    protected Map<String, Object> createSetting(Model model) throws StorageException {
        Map<String, Object> setting = new HashMap<>();
        Map<String, Object> indexSettings = new HashMap<>();
        setting.put("index", indexSettings);
        indexSettings.put("number_of_replicas", model.isSuperDataset()
            ? Integer.toString(config.getSuperDatasetIndexReplicasNumber())
            : Integer.toString(config.getIndexReplicasNumber()));
        indexSettings.put("number_of_shards", model.isSuperDataset()
            ? Integer.toString(config.getIndexShardsNumber() * config.getSuperDatasetIndexShardsFactor())
            : Integer.toString(config.getIndexShardsNumber()));
        indexSettings.put("refresh_interval", indexRefreshInterval + "s");
        indexSettings.put("analysis", getAnalyzerSetting(model));
        if (!StringUtil.isEmpty(config.getAdvanced())) {
            Map<String, Object> advancedSettings = gson.fromJson(config.getAdvanced(), Map.class);
            setting.putAll(advancedSettings);
        }

        //Set the config for the specific index, if has been configured.
        Map<String, Object> specificSettings = this.specificIndexesSettings.get(IndexController.INSTANCE.getTableName(model));
        if (!CollectionUtils.isEmpty(specificSettings)) {
            indexSettings.putAll(specificSettings);
        }
        
        return setting;
    }

    //In the `No-Sharding Mode`:
    //https://skywalking.apache.org/docs/main/next/en/faq/new-elasticsearch-storage-option-explanation-in-9.2.0/
    //Some of models require a analyzer to run match query, some others are not.
    //They are merged into the one physical index(metrics-all or record-all)
    //When adding a new model(with an analyzer) into an existed index by update will be failed, if the index is without analyzer settings.
    //To avoid this, add the analyzer settings to the template before index creation.
    private Map getAnalyzerSetting(Model model) throws StorageException {
        if (config.isLogicSharding() || !model.isTimeSeries()) {
            return getAnalyzerSettingByColumn(model);
        } else if (IndexController.INSTANCE.isRecordModel(model) && model.isSuperDataset()) {
            //SuperDataset doesn't merge index, the analyzer follow the column config.
            return getAnalyzerSettingByColumn(model);
        } else {
            return getAnalyzerSetting4MergedIndex(model);
        }
    }

    private Map getAnalyzerSettingByColumn(Model model) throws StorageException {
        AnalyzerSetting analyzerSetting = new AnalyzerSetting();
        List<ModelColumn> analyzerTypes = IndexController.LogicIndicesRegister.getPhysicalTableColumns(model);
        for (final ModelColumn column : analyzerTypes) {
            if (!column.getElasticSearchExtension().needMatchQuery()) {
                continue;
            }
            AnalyzerSetting setting = AnalyzerSetting.Generator.getGenerator(
                                                         column.getElasticSearchExtension().getAnalyzer())
                                                               .getGenerateFunc()
                                                               .generate(config);
            analyzerSetting.combine(setting);
        }
        return gson.fromJson(gson.toJson(analyzerSetting), Map.class);
    }

    //Indexes `metrics-all and records-all` are required `OAP_ANALYZER`
    private Map getAnalyzerSetting4MergedIndex(Model model) throws StorageException {
        AnalyzerSetting setting = AnalyzerSetting.Generator.getGenerator(
                                                     ElasticSearch.MatchQuery.AnalyzerType.OAP_ANALYZER)
                                                           .getGenerateFunc()
                                                           .generate(config);

        return gson.fromJson(gson.toJson(setting), Map.class);
    }

    protected Mappings createMapping(Model model) {
        Map<String, Object> properties = new HashMap<>();
        Mappings.Source source = new Mappings.Source();
        for (ModelColumn columnDefine : model.getColumns()) {
            final String type = columnTypeEsMapping.transform(columnDefine.getType(), columnDefine.getGenericType(), columnDefine.getElasticSearchExtension());
            String columnName = columnDefine.getColumnName().getName();
            String legacyName = columnDefine.getColumnName().getLegacyName();
            if (config.isLogicSharding() && !Strings.isNullOrEmpty(legacyName)) {
                columnName = legacyName;
            }
            if (columnDefine.getElasticSearchExtension().needMatchQuery()) {
                String matchCName = MatchCNameBuilder.INSTANCE.build(columnName);

                Map<String, Object> originalColumn = new HashMap<>();
                originalColumn.put("type", type);
                originalColumn.put("copy_to", matchCName);
                properties.put(columnName, originalColumn);

                Map<String, Object> matchColumn = new HashMap<>();
                matchColumn.put("type", "text");
                matchColumn.put("analyzer", columnDefine.getElasticSearchExtension().getAnalyzer().getName());
                properties.put(matchCName, matchColumn);
            } else {
                Map<String, Object> column = new HashMap<>();
                column.put("type", type);
                // no index parameter is allowed for binary type, since ES 8.0
                if (columnDefine.isStorageOnly() && !"binary".equals(type)) {
                    column.put("index", false);
                }
                properties.put(columnName, column);
            }

            if (columnDefine.isIndexOnly()) {
                source.getExcludes().add(columnName);
            }
        }

        if ((IndexController.INSTANCE.isMetricModel(model) && !config.isLogicSharding())
            || (config.isLogicSharding() && IndexController.INSTANCE.isFunctionMetric(model))) {
            Map<String, Object> column = new HashMap<>();
            column.put("type", "keyword");
            properties.put(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME, column);
        }
        if (!config.isLogicSharding() && IndexController.INSTANCE.isRecordModel(model) && !model.isSuperDataset()) {
            Map<String, Object> column = new HashMap<>();
            column.put("type", "keyword");
            properties.put(IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, column);
        }
        Mappings mappings = Mappings.builder()
                                    .type("type")
                                    .properties(properties)
                                    .source(source)
                                    .build();
        log.debug("elasticsearch index template setting: {}", mappings.toString());

        return mappings;
    }
}
