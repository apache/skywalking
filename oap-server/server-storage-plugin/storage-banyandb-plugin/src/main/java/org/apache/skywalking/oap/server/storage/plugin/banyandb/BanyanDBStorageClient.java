package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import org.apache.skywalking.banyandb.v1.client.BanyanDBClient;
import org.apache.skywalking.banyandb.v1.client.StreamBulkWriteProcessor;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.StreamWrite;
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
    private final DelegatedHealthChecker healthChecker = new DelegatedHealthChecker();

    public BanyanDBStorageClient(String host, int port, String group) {
        this.client = new BanyanDBClient(host, port, group);
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
            StreamQueryResponse response = this.client.queryStreams(streamQuery);
            this.healthChecker.health();
            return response;
        } catch (Throwable t) {
            healthChecker.unHealth(t);
            throw t;
        }
    }

    public void write(StreamWrite streamWrite) {
        this.client.write(streamWrite);
    }

    public StreamBulkWriteProcessor createBulkProcessor(int maxBulkSize, int flushInterval, int concurrency) {
        return this.client.buildStreamWriteProcessor(maxBulkSize, flushInterval, concurrency);
    }

    @Override
    public void registerChecker(HealthChecker healthChecker) {
        this.healthChecker.register(healthChecker);
    }
}
