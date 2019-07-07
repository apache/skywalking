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
import java.util.*;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.ttl.StorageTTL;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchTimeSeriesIndex;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.joda.time.DateTime;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class HistoryDeleteEsDAO extends EsDAO implements IHistoryDeleteDAO {

    private static final Logger logger = LoggerFactory.getLogger(HistoryDeleteEsDAO.class);

    private final StorageTTL storageTTL;
    private final ModuleDefineHolder moduleDefineHolder;

    public HistoryDeleteEsDAO(ModuleDefineHolder moduleDefineHolder, ElasticSearchClient client, StorageTTL storageTTL) {
        super(client);
        this.moduleDefineHolder = moduleDefineHolder;
        this.storageTTL = storageTTL;
    }

    @Override
    public void deleteHistory(Model model, String timeBucketColumnName) throws IOException {
        ConfigService configService = moduleDefineHolder.find(CoreModule.NAME).provider().getService(ConfigService.class);

        ElasticSearchClient client = getClient();
        long timeBefore = storageTTL.calculator(model.getDownsampling()).timeBefore(new DateTime(), configService.getDataTTLConfig());

        if (model.isCapableOfTimeSeries()) {
            List<ElasticSearchTimeSeriesIndex> indexes = client.retrievalIndexByAliases(model.getName());

            List<ElasticSearchTimeSeriesIndex> prepareDeleteIndexes = new ArrayList<>();
            for (ElasticSearchTimeSeriesIndex index : indexes) {
                long timeSeries = TimeSeriesUtils.indexTimeSeries(index.getIndex());
                if (timeBefore >= timeSeries) {
                    prepareDeleteIndexes.add(index);
                }
            }

            if (indexes.size() == prepareDeleteIndexes.size()) {
                client.createIndex(TimeSeriesUtils.timeSeries(model));
            }

            for (ElasticSearchTimeSeriesIndex prepareDeleteIndex : prepareDeleteIndexes) {
                client.deleteTimeSeriesIndex(prepareDeleteIndex);
            }
        } else {
            int statusCode = client.delete(model.getName(), timeBucketColumnName, timeBefore);
            if (logger.isDebugEnabled()) {
                logger.debug("Delete history from {} index, status code {}", client.formatIndexName(model.getName()), statusCode);
            }
        }
    }
}
