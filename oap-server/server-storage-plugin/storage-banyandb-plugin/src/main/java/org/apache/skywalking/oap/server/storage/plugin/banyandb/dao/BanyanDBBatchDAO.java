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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.BulkConsumePool;
import org.apache.skywalking.apm.commons.datacarrier.consumer.ConsumerPoolFactory;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.client.BanyanDBGrpcClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.client.BanyanDBInsertRequest;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2BatchDAO;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class BanyanDBBatchDAO extends H2BatchDAO {
    private final DataCarrier<PrepareRequest> dataCarrier;
    private final BanyanDBGrpcClient grpcClient;

    public BanyanDBBatchDAO(JDBCHikariCPClient h2Client, BanyanDBStorageConfig config) {
        super(h2Client);

        this.grpcClient = new BanyanDBGrpcClient(config.getHost(), config.getPort());

        String name = "BANYANDB_BATCH_INSERT";
        BulkConsumePool.Creator creator = new BulkConsumePool.Creator(name, 1, 20);
        try {
            ConsumerPoolFactory.INSTANCE.createIfAbsent(name, creator);
        } catch (Exception e) {
            throw new UnexpectedException(e.getMessage(), e);
        }

        this.dataCarrier = new DataCarrier<>(1, 10000);
        this.dataCarrier.consume(ConsumerPoolFactory.INSTANCE.get(name), new BanyanDBBatchDAO.BanyanDBBatchConsumer());
    }

    @Override
    public void insert(InsertRequest insertRequest) {
        if (insertRequest instanceof SQLExecutor) {
            super.insert(insertRequest);
            return;
        }

        this.dataCarrier.produce(insertRequest);
    }

    @Override
    public void flush(List<PrepareRequest> prepareRequests) {
        prepareRequests.stream().collect(Collectors.groupingBy(prepareRequest -> {
            if (prepareRequest instanceof SQLExecutor) {
                return PrepareRequestType.H2;
            } else {
                return PrepareRequestType.BANYANDB;
            }
        })).forEach((prepareRequestType, groupedRequests) -> {
            switch (prepareRequestType) {
                case H2:
                    BanyanDBBatchDAO.super.flush(groupedRequests);
                    break;
                case BANYANDB:
                default:
                    doSend(prepareRequests);
                    break;
            }
        });
    }

    private void doSend(List<PrepareRequest> prepareRequests) {
        for (PrepareRequest prepareRequest : prepareRequests) {
            final BanyanDBInsertRequest banyanDBInsertRequest = (BanyanDBInsertRequest) prepareRequest;
            this.grpcClient.write(banyanDBInsertRequest.getRequest());
        }
    }

    private class BanyanDBBatchConsumer implements IConsumer<PrepareRequest> {
        @Override
        public void init() {
        }

        @Override
        public void consume(List<PrepareRequest> prepareRequests) {
            BanyanDBBatchDAO.this.flush(prepareRequests);
        }

        @Override
        public void onError(List<PrepareRequest> prepareRequests, Throwable t) {
            log.error(t.getMessage(), t);
        }

        @Override
        public void onExit() {
        }
    }

    enum PrepareRequestType {
        H2, BANYANDB
    }
}
