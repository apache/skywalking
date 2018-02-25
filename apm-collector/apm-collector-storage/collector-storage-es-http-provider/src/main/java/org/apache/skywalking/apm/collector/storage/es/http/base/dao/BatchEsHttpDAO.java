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

package org.apache.skywalking.apm.collector.storage.es.http.base.dao;

import java.util.List;

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.core.util.CollectionUtils;
import org.apache.skywalking.apm.collector.storage.base.dao.IBatchDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.searchbox.action.BulkableAction;
import io.searchbox.core.Bulk;
import io.searchbox.core.BulkResult;

/**
 * @author peng-yongsheng
 */
public class BatchEsHttpDAO extends EsHttpDAO implements IBatchDAO {

    private final Logger logger = LoggerFactory.getLogger(BatchEsHttpDAO.class);

    public BatchEsHttpDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public void batchPersistence(List<?> batchCollection) {
        Bulk.Builder bulk = new Bulk.Builder();

        logger.debug("bulk data size: {}", batchCollection.size());
        if (CollectionUtils.isNotEmpty(batchCollection)) {
            batchCollection.forEach(builder -> {
                bulk.addAction((BulkableAction)builder);
            });
            
            BulkResult bulkResult = getClient().execute(bulk.build());
            
            if(! bulkResult.isSucceeded()){
                logger.error(bulkResult.getErrorMessage());
            }

        }
    }
}
