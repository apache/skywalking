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

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.banyandb.property.v1.BanyandbProperty;
import org.apache.skywalking.banyandb.property.v1.BanyandbProperty.ApplyRequest.Strategy;
import org.apache.skywalking.banyandb.property.v1.BanyandbProperty.DeleteResponse;
import org.apache.skywalking.banyandb.property.v1.BanyandbProperty.Property;
import org.apache.skywalking.banyandb.stream.v1.BanyandbStream;
import org.apache.skywalking.library.banyandb.v1.client.BanyanDBClient;
import org.apache.skywalking.library.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.library.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.library.banyandb.v1.client.MeasureWrite;
import org.apache.skywalking.library.banyandb.v1.client.Options;
import org.apache.skywalking.library.banyandb.v1.client.PropertyStore;
import org.apache.skywalking.library.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.library.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.library.banyandb.v1.client.StreamWrite;
import org.apache.skywalking.library.banyandb.v1.client.TopNQuery;
import org.apache.skywalking.library.banyandb.v1.client.TopNQueryResponse;
import org.apache.skywalking.library.banyandb.v1.client.TraceQuery;
import org.apache.skywalking.library.banyandb.v1.client.TraceQueryResponse;
import org.apache.skywalking.library.banyandb.v1.client.TraceWrite;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.InternalException;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.InvalidArgumentException;
import org.apache.skywalking.library.banyandb.v1.client.util.StatusUtil;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.healthcheck.DelegatedHealthChecker;
import org.apache.skywalking.oap.server.library.client.healthcheck.HealthCheckable;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.HealthChecker;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.bulk.MeasureBulkWriteProcessor;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.bulk.StreamBulkWriteProcessor;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.bulk.TraceBulkWriteProcessor;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * BanyanDBStorageClient is a simple wrapper for the underlying {@link BanyanDBClient},
 * which implement {@link Client} and {@link HealthCheckable}.
 */
@Slf4j
public class BanyanDBStorageClient implements Client, HealthCheckable {
    private final String[] compatibleServerApiVersions;
    final BanyanDBClient client;
    private final DelegatedHealthChecker healthChecker = new DelegatedHealthChecker();
    private final int flushTimeout;
    private final ModuleManager moduleManager;
    private final Options options;
    private BanyandbDatabase database;
    private HistogramMetrics propertySingleWriteHistogram;
    private HistogramMetrics propertySingleDeleteHistogram;
    private HistogramMetrics streamSingleWriteHistogram;
    private HistogramMetrics measureWriteHistogram;
    private HistogramMetrics streamWriteHistogram;
    private HistogramMetrics traceWriteHistogram;

    public BanyanDBStorageClient(ModuleManager moduleManager, BanyanDBStorageConfig config) {
        Options options = new Options();
        options.setSslTrustCAPath(config.getGlobal().getSslTrustCAPath());
        String username = config.getGlobal().getUser();
        String password = config.getGlobal().getPassword();
        if (StringUtil.isNotBlank(username)) {
            if (StringUtil.isBlank(password)) {
                throw new IllegalArgumentException("User is set, but password is not set.");
            }
            options.setUsername(username);
            options.setPassword(password);
        } else if (StringUtil.isNotBlank(password)) {
            throw new IllegalArgumentException("Password is set, but user is not set.");
        }
        this.client = new BanyanDBClient(config.getGlobal().getTargets(), options);
        this.flushTimeout = config.getGlobal().getFlushTimeout();
        this.options = options;
        this.moduleManager = moduleManager;
        this.compatibleServerApiVersions = config.getGlobal().getCompatibleServerApiVersions();
    }

    @Override
    public void connect() throws Exception {
        initTelemetry();
        this.client.connect();
        BanyandbCommon.APIVersion apiVersion;
        try {
            apiVersion = this.client.getAPIVersion();
        } catch (BanyanDBException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof StatusRuntimeException) {
                final Status status = ((StatusRuntimeException) cause).getStatus();
                if (Status.Code.UNIMPLEMENTED.equals(status.getCode())) {
                    log.error("fail to get BanyanDB API version, server version < 0.8 is not supported.");
                }
            }
            throw e;
        }
        final boolean isCompatible = Arrays.stream(compatibleServerApiVersions)
                                           .anyMatch(v -> v.equals(apiVersion.getVersion()));
        final String revision = apiVersion.getRevision();
        log.info("BanyanDB server API version: {}, revision: {}", apiVersion.getVersion(), revision);
        if (!isCompatible) {
            throw new IllegalStateException(
                "Incompatible BanyanDB server API version: " + apiVersion.getVersion() + ". But accepted versions: "
                    + String.join(", ", compatibleServerApiVersions));
        }

    }

    @Override
    public void shutdown() throws IOException {
        this.client.close();
    }

    public List<Property> listProperties(String name) throws IOException {
        try {
            MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findManagementMetadata(name);
            BanyandbProperty.QueryResponse resp
                = this.client.query(BanyandbProperty.QueryRequest.newBuilder()
                                                                 .addGroups(schema.getMetadata().getGroup())
                                                                 .setName(name)
                                                                 .setLimit(Integer.MAX_VALUE)
                                                                 .build());
            this.healthChecker.health();
            return resp.getPropertiesList();
        } catch (BanyanDBException ex) {
            if (ex.getStatus().equals(Status.Code.NOT_FOUND)) {
                this.healthChecker.health();
                return Collections.emptyList();
            }

            healthChecker.unHealth(ex);
            throw new IOException("fail to list properties", ex);
        }
    }

    public Property queryProperty(String name, String id) throws IOException {
        try {
            MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findManagementMetadata(name);
            BanyandbProperty.QueryResponse resp = this.client.query(BanyandbProperty.QueryRequest.newBuilder()
                                                                                                 .addGroups(schema.getMetadata().getGroup())
                                                                                                 .setName(name)
                                                                                                 .addIds(id)
                                                                                                 .build());
            this.healthChecker.health();
            if (resp.getPropertiesCount() == 0) {
                return null;
            }
            return resp.getProperties(0);
        } catch (BanyanDBException ex) {
            if (ex.getStatus().equals(Status.Code.NOT_FOUND)) {
                this.healthChecker.health();
                return null;
            }

            healthChecker.unHealth(ex);
            throw new IOException("fail to query property", ex);
        }
    }

    public DeleteResponse deleteProperty(String name, String id) throws IOException {
        try (HistogramMetrics.Timer timer = propertySingleDeleteHistogram.createTimer()) {
            MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findManagementMetadata(name);
            PropertyStore store = new PropertyStore(checkNotNull(client.getChannel()));
            DeleteResponse result = store.delete(schema.getMetadata().getGroup(), name, id);
            this.healthChecker.health();
            return result;
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw new IOException("fail to delete property", ex);
        }
    }

    public StreamQueryResponse query(StreamQuery q) throws IOException {
        try {
            StreamQueryResponse response = this.client.query(q);
            this.healthChecker.health();
            return response;
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw new IOException("fail to query stream", ex);
        }
    }

    public MeasureQueryResponse query(MeasureQuery q) throws IOException {
        try {
            MeasureQueryResponse response = this.client.query(q);
            this.healthChecker.health();
            return response;
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw new IOException("fail to query measure", ex);
        }
    }

    public TraceQueryResponse query(TraceQuery q) throws IOException {
        try {
            TraceQueryResponse response = this.client.query(q);
            this.healthChecker.health();
            return response;
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw new IOException("fail to query trace", ex);
        }
    }

    public TopNQueryResponse query(TopNQuery q) throws IOException {
        try {
            TopNQueryResponse response = this.client.query(q);
            this.healthChecker.health();
            return response;
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw new IOException("fail to query topn", ex);
        }
    }

    public BanyandbProperty.QueryResponse query(BanyandbProperty.QueryRequest request) throws IOException {
        try {
            BanyandbProperty.QueryResponse response = this.client.query(request);
            this.healthChecker.health();
            return response;
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw new IOException("fail to query property", ex);
        }
    }

    /**
     * Apply(Create or update) the property with {@link BanyandbProperty.ApplyRequest.Strategy#STRATEGY_MERGE}
     *
     * @param property the property to be stored in the BanyanBD
     */
    public BanyandbProperty.ApplyResponse apply(Property property) throws IOException {
        try (HistogramMetrics.Timer timer = propertySingleWriteHistogram.createTimer()) {
            PropertyStore store = new PropertyStore(checkNotNull(client.getChannel()));
            BanyandbProperty.ApplyResponse response = store.apply(property);
            this.healthChecker.health();
            return response;
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw new IOException("fail to create property", ex);
        }
    }

    /**
     * Apply(Create or update) the property
     *
     * @param property the property to be stored in the BanyanBD
     * @param strategy dedicates how to apply the property
     */
    public BanyandbProperty.ApplyResponse apply(Property property, Strategy strategy) throws IOException {
        try (HistogramMetrics.Timer timer = propertySingleWriteHistogram.createTimer()) {
            PropertyStore store = new PropertyStore(checkNotNull(client.getChannel()));
            BanyandbProperty.ApplyResponse response = store.apply(property, strategy);
            this.healthChecker.health();
            return response;
        } catch (BanyanDBException ex) {
            healthChecker.unHealth(ex);
            throw new IOException("fail to create property", ex);
        }
    }

    public StreamWrite createStreamWrite(String group, String name, String elementId) throws IOException {
        try {
            return this.client.createStreamWrite(group, name, elementId);
        } catch (BanyanDBException e) {
            throw new IOException("fail to create stream write", e);
        }
    }

    public MeasureWrite createMeasureWrite(String group, String name, long timestamp) throws IOException {
        try {
            return this.client.createMeasureWrite(group, name, timestamp);
        } catch (BanyanDBException e) {
            throw new IOException("fail to create measure write", e);
        }
    }

    public TraceWrite createTraceWrite(String group, String name) throws IOException {
        try {
            return this.client.createTraceWrite(group, name);
        } catch (BanyanDBException e) {
            throw new IOException("fail to create trace write", e);
        }
    }

    /**
     * Perform a single write with given entity.
     *
     * @param streamWrite the entity to be written
     * @return a future of write result
     */
    public CompletableFuture<Void> write(StreamWrite streamWrite) {
        checkState(client.getStreamServiceStub() != null, "stream service is null");
        HistogramMetrics.Timer timer = streamSingleWriteHistogram.createTimer();
        CompletableFuture<Void> future = new CompletableFuture<>();
        final StreamObserver<BanyandbStream.WriteRequest> writeRequestStreamObserver
            = client.getStreamServiceStub()
                    .withDeadlineAfter(options.getDeadline(), TimeUnit.SECONDS)
                    .write(
                        new StreamObserver<BanyandbStream.WriteResponse>() {
                            private BanyanDBException responseException;

                            @Override
                            public void onNext(BanyandbStream.WriteResponse writeResponse) {
                                BanyandbModel.Status status = StatusUtil.convertStringToStatus(
                                    writeResponse.getStatus());
                                switch (status) {
                                    case STATUS_SUCCEED:
                                        break;
                                    case STATUS_INVALID_TIMESTAMP:
                                        responseException = new InvalidArgumentException(
                                            "Invalid timestamp: " + streamWrite.getTimestamp(), null,
                                            Status.Code.INVALID_ARGUMENT, false
                                        );
                                        break;
                                    case STATUS_NOT_FOUND:
                                        responseException = new InvalidArgumentException(
                                            "Invalid metadata: " + streamWrite.getEntityMetadata(), null,
                                            Status.Code.INVALID_ARGUMENT, false
                                        );
                                        break;
                                    case STATUS_EXPIRED_SCHEMA:
                                        BanyandbCommon.Metadata metadata = writeResponse.getMetadata();
                                        log.error(
                                            "The schema {}.{} is expired",
                                            metadata.getGroup(), metadata.getName()
                                        );
                                        responseException = new InvalidArgumentException(
                                            "Expired revision: " + metadata.getModRevision(), null,
                                            Status.Code.INVALID_ARGUMENT, true
                                        );
                                        break;
                                    default:
                                        responseException = new InternalException(
                                            String.format(
                                                "Internal error (%s) occurs in server", writeResponse.getStatus()),
                                            null, Status.Code.INTERNAL, true
                                        );
                                        break;
                                }
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                timer.close();
                                log.error("Error occurs in flushing streams.", throwable);
                                future.completeExceptionally(throwable);
                            }

                            @Override
                            public void onCompleted() {
                                timer.close();
                                if (responseException == null) {
                                    future.complete(null);
                                } else {
                                    future.completeExceptionally(responseException);
                                }
                            }
                        });
        try {
            writeRequestStreamObserver.onNext(streamWrite.build());
        } finally {
            writeRequestStreamObserver.onCompleted();
        }
        return future;
    }

    /**
     * Create a build process for stream write.
     *
     * @param maxBulkSize   the max bulk size for the flush operation
     * @param flushInterval if given maxBulkSize is not reached in this period, the flush would be trigger
     *                      automatically. Unit is second
     * @param concurrency   the number of concurrency would run for the flush max
     * @return stream bulk write processor
     */
    public StreamBulkWriteProcessor createStreamBulkProcessor(int maxBulkSize, int flushInterval, int concurrency) {
        checkState(client.getStreamServiceStub() != null, "stream service is null");
        return new StreamBulkWriteProcessor(client, maxBulkSize, flushInterval, concurrency, flushTimeout, streamWriteHistogram, options);
    }

    /**
     * Create a build process for measure write.
     *
     * @param maxBulkSize   the max bulk size for the flush operation
     * @param flushInterval if given maxBulkSize is not reached in this period, the flush would be trigger
     *                      automatically. Unit is second
     * @param concurrency   the number of concurrency would run for the flush max
     * @return stream bulk write processor
     */
    public MeasureBulkWriteProcessor createMeasureBulkProcessor(int maxBulkSize, int flushInterval, int concurrency) {
        checkState(client.getMeasureServiceStub() != null, "measure service is null");
        return new MeasureBulkWriteProcessor(client, maxBulkSize, flushInterval, concurrency, flushTimeout, measureWriteHistogram, options);
    }

    /**
     * Build a trace bulk write processor.
     *
     * @param maxBulkSize   the max size of each flush. The actual size is determined by the length of byte array.
     * @param flushInterval if given maxBulkSize is not reached in this period, the flush would be trigger
     *                      automatically. Unit is second.
     * @param concurrency   the number of concurrency would run for the flush max.
     * @return trace bulk write processor
     */
    public TraceBulkWriteProcessor createTraceBulkProcessor(int maxBulkSize, int flushInterval, int concurrency) {
        return new TraceBulkWriteProcessor(client, maxBulkSize, flushInterval, concurrency, flushTimeout, traceWriteHistogram, options);
    }

    @Override
    public void registerChecker(HealthChecker healthChecker) {
        this.healthChecker.register(healthChecker);
    }

    private void initTelemetry() {
        MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                     .provider()
                                                     .getService(MetricsCreator.class);
        if (propertySingleWriteHistogram == null) {
            propertySingleWriteHistogram = metricsCreator.createHistogramMetric(
                "banyandb_write_latency",
                "BanyanDB write/update/delete latency in seconds, bulk_write include write/update",
                new MetricsTag.Keys("catalog", "operation"),
                new MetricsTag.Values("property", "single_write"),
                0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0
            );
        }
        if (propertySingleDeleteHistogram == null) {
            propertySingleDeleteHistogram = metricsCreator.createHistogramMetric(
                "banyandb_write_latency",
                "BanyanDB write/update/delete latency in seconds, bulk_write include write/update",
                new MetricsTag.Keys("catalog", "operation"),
                new MetricsTag.Values("property", "single_delete"),
                0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0
            );
        }
        if (streamSingleWriteHistogram == null) {
            streamSingleWriteHistogram = metricsCreator.createHistogramMetric(
                "banyandb_write_latency",
                "BanyanDB write/update/delete latency in seconds, bulk_write include write/update",
                new MetricsTag.Keys("catalog", "operation"),
                new MetricsTag.Values("stream", "single_write"),
                0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0
            );
        }
        if (measureWriteHistogram == null) {
            measureWriteHistogram = metricsCreator.createHistogramMetric(
                "banyandb_write_latency",
                "BanyanDB write/update/delete latency in seconds, bulk_write include write/update",
                new MetricsTag.Keys("catalog", "operation"),
                new MetricsTag.Values("measure", "bulk_write"),
                0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0
            );
        }
        if (streamWriteHistogram == null) {
            streamWriteHistogram = metricsCreator.createHistogramMetric(
                "banyandb_write_latency",
                "BanyanDB write/update/delete latency in seconds, bulk_write include write/update",
                new MetricsTag.Keys("catalog", "operation"),
                new MetricsTag.Values("stream", "bulk_write"),
                0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0
            );
        }
        if (traceWriteHistogram == null) {
            traceWriteHistogram = metricsCreator.createHistogramMetric(
                "banyandb_write_latency",
                "BanyanDB write/update/delete latency in seconds, bulk_write include write/update",
                new MetricsTag.Keys("catalog", "operation"),
                new MetricsTag.Values("trace", "bulk_write"),
                0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0
            );
        }
    }
}
