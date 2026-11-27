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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.TextFormat;
import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Getter;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanAttachedEvent;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.config.NoneStream;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SWSpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventRecord;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.config.DownSamplingConfigService;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.apache.skywalking.oap.server.core.storage.query.proto.SpanWrapper;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.apache.skywalking.oap.server.library.it.BanyanDBTestContainer;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.bulk.AbstractBulkWriteProcessor;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBMeasureInsertRequest;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBMeasureUpdateRequest;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBMetricsDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBRecordDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBStorageDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBStreamInsertRequest;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.trace.BanyanDBTraceInsertRequest;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.apache.skywalking.oap.server.telemetry.none.NoneTelemetryProvider;
import org.apache.skywalking.oap.server.testing.util.ReflectUtil;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import zipkin2.codec.SpanBytesDecoder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A BanyanDB storage client on a test container, with OAP's own schema install and write paths for every table type
 * OAP uses — records (streams and traces), task streams, metrics (measures and index-mode measures) and management
 * properties — for the ITs that read them back through OAP's query DAOs.
 *
 * <p>Records are written in one bulk so they share a block. A criterion on a tag with no inverted index (a skipping
 * index, or none) is answered by reading blocks, and only records inside a read block can push a match past a
 * query's {@code limit + offset} — which is how such a query lost rows (apache/skywalking#14215). Records of a
 * trace model (a {@code TraceGroup}) go to BanyanDB as trace spans, the rest as stream elements.
 */
final class BanyanDBQueryITSupport implements Closeable {
    private static final Gson JSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

    private final ModuleManager moduleManager;
    @Getter
    private final BanyanDBStorageConfig config;
    @Getter
    private final BanyanDBStorageClient client;

    private BanyanDBQueryITSupport(ModuleManager moduleManager, BanyanDBStorageConfig config,
                                 BanyanDBStorageClient client) {
        this.moduleManager = moduleManager;
        this.config = config;
        this.client = client;
    }

    static GenericContainer<?> banyanDB() {
        return new GenericContainer<>(DockerImageName.parse(BanyanDBTestContainer.image()))
            .withCommand(BanyanDBTestContainer.standaloneCommand())
            .withExposedPorts(BanyanDBTestContainer.GRPC_PORT, BanyanDBTestContainer.HTTP_PORT)
            .waitingFor(Wait.forHttp(BanyanDBTestContainer.HEALTH_ENDPOINT).forPort(BanyanDBTestContainer.HTTP_PORT));
    }

    static BanyanDBQueryITSupport connect(GenericContainer<?> banyanDB) throws Exception {
        final ModuleManager moduleManager = moduleManager();
        final BanyanDBStorageProvider provider = mock(BanyanDBStorageProvider.class);
        when(provider.getModule()).thenReturn(mock(ModuleDefine.class));
        final BanyanDBStorageConfig config = new BanyanDBConfigLoader(provider).loadConfig();
        config.getGlobal().setTargets(
            banyanDB.getHost() + ":" + banyanDB.getMappedPort(BanyanDBTestContainer.GRPC_PORT));
        final BanyanDBStorageClient client = new BanyanDBStorageClient(moduleManager, config);
        client.connect();
        return new BanyanDBQueryITSupport(moduleManager, config, client);
    }

    /**
     * Installs a record's stream or trace the way OAP does at boot, from the record class's own annotations.
     */
    Model install(Class<? extends Record> recordClass, int scopeId, String name) throws StorageException {
        return install(recordClass, scopeId, new Storage(name, false, DownSampling.Second));
    }

    /**
     * Installs the minute measure of a metrics class the way OAP does at boot.
     */
    Model installMetrics(Class<? extends Metrics> metricsClass, int scopeId, String name) throws StorageException {
        return installMetrics(metricsClass, scopeId, name, DownSampling.Minute);
    }

    /**
     * Installs one downsampling of a metrics class: a measure per downsampling, as OAP installs them.
     */
    Model installMetrics(Class<? extends Metrics> metricsClass, int scopeId, String name, DownSampling downSampling)
        throws StorageException {
        return install(metricsClass, scopeId, new Storage(name, false, downSampling));
    }

    /**
     * Installs one downsampling of a metric the shipped {@code core.oal} defines, from the class OAP generates for it.
     */
    Model installOAL(String metricName, DownSampling downSampling) throws Exception {
        final Class<? extends Metrics> metricsClass = OALTestMetrics.metricsClass(metricName);
        return installMetrics(metricsClass, OALTestMetrics.scopeId(metricsClass), metricName, downSampling);
    }

    /**
     * Installs a task stream (a {@code NoneStream}) the way OAP does at boot.
     */
    Model installNoneStream(Class<? extends NoneStream> noneStreamClass, int scopeId, String name)
        throws StorageException {
        return install(noneStreamClass, scopeId, new Storage(name, false, DownSampling.Minute));
    }

    /**
     * Installs a management model, which BanyanDB stores as a property, the way OAP does at boot.
     */
    Model installManagement(Class<?> managementClass, int scopeId, String name) throws StorageException {
        return install(managementClass, scopeId, new Storage(name, false, DownSampling.None));
    }

    private Model install(Class<?> storageClass, int scopeId, Storage storage) throws StorageException {
        try (MockedStatic<DefaultScopeDefine> ignored = mockStatic(DefaultScopeDefine.class)) {
            final Model model = new StorageModels().add(
                storageClass, scopeId, storage, StorageManipulationOpt.runtimeApply());
            final BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
            installer.isExists(model, StorageManipulationOpt.runtimeApply());
            installer.createTable(model);
            return model;
        }
    }

    /**
     * Writes the records in one bulk, through OAP's own record DAO.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    void write(Model model, StorageBuilder<? extends Record> builder, List<? extends Record> records) throws Exception {
        final BanyanDBRecordDAO recordDAO = new BanyanDBRecordDAO(client, (StorageBuilder<Record>) (StorageBuilder) builder);
        final boolean trace = model.getBanyanDBModelExtension().getTraceGroup() != BanyanDB.TraceGroup.NONE;
        try (AbstractBulkWriteProcessor bulk = trace
            ? client.createTraceBulkProcessor(records.size(), 1, 1)
            : client.createStreamBulkProcessor(records.size(), 1, 1)) {
            final CompletableFuture<?>[] written = new CompletableFuture<?>[records.size()];
            for (int i = 0; i < records.size(); i++) {
                final InsertRequest insert = recordDAO.prepareBatchInsert(model, records.get(i));
                written[i] = bulk.add(trace
                    ? ((BanyanDBTraceInsertRequest) insert).getTraceWrite()
                    : ((BanyanDBStreamInsertRequest) insert).getStreamWrite());
            }
            bulk.flush();
            CompletableFuture.allOf(written).get(10, TimeUnit.SECONDS);
        }
    }

    /**
     * Writes the metrics in one bulk, through OAP's own metrics DAO.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    void writeMetrics(Model model, StorageBuilder<? extends Metrics> builder, List<? extends Metrics> metrics)
        throws Exception {
        final BanyanDBMetricsDAO metricsDAO = new BanyanDBMetricsDAO(client, (StorageBuilder<Metrics>) (StorageBuilder) builder);
        try (AbstractBulkWriteProcessor bulk = client.createMeasureBulkProcessor(metrics.size(), 1, 1)) {
            final CompletableFuture<?>[] written = new CompletableFuture<?>[metrics.size()];
            for (int i = 0; i < metrics.size(); i++) {
                written[i] = bulk.add(((BanyanDBMeasureInsertRequest) metricsDAO.prepareBatchInsert(
                    model, metrics.get(i), null)).getMeasureWrite());
            }
            bulk.flush();
            CompletableFuture.allOf(written).get(10, TimeUnit.SECONDS);
        }
    }

    /**
     * Updates the metrics in one bulk, through OAP's own metrics DAO, the way a metrics worker writes a metric it
     * read back and combined.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    void updateMetrics(Model model, StorageBuilder<? extends Metrics> builder, List<? extends Metrics> metrics)
        throws Exception {
        final BanyanDBMetricsDAO metricsDAO = new BanyanDBMetricsDAO(client, (StorageBuilder<Metrics>) (StorageBuilder) builder);
        try (AbstractBulkWriteProcessor bulk = client.createMeasureBulkProcessor(metrics.size(), 1, 1)) {
            final CompletableFuture<?>[] written = new CompletableFuture<?>[metrics.size()];
            for (int i = 0; i < metrics.size(); i++) {
                written[i] = bulk.add(((BanyanDBMeasureUpdateRequest) metricsDAO.prepareBatchUpdate(
                    model, metrics.get(i), null)).getMeasureWrite());
            }
            bulk.flush();
            CompletableFuture.allOf(written).get(10, TimeUnit.SECONDS);
        }
    }

    /**
     * Writes the task stream rows through OAP's own task-stream DAO, one by one as the task workers do.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    void writeNoneStreams(Model model, StorageBuilder<? extends NoneStream> builder, List<? extends NoneStream> rows)
        throws IOException {
        final BanyanDBNoneStreamDAO dao = new BanyanDBNoneStreamDAO(client, (StorageBuilder<NoneStream>) (StorageBuilder) builder);
        for (final NoneStream row : rows) {
            dao.insert(model, row);
        }
    }

    /**
     * OAP's model installer on this client, as the provider registers it.
     */
    BanyanDBIndexInstaller installer() {
        return new BanyanDBIndexInstaller(client, moduleManager, config);
    }

    /**
     * OAP's batch DAO on this client, with the bulk settings the provider gives it.
     */
    BanyanDBBatchDAO batchDAO() {
        return new BanyanDBBatchDAO(client, config.getGlobal().getMaxBulkSize(), config.getGlobal().getFlushInterval(),
                                    config.getGlobal().getConcurrentWriteThreads());
    }

    /**
     * The request a record worker or a TopN worker prepares for a record, with the record DAO OAP's storage DAO gives
     * it, to hand to the batch DAO.
     */
    InsertRequest prepareInsert(Model model, StorageBuilder<? extends Record> builder, Record record)
        throws IOException {
        return new BanyanDBStorageDAO(client).newRecordDao(builder).prepareBatchInsert(model, record);
    }

    /**
     * The request a metrics worker prepares for a metric it writes for the first time, with the metrics DAO OAP's
     * storage DAO gives it.
     */
    InsertRequest prepareMetricsInsert(Model model, StorageBuilder<? extends Metrics> builder, Metrics metrics,
                                       SessionCacheCallback callback) throws IOException {
        return new BanyanDBStorageDAO(client).newMetricsDao(builder).prepareBatchInsert(model, metrics, callback);
    }

    /**
     * The request a metrics worker prepares for a metric it read back and combined, with the metrics DAO OAP's storage
     * DAO gives it.
     */
    UpdateRequest prepareMetricsUpdate(Model model, StorageBuilder<? extends Metrics> builder, Metrics metrics)
        throws IOException {
        return new BanyanDBStorageDAO(client).newMetricsDao(builder).prepareBatchUpdate(model, metrics, null);
    }

    /**
     * The record the trace receiver builds from an event attached to a span of a native trace.
     */
    static SWSpanAttachedEventRecord swEventRecord(SpanAttachedEvent event) {
        final SWSpanAttachedEventRecord record = new SWSpanAttachedEventRecord();
        record.setStartTimeSecond(event.getStartTime().getSeconds());
        record.setStartTimeNanos(event.getStartTime().getNanos());
        record.setEvent(event.getEvent());
        record.setEndTimeSecond(event.getEndTime().getSeconds());
        record.setEndTimeNanos(event.getEndTime().getNanos());
        record.setTraceRefType(event.getTraceContext().getTypeValue());
        record.setRelatedTraceId(event.getTraceContext().getTraceId());
        record.setTraceSegmentId(event.getTraceContext().getTraceSegmentId());
        record.setTraceSpanId(event.getTraceContext().getSpanId());
        record.setDataBinary(event.toByteArray());
        final long timestamp = eventStartMillis(event);
        record.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
        record.setTimestamp(timestamp);
        return record;
    }

    /**
     * The record the trace receiver builds from an event attached to a span of a Zipkin trace.
     */
    static SpanAttachedEventRecord zkEventRecord(SpanAttachedEvent event) {
        final SpanAttachedEventRecord record = new SpanAttachedEventRecord();
        record.setStartTimeSecond(event.getStartTime().getSeconds());
        record.setStartTimeNanos(event.getStartTime().getNanos());
        record.setEvent(event.getEvent());
        record.setEndTimeSecond(event.getEndTime().getSeconds());
        record.setEndTimeNanos(event.getEndTime().getNanos());
        record.setTraceRefType(event.getTraceContext().getTypeValue());
        record.setRelatedTraceId(event.getTraceContext().getTraceId());
        record.setTraceSegmentId(event.getTraceContext().getTraceSegmentId());
        record.setTraceSpanId(event.getTraceContext().getSpanId());
        record.setDataBinary(event.toByteArray());
        final long timestamp = eventStartMillis(event);
        record.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
        record.setTimestamp(timestamp);
        return record;
    }

    private static long eventStartMillis(SpanAttachedEvent event) {
        return TimeUnit.SECONDS.toMillis(event.getStartTime().getSeconds())
            + TimeUnit.NANOSECONDS.toMillis(event.getStartTime().getNanos());
    }

    /**
     * Every column the storage builder stores for each entity, as text: whole entities, sorted, duplicates kept.
     */
    static <T extends StorageData> List<String> stored(StorageBuilder<T> builder, List<? extends StorageData> entities) {
        return entities.stream().map(entity -> columns(builder, entity)).sorted().collect(Collectors.toList());
    }

    /**
     * Every column the storage builder stores for an entity, as a JSON object: each value as it is, a null apart from
     * the text "null".
     */
    static <T extends StorageData> String columns(StorageBuilder<T> builder, StorageData entity) {
        return columns(columnMap(builder, entity));
    }

    /** Columns by name as a JSON object, rendered as {@link #columns(StorageBuilder, StorageData)} renders them. */
    static String columns(Map<String, Object> columns) {
        return JSON.toJson(columns);
    }

    /** Every column the storage builder stores for an entity, by column name. */
    @SuppressWarnings("unchecked")
    static <T extends StorageData> Map<String, Object> columnMap(StorageBuilder<T> builder, StorageData entity) {
        final HashMapConverter.ToStorage columns = new HashMapConverter.ToStorage();
        builder.entity2Storage((T) entity, columns);
        return new TreeMap<>(columns.obtain());
    }

    /** Values as a JSON array: each as it is, nothing trimmed, collapsed or folded, a null apart from "null". */
    static String json(Object... values) {
        return JSON.toJson(Arrays.asList(values));
    }

    /** A message whole, on one line, in text format: strings are escaped, not reflowed, so every value is kept. */
    static String text(MessageOrBuilder message) {
        return TextFormat.printer().emittingSingleLine(true).printToString(message);
    }

    /**
     * A Zipkin span whole: its JSON, which writes the debug and shared flags only when they are true, then both flags,
     * so one set to false is told apart from one not set.
     */
    static String zipkin(zipkin2.Span span) {
        return span + " debug=" + span.debug() + " shared=" + span.shared();
    }

    /**
     * A span of a trace whole: its source, the service it was stored under, and its payload decoded by its source.
     */
    static String describe(SpanWrapper span) {
        final String payload;
        try {
            switch (span.getSource()) {
                case SKYWALKING:
                    payload = text(SegmentObject.parseFrom(span.getSpan()));
                    break;
                case SKYWALKING_EVENT:
                case ZIPKIN_EVENT:
                    payload = text(SpanAttachedEvent.parseFrom(span.getSpan()));
                    break;
                case ZIPKIN:
                    payload = zipkin(SpanBytesDecoder.PROTO3.decodeOne(span.getSpan().toByteArray()));
                    break;
                default:
                    payload = Base64.getEncoder().encodeToString(span.getSpan().toByteArray());
                    break;
            }
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(e);
        }
        return json(span.getSource().name(), span.getService(), payload);
    }

    /**
     * The spans of a trace whole, sorted, duplicates kept: a trace read defines no order of its spans.
     */
    static List<String> describe(List<SpanWrapper> trace) {
        return trace.stream().map(BanyanDBQueryITSupport::describe).sorted().collect(Collectors.toList());
    }

    /**
     * A minute-step window from two minutes before {@code first} to two minutes after {@code last}.
     */
    static Duration around(long first, long last) {
        final Duration duration = new Duration();
        duration.setStep(Step.MINUTE);
        duration.setStart(DurationUtils.YYYY_MM_DD_HHMM.print(first - 120_000L));
        duration.setEnd(DurationUtils.YYYY_MM_DD_HHMM.print(last + 120_000L));
        return duration;
    }

    @Override
    public void close() throws IOException {
        client.shutdown();
    }

    private static ModuleManager moduleManager() {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final NoneTelemetryProvider telemetryProvider = mock(NoneTelemetryProvider.class);
        when(telemetryProvider.getService(MetricsCreator.class)).thenReturn(new MetricsCreatorNoop());
        final TelemetryModule telemetryModule = Mockito.spy(TelemetryModule.class);
        ReflectUtil.setInternalState(telemetryModule, "loadedProvider", telemetryProvider);
        when(moduleManager.find(TelemetryModule.NAME)).thenReturn(telemetryModule);

        final ModuleProviderHolder coreProvider = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder coreServices = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(coreProvider);
        when(coreProvider.provider()).thenReturn(coreServices);
        when(coreServices.getService(DownSamplingConfigService.class))
            .thenReturn(new DownSamplingConfigService(Arrays.asList("Hour", "Day")));
        return moduleManager;
    }
}
