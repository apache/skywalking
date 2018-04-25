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

package org.apache.skywalking.apm.collector.storage.es.base.dao;

import java.util.List;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.BatchParameter;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.core.util.CollectionUtils;
import org.apache.skywalking.apm.collector.storage.base.dao.IBatchDAO;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class BatchEsDAO extends EsDAO implements IBatchDAO {

    private final Logger logger = LoggerFactory.getLogger(BatchEsDAO.class);

    public BatchEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @GraphComputingMetric(name = "/persistence/batchPersistence/")
    @Override public void batchPersistence(@BatchParameter List<?> batchCollection) {
        if (logger.isDebugEnabled()) {
            logger.debug("bulk data size: {}", batchCollection.size());
        }
        if (CollectionUtils.isNotEmpty(batchCollection)) {
            BulkRequestBuilder bulkRequest = getClient().prepareBulk();

            batchCollection.forEach(builder -> {
                if (builder instanceof IndexRequestBuilder) {
                    bulkRequest.add((IndexRequestBuilder)builder);
                }
                if (builder instanceof UpdateRequestBuilder) {
                    bulkRequest.add((UpdateRequestBuilder)builder);
                }
            });

            BulkResponse bulkResponse = bulkRequest.execute().actionGet();
            if (bulkResponse.hasFailures()) {
                logger.error(bulkResponse.buildFailureMessage());
                for (BulkItemResponse itemResponse : bulkResponse.getItems()) {
                    logger.error("Bulk request failure, index: {}, id: {}", itemResponse.getIndex(), itemResponse.getId());
                }
            }
        }
    }
}
