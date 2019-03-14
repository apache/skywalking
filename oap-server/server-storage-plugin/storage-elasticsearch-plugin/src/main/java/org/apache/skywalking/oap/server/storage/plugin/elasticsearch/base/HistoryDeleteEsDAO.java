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

import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.IModelGetter;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;


/**
 * @author peng-yongsheng
 */
public class HistoryDeleteEsDAO extends EsDAO implements IHistoryDeleteDAO {

    private static final Logger logger = LoggerFactory.getLogger(HistoryDeleteEsDAO.class);

    private final ModuleManager moduleManager;
    private final StorageEsInstaller storageEsInstaller;

    public HistoryDeleteEsDAO(ElasticSearchClient client, ModuleManager moduleManager, StorageEsInstaller storageEsInstaller) {
        super(client);
        this.moduleManager = moduleManager;
        this.storageEsInstaller = storageEsInstaller;
    }

    @Override
    public void deleteHistory(String modelName, String timeBucketColumnName, Long timeBucketBefore) throws IOException {
        ElasticSearchClient client = getClient();
        if (client.getCreateByDayIndexes().contains(modelName)) {

            Model currModel = null;

            DateTimeFormatter format = DateTimeFormat.forPattern("yyyyMMdd");
            DateTime currentTime = new DateTime();
            DateTime beforeTime = DateTime.parse(timeBucketBefore.toString().substring(0, 8), format);

            IModelGetter modelGetter = moduleManager.find(CoreModule.NAME).provider().getService(IModelGetter.class);
            List<Model> models = modelGetter.getModels();
            for (Model model : models) {
                if (model.getName().equals(modelName)) {
                    currModel = model;
                    break;
                }
            }
            if (currModel != null) {
                for (int i = 1; i <= 5; i++) {
                    deleteIndex(client, modelName + Const.ID_SPLIT + beforeTime.plusDays(-i).toString("yyyyMMdd"));
                }
                for (int i = 0; i <= 2; i++) {
                    createIndex(client, currModel.copy(modelName + Const.ID_SPLIT + currentTime.plusDays(i).toString("yyyyMMdd")));
                }
            }
            modelName = modelName + Const.ID_SPLIT + beforeTime.toString("yyyyMMdd");
        }
        int statusCode = client.delete(modelName, timeBucketColumnName, timeBucketBefore);
        if (logger.isDebugEnabled()) {
            logger.debug("Delete history from {} index, status code {}", client.formatIndexName(modelName), statusCode);
        }
    }

    private void deleteIndex(ElasticSearchClient client, String indexName) {
        try {
            if (client.isExistsIndex(indexName)) {
                boolean statusCode = false;
                try {
                    statusCode = client.deleteIndex(indexName);
                } catch (IOException e) {
                    logger.warn("index of {} delete failure", indexName);
                    logger.error(e.getMessage(), e);
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Delete history {} index, status code {}", indexName, statusCode);
                }
            }
        } catch (IOException e) {
            logger.warn("index of {} delete failure", indexName);
            logger.error(e.getMessage(), e);
        }
    }

    private void createIndex(ElasticSearchClient client, Model model) {
        try {
            if (!client.isExistsIndex(model.getName())) {
                try {
                    storageEsInstaller.createTable(client, model);
                } catch (StorageException e) {
                    logger.warn("index of {} create failure", model.getName());
                    logger.error(e.getMessage(), e);
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("index of {} create success", model.getName());
                }
            }
        } catch (IOException e) {
            logger.warn("index of {} delete failure", model.getName());
            logger.error(e.getMessage(), e);
        }

    }
}
