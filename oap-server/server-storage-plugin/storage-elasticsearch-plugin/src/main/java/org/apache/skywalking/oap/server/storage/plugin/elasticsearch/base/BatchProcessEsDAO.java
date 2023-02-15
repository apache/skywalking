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
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.elasticsearch.bulk.BulkProcessor;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.client.elasticsearch.IndexRequestWrapper;
import org.apache.skywalking.oap.server.library.client.elasticsearch.UpdateRequestWrapper;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

@Slf4j
public class BatchProcessEsDAO extends EsDAO implements IBatchDAO {
    private volatile BulkProcessor bulkProcessor;
    private final int bulkActions;
    private final int flushInterval;
    private final int concurrentRequests;
    private final int batchOfBytes;

    public BatchProcessEsDAO(ElasticSearchClient client,
                             int bulkActions,
                             int flushInterval,
                             int concurrentRequests,
                             int batchOfBytes) {
        super(client);
        this.bulkActions = bulkActions;
        this.flushInterval = flushInterval;
        this.concurrentRequests = concurrentRequests;
        this.batchOfBytes = batchOfBytes;
    }

    @Override
    public void insert(InsertRequest insertRequest) {
        if (bulkProcessor == null) {
            synchronized (this) {
                if (bulkProcessor == null) {
                    this.bulkProcessor = getClient().createBulkProcessor(
                        bulkActions, flushInterval, concurrentRequests, batchOfBytes);
                }
            }
        }

        this.bulkProcessor.add(((IndexRequestWrapper) insertRequest).getRequest());
    }

    @Override
    public CompletableFuture<Void> flush(List<PrepareRequest> prepareRequests) {
        if (bulkProcessor == null) {
            synchronized (this) {
                if (bulkProcessor == null) {
                    this.bulkProcessor = getClient().createBulkProcessor(
                        bulkActions, flushInterval, concurrentRequests, batchOfBytes);
                }
            }
        }

        if (CollectionUtils.isNotEmpty(prepareRequests)) {
            return CompletableFuture.allOf(prepareRequests.stream().map(prepareRequest -> {
                if (prepareRequest instanceof InsertRequest) {
                    return bulkProcessor.add(((IndexRequestWrapper) prepareRequest).getRequest())
                        .whenComplete((v, throwable) -> {
                            if (throwable == null) {
                                // Insert completed
                                ((IndexRequestWrapper) prepareRequest).onInsertCompleted();
                            }
                        });
                } else {
                    return bulkProcessor.add(((UpdateRequestWrapper) prepareRequest).getRequest())
                        .whenComplete((v, throwable) -> {
                            if (throwable != null) {
                                // Update failure
                                ((UpdateRequestWrapper) prepareRequest).onUpdateFailure();
                            }
                        });
                }
            }).toArray(CompletableFuture[]::new));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void endOfFlush() {
        // Flush forcibly due to this kind of metrics has been pushed into the bulk processor.
        if (bulkProcessor != null) {
            bulkProcessor.flush();
        }
    }
}
