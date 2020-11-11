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

import java.util.List;

import com.google.common.collect.Lists;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchProcessEsDAO extends EsDAO implements IBatchDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchProcessEsDAO.class);

    private BulkProcessor bulkProcessor;
    private final int bulkActions;
    private final int syncBulkActions;
    private final int flushInterval;
    private final int concurrentRequests;

    public BatchProcessEsDAO(ElasticSearchClient client, int bulkActions, int syncBulkActions, int flushInterval, int concurrentRequests) {
        super(client);
        this.bulkActions = bulkActions;
        this.syncBulkActions = syncBulkActions;
        this.flushInterval = flushInterval;
        this.concurrentRequests = concurrentRequests;
    }

    @Override
    public void asynchronous(InsertRequest insertRequest) {
        if (bulkProcessor == null) {
            this.bulkProcessor = getClient().createBulkProcessor(bulkActions, flushInterval, concurrentRequests);
        }

        this.bulkProcessor.add((IndexRequest) insertRequest);
    }

    @Override
    public void synchronous(List<PrepareRequest> prepareRequests) {
        if (CollectionUtils.isNotEmpty(prepareRequests)) {
            List<List<PrepareRequest>> partitions = Lists.partition(prepareRequests, syncBulkActions);

            for (List<PrepareRequest> partition : partitions) {
                BulkRequest request = new BulkRequest();

                for (PrepareRequest prepareRequest : partition) {
                    if (prepareRequest instanceof InsertRequest) {
                        request.add((IndexRequest) prepareRequest);
                    } else {
                        request.add((UpdateRequest) prepareRequest);
                    }
                }
                getClient().synchronousBulk(request);
            }
        }
    }
}
