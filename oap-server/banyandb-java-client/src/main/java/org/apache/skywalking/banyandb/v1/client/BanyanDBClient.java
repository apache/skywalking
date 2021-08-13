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

package org.apache.skywalking.banyandb.v1.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.NameResolverRegistry;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.v1.trace.TraceServiceGrpc;

/**
 * BanyanDBClient represents a client instance interacting with BanyanDB server. This is built on the top of BanyanDB v1
 * gRPC APIs.
 */
@Slf4j
public class BanyanDBClient {
    /**
     * The hostname of BanyanDB server.
     */
    private final String host;
    /**
     * The port of BanyanDB server.
     */
    private final int port;
    /**
     * The instance name.
     */
    private final String group;
    /**
     * Options for server connection.
     */
    @Setter
    private Options options = new Options();
    /**
     * Managed gRPC connection.
     */
    private volatile ManagedChannel managedChannel;
    /**
     * gRPC client stub
     */
    private volatile TraceServiceGrpc.TraceServiceStub traceServiceStub;
    /**
     * The connection status.
     */
    private volatile boolean isConnected = false;
    /**
     * A lock to control the race condition in establishing and disconnecting network connection.
     */
    private volatile ReentrantLock connectionEstablishLock;

    /**
     * Create a BanyanDB client instance
     *
     * @param host  IP or domain name
     * @param port  Server port
     * @param group Database instance name
     */
    public BanyanDBClient(final String host, final int port, final String group) {
        this(host, port, group, new Options());
    }

    /**
     * Create a BanyanDB client instance with custom options
     *
     * @param host    IP or domain name
     * @param port    Server port
     * @param group   Database instance name
     * @param options for database connection
     */
    public BanyanDBClient(final String host,
                          final int port,
                          final String group,
                          final Options options) {
        this.host = host;
        this.port = port;
        this.group = group;
        this.options = options;
        this.connectionEstablishLock = new ReentrantLock();

        NameResolverRegistry.getDefaultRegistry().register(new DnsNameResolverProvider());
    }

    /**
     * Connect to the server.
     *
     * @throws RuntimeException if server is not reachable.
     */
    public void connect() {
        connectionEstablishLock.lock();
        try {
            if (!isConnected) {
                final ManagedChannelBuilder nettyChannelBuilder = NettyChannelBuilder.forAddress(host, port);
                nettyChannelBuilder.maxInboundMessageSize(options.getMaxInboundMessageSize());

                managedChannel = nettyChannelBuilder.build();
                traceServiceStub = TraceServiceGrpc.newStub(managedChannel);
                isConnected = true;
            }
        } finally {
            connectionEstablishLock.unlock();
        }
    }

    /**
     * Create a build process for trace write.
     *
     * @param maxBulkSize   the max bulk size for the flush operation
     * @param flushInterval if given maxBulkSize is not reached in this period, the flush would be trigger
     *                      automatically. Unit is second
     * @param concurrency   the number of concurrency would run for the flush max
     * @return trace bulk write processor
     */
    public TraceBulkWriteProcessor buildTraceWriteProcessor(int maxBulkSize, int flushInterval, int concurrency) {
        return new TraceBulkWriteProcessor(group, traceServiceStub, maxBulkSize, flushInterval, concurrency);
    }

}
