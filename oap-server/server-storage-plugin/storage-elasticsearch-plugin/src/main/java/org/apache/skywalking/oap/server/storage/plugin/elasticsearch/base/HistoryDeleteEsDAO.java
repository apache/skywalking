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
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * @author peng-yongsheng
 */
public class HistoryDeleteEsDAO extends EsDAO implements IHistoryDeleteDAO {

    private static final Logger logger = LoggerFactory.getLogger(HistoryDeleteEsDAO.class);

    private final StorageEsInstaller storageEsInstaller;

    public HistoryDeleteEsDAO(ElasticSearchClient client, StorageEsInstaller storageEsInstaller) {
        super(client);
        this.storageEsInstaller = storageEsInstaller;
    }

    @Override
    public void deleteHistory(String modelName, String timeBucketColumnName, Long timeBucketBefore) throws IOException {
        ElasticSearchClient client = getClient();
        int statusCode = client.delete(modelName, timeBucketColumnName, timeBucketBefore);
        if (logger.isDebugEnabled()) {
            logger.debug("Delete history from {} index, status code {}", client.formatIndexName(modelName), statusCode);
        }
    }

    @Override
    public void historyOther(List<Model> models, long otherDataTTL) {

        List<Model> tables = new ArrayList<>();

        ElasticSearchClient client = getClient();
        models.forEach(module -> {
            if (client.getCreateByDayIndexes().contains(module.getName())) {
                tables.add(module);
            }
        });

        DateTime currentTime = new DateTime();

        tables.forEach(module -> {
            for (int i = 1; i <= 3; i++) {
                deleteIndex(client, module.getName() + Const.ID_SPLIT + currentTime.plusDays(-i).toString("yyyyMMdd"));
            }

            for (int i = 0; i <= 2; i++) {
                createIndex(client, module.copy(module.getName() + Const.ID_SPLIT + currentTime.plusDays(i).toString("yyyyMMdd")));
            }
        });
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
