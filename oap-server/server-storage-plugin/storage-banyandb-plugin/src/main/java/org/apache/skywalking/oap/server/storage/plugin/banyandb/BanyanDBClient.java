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

import com.google.common.annotations.VisibleForTesting;
import io.grpc.ManagedChannel;
import io.netty.handler.ssl.SslContext;
import org.apache.skywalking.banyandb.client.BanyanDBService;
import org.apache.skywalking.banyandb.client.impl.BanyanDBGrpcClient;
import org.apache.skywalking.banyandb.client.request.TraceFetchRequest;
import org.apache.skywalking.banyandb.client.request.TraceSearchRequest;
import org.apache.skywalking.banyandb.client.request.TraceWriteRequest;
import org.apache.skywalking.banyandb.client.response.BanyanDBQueryResponse;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.healthcheck.DelegatedHealthChecker;
import org.apache.skywalking.oap.server.library.client.healthcheck.HealthCheckable;
import org.apache.skywalking.oap.server.library.util.HealthChecker;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BanyanDBClient implements Client, BanyanDBService, HealthCheckable {
    private ManagedChannel channel;
    private BanyanDBService delegation;
    private final DelegatedHealthChecker healthChecker = new DelegatedHealthChecker();

    private String host;
    private int port;
    private SslContext sslContext;

    @VisibleForTesting
    public BanyanDBClient(ManagedChannel channel) {
        this.channel = channel;
    }

    public BanyanDBClient(String host, int port) {
        this(host, port, null);
    }

    public BanyanDBClient(String host, int port, final SslContext sslContext) {
        this.host = host;
        this.port = port;
        this.sslContext = sslContext;
    }

    @Override
    public void connect() throws Exception {
        this.delegation = new BanyanDBGrpcClient(this.host, this.port, this.sslContext, BanyanDBSchema.GROUP, BanyanDBSchema.NAME);
    }

    @Override
    public void shutdown() throws IOException {
        try {
            this.channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            this.channel.shutdownNow();
        }
    }

    @Override
    public BanyanDBQueryResponse queryBasicTraces(TraceSearchRequest request) {
        try {
            BanyanDBQueryResponse resp = delegation.queryBasicTraces(request);
            this.healthChecker.health();
            return resp;
        } catch (Throwable t) {
            this.healthChecker.unHealth(t);
            throw t;
        }
    }

    @Override
    public BanyanDBQueryResponse queryByTraceId(TraceFetchRequest traceFetchRequest) {
        try {
            BanyanDBQueryResponse resp = this.delegation.queryByTraceId(traceFetchRequest);
            this.healthChecker.health();
            return resp;
        } catch (Throwable t) {
            this.healthChecker.unHealth(t);
            throw t;
        }
    }

    @Override
    public void writeEntity(List<TraceWriteRequest> data) {
        try {
            delegation.writeEntity(data);
            this.healthChecker.health();
        } catch (Throwable t) {
            this.healthChecker.unHealth(t);
            throw t;
        }
    }

    @Override
    public void registerChecker(HealthChecker healthChecker) {
        this.healthChecker.register(healthChecker);
    }
}
