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

import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.elasticsearch.common.unit.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author peng-yongsheng
 */
public class StorageEsInstaller extends ModelInstaller {

    private static final Logger logger = LoggerFactory.getLogger(StorageEsInstaller.class);

    protected final int indexShardsNumber;
    protected final int indexReplicasNumber;
    protected final int indexRefreshInterval;
    protected final ColumnTypeEsMapping columnTypeEsMapping;

    public StorageEsInstaller(ModuleManager moduleManager, int indexShardsNumber, int indexReplicasNumber, int indexRefreshInterval) {
        super(moduleManager);
        this.indexShardsNumber = indexShardsNumber;
        this.indexReplicasNumber = indexReplicasNumber;
        this.indexRefreshInterval = indexRefreshInterval;
        this.columnTypeEsMapping = new ColumnTypeEsMapping();
    }

    @Override protected boolean isExists(Client client, Model model) throws StorageException {
        ElasticSearchClient esClient = (ElasticSearchClient)client;
        try {
            if (model.isCapableOfTimeSeries()) {
                return esClient.isExistsTemplate(model.getName()) && esClient.isExistsIndex(model.getName());
            } else {
                return esClient.isExistsIndex(model.getName());
            }
        } catch (IOException e) {
            throw new StorageException(e.getMessage());
        }
    }

    @Override protected void createTable(Client client, Model model) throws StorageException {
        ElasticSearchClient esClient = (ElasticSearchClient)client;

        Map<String, Object> settings = createSetting(model.isRecord());
        Map<String, Object> mapping = createMapping(model);
        logger.info("index {}'s columnTypeEsMapping builder str: {}", esClient.formatIndexName(model.getName()), mapping.toString());

        try {
            if (model.isCapableOfTimeSeries()) {
                if (!esClient.isExistsTemplate(model.getName())) {
                    boolean isAcknowledged = esClient.createTemplate(model.getName(), settings, mapping);
                    logger.info("create {} index template finished, isAcknowledged: {}", model.getName(), isAcknowledged);
                    if (!isAcknowledged) {
                        throw new StorageException("create " + model.getName() + " index template failure, ");
                    }
                }
                if (!esClient.isExistsIndex(model.getName())) {
                    String timeSeriesIndexName = TimeSeriesUtils.timeSeries(model);
                    boolean isAcknowledged = esClient.createIndex(timeSeriesIndexName);
                    logger.info("create {} index finished, isAcknowledged: {}", timeSeriesIndexName, isAcknowledged);
                    if (!isAcknowledged) {
                        throw new StorageException("create " + timeSeriesIndexName + " time series index failure, ");
                    }
                }
            } else {
                boolean isAcknowledged = esClient.createIndex(model.getName(), settings, mapping);
                logger.info("create {} index finished, isAcknowledged: {}", model.getName(), isAcknowledged);
                if (!isAcknowledged) {
                    throw new StorageException("create " + model.getName() + " index failure, ");
                }
            }
        } catch (IOException e) {
            throw new StorageException(e.getMessage());
        }
    }

    protected Map<String, Object> createSetting(boolean record) {
        Map<String, Object> setting = new HashMap<>();
        setting.put("index.number_of_shards", indexShardsNumber);
        setting.put("index.number_of_replicas", indexReplicasNumber);
        setting.put("index.refresh_interval", record ? TimeValue.timeValueSeconds(10).toString() : TimeValue.timeValueSeconds(indexRefreshInterval).toString());
        setting.put("analysis.analyzer.oap_analyzer.type", "stop");
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
                originalColumn.put("type", columnTypeEsMapping.transform(columnDefine.getType()));
                originalColumn.put("copy_to", matchCName);
                properties.put(columnDefine.getColumnName().getName(), originalColumn);

                Map<String, Object> matchColumn = new HashMap<>();
                matchColumn.put("type", "text");
                matchColumn.put("analyzer", "oap_analyzer");
                properties.put(matchCName, matchColumn);
            } else if (columnDefine.isContent()) {
                Map<String, Object> column = new HashMap<>();
                column.put("type", "text");
                column.put("index", false);
                properties.put(columnDefine.getColumnName().getName(), column);
            } else {
                Map<String, Object> column = new HashMap<>();
                column.put("type", columnTypeEsMapping.transform(columnDefine.getType()));
                properties.put(columnDefine.getColumnName().getName(), column);
            }
        }

        logger.debug("elasticsearch index template setting: {}", mapping.toString());

        return mapping;
    }
}
