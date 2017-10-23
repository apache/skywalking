/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.storage.elasticsearch.dao;

import java.util.List;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.skywalking.apm.collector.core.util.CollectionUtils;
import org.skywalking.apm.collector.storage.dao.IBatchDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class BatchEsDAO extends EsDAO implements IBatchDAO {

    private final Logger logger = LoggerFactory.getLogger(BatchEsDAO.class);

    @Override public void batchPersistence(List<?> batchCollection) {
        BulkRequestBuilder bulkRequest = getClient().prepareBulk();

        logger.debug("bulk data size: {}", batchCollection.size());
        if (CollectionUtils.isNotEmpty(batchCollection)) {
            for (int i = 0; i < batchCollection.size(); i++) {
                Object builder = batchCollection.get(i);
                if (builder instanceof IndexRequestBuilder) {
                    bulkRequest.add((IndexRequestBuilder)builder);
                }
                if (builder instanceof UpdateRequestBuilder) {
                    bulkRequest.add((UpdateRequestBuilder)builder);
                }
            }

            BulkResponse bulkResponse = bulkRequest.execute().actionGet();
            if (bulkResponse.hasFailures()) {
                logger.error(bulkResponse.buildFailureMessage());
            }
        }
    }
}
