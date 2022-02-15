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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import org.apache.skywalking.banyandb.v1.client.BanyanDBClient;
import org.apache.skywalking.banyandb.v1.client.GroupedBanyanDBClient;
import org.apache.skywalking.banyandb.v1.client.StreamBulkWriteProcessor;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.StreamWrite;
import org.apache.skywalking.banyandb.v1.client.metadata.Group;
import org.apache.skywalking.banyandb.v1.client.metadata.IndexRule;
import org.apache.skywalking.banyandb.v1.client.metadata.Stream;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.healthcheck.DelegatedHealthChecker;
import org.apache.skywalking.oap.server.library.client.healthcheck.HealthCheckable;
import org.apache.skywalking.oap.server.library.util.HealthChecker;

import java.io.IOException;

/**
 * BanyanDBStorageClient is a simple wrapper for the underlying {@link BanyanDBClient},
 * which implement {@link Client} and {@link HealthCheckable}.
 */
public class BanyanDBStorageClient implements Client, HealthCheckable {
    private final BanyanDBClient client;
    private GroupedBanyanDBClient streamClient;
    private final DelegatedHealthChecker healthChecker = new DelegatedHealthChecker();

    public BanyanDBStorageClient(String host, int port) {
        this.client = new BanyanDBClient(host, port);
    }

    public void defineStreamGroup(Group group) {
        this.streamClient = this.client.attachGroup(group);
    }

    @Override
    public void connect() throws Exception {
        this.client.connect();
    }

    @Override
    public void shutdown() throws IOException {
        this.client.close();
    }

    public StreamQueryResponse query(StreamQuery streamQuery) {
        try {
            StreamQueryResponse response = this.streamClient.queryStreams(streamQuery);
            this.healthChecker.health();
            return response;
        } catch (Throwable t) {
            healthChecker.unHealth(t);
            throw t;
        }
    }

    public void createStream(StreamMetaInfo streamMetaInfo) {
        Stream stm = this.streamClient.define(streamMetaInfo.getStream());
        if (stm != null) {
            this.streamClient.defineIndexRules(stm, streamMetaInfo.getIndexRules().toArray(new IndexRule[]{}));
        }
    }

    public void write(StreamWrite streamWrite) {
        this.streamClient.write(streamWrite);
    }

    public StreamBulkWriteProcessor createBulkProcessor(int maxBulkSize, int flushInterval, int concurrency) {
        return this.streamClient.buildStreamWriteProcessor(maxBulkSize, flushInterval, concurrency);
    }

    @Override
    public void registerChecker(HealthChecker healthChecker) {
        this.healthChecker.register(healthChecker);
    }
}