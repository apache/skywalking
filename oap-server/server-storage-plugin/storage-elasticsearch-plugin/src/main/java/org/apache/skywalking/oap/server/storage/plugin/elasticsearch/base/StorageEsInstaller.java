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
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.*;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class StorageEsInstaller extends ModelInstaller {

    private static final Logger logger = LoggerFactory.getLogger(StorageEsInstaller.class);

    private final int indexShardsNumber;
    private final int indexReplicasNumber;
    private final ColumnTypeEsMapping mapping;

    public StorageEsInstaller(ModuleManager moduleManager, int indexShardsNumber, int indexReplicasNumber) {
        super(moduleManager);
        this.indexShardsNumber = indexShardsNumber;
        this.indexReplicasNumber = indexReplicasNumber;
        this.mapping = new ColumnTypeEsMapping();
    }

    @Override protected boolean isExists(Client client, Model tableDefine) throws StorageException {
        ElasticSearchClient esClient = (ElasticSearchClient)client;
        try {
            return esClient.isExistsIndex(tableDefine.getName());
        } catch (IOException e) {
            throw new StorageException(e.getMessage());
        }
    }

    @Override protected void columnCheck(Client client, Model tableDefine) {

    }

    @Override protected void deleteTable(Client client, Model tableDefine) throws StorageException {
        ElasticSearchClient esClient = (ElasticSearchClient)client;

        try {
            if (!esClient.deleteIndex(tableDefine.getName())) {
                throw new StorageException(tableDefine.getName() + " index delete failure.");
            }
        } catch (IOException e) {
            throw new StorageException(tableDefine.getName() + " index delete failure.");
        }
    }

    @Override protected void createTable(Client client, Model tableDefine) throws StorageException {
        ElasticSearchClient esClient = (ElasticSearchClient)client;

        // mapping
        XContentBuilder mappingBuilder = null;

        Settings settings = createSettingBuilder();
        try {
            mappingBuilder = createMappingBuilder(tableDefine);
            logger.info("index {}'s mapping builder str: {}", tableDefine.getName(), Strings.toString(mappingBuilder.prettyPrint()));
        } catch (Exception e) {
            logger.error("create {} index mapping builder error, error message: {}", tableDefine.getName(), e.getMessage());
        }

        boolean isAcknowledged;
        try {
            isAcknowledged = esClient.createIndex(tableDefine.getName(), settings, mappingBuilder);
        } catch (IOException e) {
            throw new StorageException(e.getMessage());
        }
        logger.info("create {} index finished, isAcknowledged: {}", tableDefine.getName(), isAcknowledged);

        if (!isAcknowledged) {
            throw new StorageException("create " + tableDefine.getName() + " index failure, ");
        }
    }

    private Settings createSettingBuilder() {
        return Settings.builder()
            .put("index.number_of_shards", indexShardsNumber)
            .put("index.number_of_replicas", indexReplicasNumber)
            .put("index.refresh_interval", "3s")
            .put("analysis.analyzer.oap_analyzer.type", "stop")
            .build();
    }

    private XContentBuilder createMappingBuilder(Model tableDefine) throws IOException {
        XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
            .startObject()
            .startObject("_all")
            .field("enabled", false)
            .endObject()
            .startObject("properties");

        for (ModelColumn columnDefine : tableDefine.getColumns()) {
            if (columnDefine.isMatchQuery()) {
                String matchCName = MatchCNameBuilder.INSTANCE.build(columnDefine.getColumnName().getName());

                mappingBuilder
                    .startObject(columnDefine.getColumnName().getName())
                    .field("type", mapping.transform(columnDefine.getType()))
                    .field("copy_to", matchCName)
                    .endObject()
                    .startObject(matchCName)
                    .field("type", "text")
                    .field("analyzer", "oap_analyzer")
                    .endObject();
            } else {
                mappingBuilder
                    .startObject(columnDefine.getColumnName().getName())
                    .field("type", mapping.transform(columnDefine.getType()))
                    .endObject();
            }
        }

        mappingBuilder
            .endObject()
            .endObject();

        logger.debug("create elasticsearch index: {}", mappingBuilder.prettyPrint());

        return mappingBuilder;
    }
}
