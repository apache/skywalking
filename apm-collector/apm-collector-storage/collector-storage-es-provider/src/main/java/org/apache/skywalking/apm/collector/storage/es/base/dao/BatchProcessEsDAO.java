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

import java.lang.reflect.Field;
import java.util.List;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.UnexpectedException;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.core.util.CollectionUtils;
import org.apache.skywalking.apm.collector.storage.base.dao.IBatchDAO;
import org.elasticsearch.action.bulk.*;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class BatchProcessEsDAO extends EsDAO implements IBatchDAO {

    private static final Logger logger = LoggerFactory.getLogger(BatchProcessEsDAO.class);

    private BulkProcessor bulkProcessor;
    private final int bulkActions;
    private final int bulkSize;
    private final int flushInterval;
    private final int concurrentRequests;

    public BatchProcessEsDAO(ElasticSearchClient client, int bulkActions, int bulkSize, int flushInterval,
        int concurrentRequests) {
        super(client);
        this.bulkActions = bulkActions;
        this.bulkSize = bulkSize;
        this.flushInterval = flushInterval;
        this.concurrentRequests = concurrentRequests;
    }

    @GraphComputingMetric(name = "/persistence/batchPersistence/")
    @Override public void batchPersistence(List<?> batchCollection) {
        if (bulkProcessor == null) {
            this.bulkProcessor = createBulkProcessor();
        }

        if (logger.isDebugEnabled()) {
            logger.debug("bulk data size: {}", batchCollection.size());
        }

        if (CollectionUtils.isNotEmpty(batchCollection)) {
            batchCollection.forEach(builder -> {
                if (builder instanceof IndexRequestBuilder) {
                    this.bulkProcessor.add(((IndexRequestBuilder)builder).request());
                }
                if (builder instanceof UpdateRequestBuilder) {
                    this.bulkProcessor.add(((UpdateRequestBuilder)builder).request());
                }
            });
        }
    }

    private BulkProcessor createBulkProcessor() {
        ElasticSearchClient elasticSearchClient = getClient();

        Client client;
        try {
            Field field = elasticSearchClient.getClass().getDeclaredField("client");
            field.setAccessible(true);
            client = (Client)field.get(elasticSearchClient);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new UnexpectedException(e.getMessage());
        }

        return BulkProcessor.builder(
            client,
            new BulkProcessor.Listener() {
                @Override
                public void beforeBulk(long executionId,
                    BulkRequest request) {
                }

                @Override
                public void afterBulk(long executionId,
                    BulkRequest request,
                    BulkResponse response) {
                }

                @Override
                public void afterBulk(long executionId,
                    BulkRequest request,
                    Throwable failure) {
                    logger.error("{} data bulk failed, reason: {}", request.numberOfActions(), failure);
                }
            })
            .setBulkActions(bulkActions)
            .setBulkSize(new ByteSizeValue(bulkSize, ByteSizeUnit.MB))
            .setFlushInterval(TimeValue.timeValueSeconds(flushInterval))
            .setConcurrentRequests(concurrentRequests)
            .setBackoffPolicy(BackoffPolicy.exponentialBackoff(TimeValue.timeValueMillis(100), 3))
            .build();
    }
}
