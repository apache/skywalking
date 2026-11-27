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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.library.banyandb.v1.client.DataPoint;
import org.apache.skywalking.library.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.library.banyandb.v1.client.MeasureWrite;
import org.apache.skywalking.library.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.library.banyandb.v1.client.Value;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.config.DownSamplingConfigService;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.it.ITVersions;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.bulk.MeasureBulkWriteProcessor;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.apache.skywalking.oap.server.telemetry.none.NoneTelemetryProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.apache.skywalking.oap.server.testing.util.ReflectUtil;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@Slf4j
@Testcontainers
public class BanyanDBIT {
    private static final String REGISTRY = "ghcr.io";
    private static final String IMAGE_NAME = "apache/skywalking-banyandb";
    private static final String TAG = ITVersions.get("SW_BANYANDB_COMMIT");

    private static final String IMAGE = REGISTRY + "/" + IMAGE_NAME + ":" + TAG;
    private static MockedStatic<DefaultScopeDefine> DEFAULT_SCOPE_DEFINE_MOCKED_STATIC;
    protected static final int GRPC_PORT = 17912;
    protected static final int HTTP_PORT = 17913;

    @Container
    public GenericContainer<?> banyanDB = new GenericContainer<>(
        DockerImageName.parse(IMAGE))
        .withCommand("standalone", "--stream-root-path", "/tmp/banyandb-stream-data",
                     "--measure-root-path", "/tmp/banyand-measure-data"
        )
        .withExposedPorts(GRPC_PORT, HTTP_PORT)
        .waitingFor(Wait.forHttp("/api/healthz").forPort(HTTP_PORT));

    private BanyanDBStorageClient client;
    private BanyanDBStorageConfig config;

    protected void setUpConnection() throws Exception {
        ModuleManager moduleManager = mock(ModuleManager.class);
        ModuleDefine storageModule = mock(ModuleDefine.class);
        BanyanDBStorageProvider provider = mock(BanyanDBStorageProvider.class);
        Mockito.when(provider.getModule()).thenReturn(storageModule);

        NoneTelemetryProvider telemetryProvider = mock(NoneTelemetryProvider.class);
        Mockito.when(telemetryProvider.getService(MetricsCreator.class))
               .thenReturn(new MetricsCreatorNoop());
        TelemetryModule telemetryModule = Mockito.spy(TelemetryModule.class);
        ReflectUtil.setInternalState(telemetryModule, "loadedProvider", telemetryProvider);
        Mockito.when(moduleManager.find(TelemetryModule.NAME)).thenReturn(telemetryModule);
        log.info("create BanyanDB client and try to connect");
        config = new BanyanDBConfigLoader(provider).loadConfig();
        config.getGlobal().setTargets(banyanDB.getHost() + ":" + banyanDB.getMappedPort(GRPC_PORT));
        client = new BanyanDBStorageClient(moduleManager, config);
        client.connect();
    }

    private MeasureBulkWriteProcessor processor;

    @BeforeEach
    public void setUp() throws Exception {
        DEFAULT_SCOPE_DEFINE_MOCKED_STATIC = mockStatic(DefaultScopeDefine.class);
        DEFAULT_SCOPE_DEFINE_MOCKED_STATIC.when(() -> DefaultScopeDefine.nameOf(1)).thenReturn("any");
        setUpConnection();
        processor = client.createMeasureBulkProcessor(1000, 1, 1);
    }

    @AfterEach
    public void tearDown() {
        if (DEFAULT_SCOPE_DEFINE_MOCKED_STATIC != null) {
            DEFAULT_SCOPE_DEFINE_MOCKED_STATIC.close();
            DEFAULT_SCOPE_DEFINE_MOCKED_STATIC = null;
        }
    }

    @Test
    public void testInstall() throws Exception {
        DownSamplingConfigService downSamplingConfigService = new DownSamplingConfigService(Arrays.asList("minute"));
        ModuleManager moduleManager = mock(ModuleManager.class);
        ModuleProviderHolder moduleProviderHolder = mock(ModuleProviderHolder.class);
        ModuleServiceHolder moduleServiceHolder = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(moduleProviderHolder);
        when(moduleProviderHolder.provider()).thenReturn(moduleServiceHolder);
        when(moduleServiceHolder.getService(DownSamplingConfigService.class)).thenReturn(downSamplingConfigService);

        StorageModels models = new StorageModels();
        Model model = models.add(TestMetric.class, DefaultScopeDefine.SERVICE,
                                 new Storage("testMetric", true, DownSampling.Minute),
                                 StorageManipulationOpt.runtimeApply()
        );
        BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        installer.isExists(model, StorageManipulationOpt.runtimeApply());
        //test Group install
        String groupName = MetadataRegistry.convertGroupName(
            config.getGlobal().getNamespace(),
            BanyanDB.MeasureGroup.METRICS_MINUTE.getName()
        );
        BanyandbCommon.Group group = client.client.findGroup(groupName);
        assertEquals(BanyandbCommon.Catalog.CATALOG_MEASURE, group.getCatalog());
        assertEquals(config.getMetricsMin().getSegmentInterval(), group.getResourceOpts().getSegmentInterval().getNum());
        assertEquals(config.getMetricsMin().getShardNum(), group.getResourceOpts().getShardNum());
        assertEquals(BanyandbCommon.IntervalRule.Unit.UNIT_DAY, group.getResourceOpts().getSegmentInterval().getUnit());
        assertEquals(config.getMetricsMin().getTtl(), group.getResourceOpts().getTtl().getNum());
        assertEquals(BanyandbCommon.IntervalRule.Unit.UNIT_DAY, group.getResourceOpts().getTtl().getUnit());

        installer.createTable(model);
        //test Measure install
        BanyandbDatabase.Measure measure = client.client.findMeasure(groupName, "testMetric_minute");
        assertEquals("entity=[service_id] shardingKey=[service_id] interval=1m indexMode=false"
                + " tags=[storage-only/service_id:TAG_TYPE_STRING, searchable/tag:TAG_TYPE_STRING]"
                + " fields=[value:FIELD_TYPE_INT:ENCODING_METHOD_GORILLA:COMPRESSION_METHOD_ZSTD]",
            describe(measure));
        //test TopNAggregation install
        BanyandbDatabase.TopNAggregation topNAggregation = client.client.findTopNAggregation(
            groupName, "testMetric-service");
        assertEquals("value", topNAggregation.getFieldName());
        assertEquals("service_id", topNAggregation.getGroupByTagNames(0));
        assertEquals(BanyandbModel.Sort.SORT_DESC, topNAggregation.getFieldValueSort());
        assertEquals(10, topNAggregation.getLruSize());
        assertEquals(1000, topNAggregation.getCountersNumber());
        //test IndexRule install
        BanyandbDatabase.IndexRule indexRuleTag = client.client.findIndexRule(groupName, "tag");
        assertEquals("tags=[tag] type=TYPE_INVERTED analyzer=url noSort=true", describe(indexRuleTag));
        //test IndexRuleBinding install
        BanyandbDatabase.IndexRuleBinding indexRuleBinding = client.client.findIndexRuleBinding(
            groupName, "testMetric_minute");
        assertEquals("subject=CATALOG_MEASURE/testMetric_minute rules=[tag]", describe(indexRuleBinding));
        //test data query
        Instant now = Instant.now();
        Instant begin = now.minus(15, ChronoUnit.MINUTES);
        MeasureWrite measureWrite = client.createMeasureWrite(groupName, "testMetric_minute", now.toEpochMilli());
        measureWrite.tag("storage-only", "service_id", TagAndValue.stringTagValue("service1"))
                    .tag("searchable", "tag", TagAndValue.stringTagValue("tag1"))
                    .field("value", TagAndValue.longFieldValue(100));
        CompletableFuture<Void> f = processor.add(measureWrite);
        f.exceptionally(exp -> {
            Assertions.fail(exp.getMessage());
            return null;
        });
        f.get(10, TimeUnit.SECONDS);

        final String ql = "SELECT \"service_id\", \"tag\", \"value\" FROM MEASURE testMetric_minute IN "
            + groupName + " TIME BETWEEN ? AND ?";
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            MeasureQueryResponse resp = client.queryMeasure(ql,
                Value.timestampTagValue(begin.toEpochMilli()),
                Value.timestampTagValue(now.plus(1, ChronoUnit.MINUTES).toEpochMilli()));
            assertNotNull(resp);
            assertEquals(1, resp.getDataPoints().size());
            assertEquals("service1", resp.getDataPoints().get(0).getTagValue("service_id"));
            assertEquals("tag1", resp.getDataPoints().get(0).getTagValue("tag"));
            assertEquals(100, (Long) resp.getDataPoints().get(0).getFieldValue("value"));
        });

        // StorageModels.add now dedupes by (name, downsampling); evict the original
        // registration so the UpdateTestMetric registration takes effect.
        models.remove(TestMetric.class, StorageManipulationOpt.runtimeApply());
        Model updatedModel = models.add(UpdateTestMetric.class, DefaultScopeDefine.SERVICE,
                                        new Storage("testMetric", true, DownSampling.Minute),
                                        StorageManipulationOpt.runtimeApply()
        );
        config.getMetricsMin().setShardNum(config.getMetricsMin().getShardNum() + 1);
        config.getMetricsMin().setSegmentInterval(config.getMetricsMin().getSegmentInterval() + 2);
        config.getMetricsMin().setTtl(config.getMetricsMin().getTtl() + 3);
        BanyanDBIndexInstaller newInstaller = new BanyanDBIndexInstaller(client, moduleManager, config);
        newInstaller.isExists(updatedModel, StorageManipulationOpt.runtimeApply());
        //test Group update — assert the live group now reflects the values we set on config,
        //rather than hard-coding the post-mutation numbers (which would couple the test to the
        //defaults shipped in bydb.yml).
        BanyandbCommon.Group updatedGroup = client.client.findGroup(groupName);
        assertEquals(config.getMetricsMin().getShardNum(), updatedGroup.getResourceOpts().getShardNum());
        assertEquals(config.getMetricsMin().getSegmentInterval(), updatedGroup.getResourceOpts().getSegmentInterval().getNum());
        assertEquals(config.getMetricsMin().getTtl(), updatedGroup.getResourceOpts().getTtl().getNum());
        //test Measure update
        BanyandbDatabase.Measure updatedMeasure = client.client.findMeasure(groupName, "testMetric_minute");
        assertEquals("entity=[service_id] shardingKey=[] interval=1m indexMode=false"
                + " tags=[storage-only/service_id:TAG_TYPE_STRING, searchable/tag:TAG_TYPE_STRING,"
                + " searchable/new_tag:TAG_TYPE_STRING]"
                + " fields=[value:FIELD_TYPE_INT:ENCODING_METHOD_GORILLA:COMPRESSION_METHOD_ZSTD,"
                + " new_value:FIELD_TYPE_INT:ENCODING_METHOD_GORILLA:COMPRESSION_METHOD_ZSTD]",
            describe(updatedMeasure));
        //test IndexRule update
        BanyandbDatabase.IndexRule updatedIndexRuleTag = client.client.findIndexRule(groupName, "tag");
        assertEquals("tags=[tag] type=TYPE_INVERTED analyzer= noSort=false", describe(updatedIndexRuleTag));
        BanyandbDatabase.IndexRule updatedIndexRuleNewTag = client.client.findIndexRule(groupName, "new_tag");
        assertEquals("tags=[new_tag] type=TYPE_INVERTED analyzer= noSort=true", describe(updatedIndexRuleNewTag));
        //test IndexRuleBinding update
        BanyandbDatabase.IndexRuleBinding updatedIndexRuleBinding = client.client.findIndexRuleBinding(
            groupName, "testMetric_minute");
        assertEquals("subject=CATALOG_MEASURE/testMetric_minute rules=[tag, new_tag]", describe(updatedIndexRuleBinding));
        //test data
        MeasureWrite updatedMeasureWrite = client.createMeasureWrite(groupName, "testMetric_minute", now.plus(10, ChronoUnit.MINUTES).toEpochMilli());
        updatedMeasureWrite.tag("storage-only", "service_id", TagAndValue.stringTagValue("service2"))
                           .tag("searchable", "tag", TagAndValue.stringTagValue("tag1"))
                           .tag("searchable", "new_tag", TagAndValue.stringTagValue("new_tag1"))
                           .field("value", TagAndValue.longFieldValue(101))
                           .field("new_value", TagAndValue.longFieldValue(1000));
        CompletableFuture<Void> cf = processor.add(updatedMeasureWrite);
        cf.exceptionally(exp -> {
            Assertions.fail(exp.getMessage());
            return null;
        });
        cf.get(10, TimeUnit.SECONDS);
        final String updatedQl = "SELECT \"service_id\", \"tag\", \"new_tag\", \"value\", \"new_value\" "
            + "FROM MEASURE testMetric_minute IN " + groupName + " TIME BETWEEN ? AND ?";
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            MeasureQueryResponse updatedResp = client.queryMeasure(updatedQl,
                Value.timestampTagValue(begin.toEpochMilli()),
                Value.timestampTagValue(now.plus(15, ChronoUnit.MINUTES).toEpochMilli()));
            assertNotNull(updatedResp);
            assertEquals(2, updatedResp.getDataPoints().size());
            // Index by service_id so the assertions don't depend on server-side ordering
            // (the query sets no ORDER BY and BanyanDB result order is not contractually stable).
            Map<String, DataPoint> byService = updatedResp.getDataPoints().stream()
                .collect(Collectors.toMap(dp -> (String) dp.getTagValue("service_id"), dp -> dp));
            DataPoint dp1 = byService.get("service1");
            assertNotNull(dp1);
            assertEquals("tag1", dp1.getTagValue("tag"));
            assertEquals(100, (Long) dp1.getFieldValue("value"));
            DataPoint dp2 = byService.get("service2");
            assertNotNull(dp2);
            assertEquals("tag1", dp2.getTagValue("tag"));
            assertEquals("new_tag1", dp2.getTagValue("new_tag"));
            assertEquals(101, (Long) dp2.getFieldValue("value"));
            assertEquals(1000, (Long) dp2.getFieldValue("new_value"));
        });
    }

    @Test
    public void testStreamInstallAndUpdate() throws Exception {
        DownSamplingConfigService downSamplingConfigService = new DownSamplingConfigService(Arrays.asList("minute"));
        ModuleManager moduleManager = mock(ModuleManager.class);
        ModuleProviderHolder moduleProviderHolder = mock(ModuleProviderHolder.class);
        ModuleServiceHolder moduleServiceHolder = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(moduleProviderHolder);
        when(moduleProviderHolder.provider()).thenReturn(moduleServiceHolder);
        when(moduleServiceHolder.getService(DownSamplingConfigService.class)).thenReturn(downSamplingConfigService);

        StorageModels models = new StorageModels();
        Model model = models.add(TestStream.class, DefaultScopeDefine.SERVICE,
                                 new Storage("testStream", true, DownSampling.Second),
                                 StorageManipulationOpt.runtimeApply()
        );
        BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        installer.isExists(model, StorageManipulationOpt.runtimeApply());
        // test Group install
        String groupName = MetadataRegistry.convertGroupName(
            config.getGlobal().getNamespace(),
            BanyanDB.StreamGroup.RECORDS_LOG.getName()
        );
        BanyandbCommon.Group group = client.client.findGroup(groupName);
        assertEquals(BanyandbCommon.Catalog.CATALOG_STREAM, group.getCatalog());
        assertEquals(config.getRecordsLog().getSegmentInterval(), group.getResourceOpts().getSegmentInterval().getNum());
        assertEquals(config.getRecordsLog().getShardNum(), group.getResourceOpts().getShardNum());
        assertEquals(BanyandbCommon.IntervalRule.Unit.UNIT_DAY, group.getResourceOpts().getSegmentInterval().getUnit());
        assertEquals(config.getRecordsLog().getTtl(), group.getResourceOpts().getTtl().getNum());
        assertEquals(BanyandbCommon.IntervalRule.Unit.UNIT_DAY, group.getResourceOpts().getTtl().getUnit());

        installer.createTable(model);
        // test Stream install
        BanyandbDatabase.Stream stream = client.client.findStream(groupName, "testStream");
        assertEquals("entity=[service_id] tags=[storage-only/service_id:TAG_TYPE_STRING, storage-only/timestamp:TAG_TYPE_INT,"
                + " searchable/tag:TAG_TYPE_STRING]", describe(stream));
        // test IndexRule install
        BanyandbDatabase.IndexRule indexRuleTag = client.client.findIndexRule(groupName, "tag");
        assertEquals("tags=[tag] type=TYPE_INVERTED analyzer=url noSort=true", describe(indexRuleTag));
        // test IndexRuleBinding install
        BanyandbDatabase.IndexRuleBinding indexRuleBinding = client.client.findIndexRuleBinding(
            groupName, "testStream");
        assertEquals("subject=CATALOG_STREAM/testStream rules=[tag]", describe(indexRuleBinding));

        // StorageModels.add now dedupes by (name, downsampling); evict the original
        // registration so the UpdateTestStream registration takes effect.
        models.remove(TestStream.class, StorageManipulationOpt.runtimeApply());
        Model updatedModel = models.add(UpdateTestStream.class, DefaultScopeDefine.SERVICE,
                                        new Storage("testStream", true, DownSampling.Second),
                                        StorageManipulationOpt.runtimeApply()
        );
        BanyanDBIndexInstaller newInstaller = new BanyanDBIndexInstaller(client, moduleManager, config);
        newInstaller.isExists(updatedModel, StorageManipulationOpt.runtimeApply());
        // test Stream update
        BanyandbDatabase.Stream updatedStream = client.client.findStream(groupName, "testStream");
        assertEquals("entity=[service_id] tags=[storage-only/service_id:TAG_TYPE_STRING, storage-only/timestamp:TAG_TYPE_INT,"
                + " searchable/tag:TAG_TYPE_STRING, searchable/new_tag:TAG_TYPE_STRING]", describe(updatedStream));
        // test IndexRule update
        BanyandbDatabase.IndexRule updatedIndexRuleTag = client.client.findIndexRule(groupName, "tag");
        assertEquals("tags=[tag] type=TYPE_INVERTED analyzer= noSort=false", describe(updatedIndexRuleTag));
        BanyandbDatabase.IndexRule updatedIndexRuleNewTag = client.client.findIndexRule(groupName, "new_tag");
        assertEquals("tags=[new_tag] type=TYPE_INVERTED analyzer= noSort=true", describe(updatedIndexRuleNewTag));
        // test IndexRuleBinding update
        BanyandbDatabase.IndexRuleBinding updatedIndexRuleBinding = client.client.findIndexRuleBinding(
            groupName, "testStream");
        assertEquals("subject=CATALOG_STREAM/testStream rules=[tag, new_tag]", describe(updatedIndexRuleBinding));
    }

    /**
     * Boot applies an additive change: install {@link TestStream} first under
     * {@link StorageManipulationOpt#runtimeApply()} so the stream lives on the backend
     * with the original shape, then re-install a class that adds {@code new_tag} under
     * {@link StorageManipulationOpt#bootInstall()}. The installer applies the update and records
     * {@link StorageManipulationOpt.Outcome#UPDATED}.
     */
    @Test
    public void testStreamAdditiveBootReshape() throws Exception {
        DownSamplingConfigService downSamplingConfigService = new DownSamplingConfigService(Arrays.asList("minute"));
        ModuleManager moduleManager = mock(ModuleManager.class);
        ModuleProviderHolder moduleProviderHolder = mock(ModuleProviderHolder.class);
        ModuleServiceHolder moduleServiceHolder = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(moduleProviderHolder);
        when(moduleProviderHolder.provider()).thenReturn(moduleServiceHolder);
        when(moduleServiceHolder.getService(DownSamplingConfigService.class)).thenReturn(downSamplingConfigService);

        StorageModels models = new StorageModels();
        Model baseModel = models.add(TestStream.class, DefaultScopeDefine.SERVICE,
                                     new Storage("testStream", true, DownSampling.Second),
                                     StorageManipulationOpt.runtimeApply());
        BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        installer.isExists(baseModel, StorageManipulationOpt.runtimeApply());
        installer.createTable(baseModel);

        String groupName = MetadataRegistry.convertGroupName(
            config.getGlobal().getNamespace(), BanyanDB.StreamGroup.RECORDS_LOG.getName());
        BanyandbDatabase.Stream initial = client.client.findStream(groupName, "testStream");
        assertEquals("entity=[service_id] tags=[storage-only/service_id:TAG_TYPE_STRING, storage-only/timestamp:TAG_TYPE_INT,"
                + " searchable/tag:TAG_TYPE_STRING]", describe(initial));

        models.remove(TestStream.class, StorageManipulationOpt.runtimeApply());
        Model reshapedModel = models.add(TestStreamAdditive.class, DefaultScopeDefine.SERVICE,
                                         new Storage("testStream", true, DownSampling.Second),
                                         StorageManipulationOpt.runtimeApply());

        StorageManipulationOpt bootOpt = StorageManipulationOpt.bootInstall();
        new BanyanDBIndexInstaller(client, moduleManager, config).isExists(reshapedModel, bootOpt);

        BanyandbDatabase.Stream reshaped = client.client.findStream(groupName, "testStream");
        assertEquals("entity=[service_id] tags=[storage-only/service_id:TAG_TYPE_STRING, storage-only/timestamp:TAG_TYPE_INT,"
                + " searchable/tag:TAG_TYPE_STRING, searchable/new_tag:TAG_TYPE_STRING]", describe(reshaped));

        boolean updatedRecorded = bootOpt.getOutcomes().stream()
            .anyMatch(o -> "stream".equals(o.getResourceType())
                && (groupName + "/testStream").equals(o.getResourceName())
                && o.getStatus() == StorageManipulationOpt.Outcome.UPDATED);
        assertTrue(updatedRecorded, "expected UPDATED outcome for additive boot reshape, got " + bootOpt.getOutcomes());
    }

    /**
     * Same setup as {@link #testStreamAdditiveBootReshape}, but a not-main OAP's startup check
     * ({@link StorageManipulationOpt#runtimePeerVerify()}) sees the additive difference. It never changes the
     * backend: it records {@link StorageManipulationOpt.Outcome#RUNTIME_PEER_SCHEMA_DIFFERENT} for the stream and
     * its dependents and leaves the live stream untouched.
     */
    @Test
    public void testStreamAdditiveDifference_peerVerifyLeavesItAlone() throws Exception {
        DownSamplingConfigService downSamplingConfigService = new DownSamplingConfigService(Arrays.asList("minute"));
        ModuleManager moduleManager = mock(ModuleManager.class);
        ModuleProviderHolder moduleProviderHolder = mock(ModuleProviderHolder.class);
        ModuleServiceHolder moduleServiceHolder = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(moduleProviderHolder);
        when(moduleProviderHolder.provider()).thenReturn(moduleServiceHolder);
        when(moduleServiceHolder.getService(DownSamplingConfigService.class)).thenReturn(downSamplingConfigService);

        StorageModels models = new StorageModels();
        Model baseModel = models.add(TestStream.class, DefaultScopeDefine.SERVICE,
                                     new Storage("testStream", true, DownSampling.Second),
                                     StorageManipulationOpt.runtimeApply());
        BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        installer.isExists(baseModel, StorageManipulationOpt.runtimeApply());
        installer.createTable(baseModel);

        models.remove(TestStream.class, StorageManipulationOpt.runtimeApply());
        Model reshapedModel = models.add(TestStreamAdditive.class, DefaultScopeDefine.SERVICE,
                                         new Storage("testStream", true, DownSampling.Second),
                                         StorageManipulationOpt.runtimeApply());

        StorageManipulationOpt verifyOpt = StorageManipulationOpt.runtimePeerVerify();
        new BanyanDBIndexInstaller(client, moduleManager, config).isExists(reshapedModel, verifyOpt);

        String groupName = MetadataRegistry.convertGroupName(
            config.getGlobal().getNamespace(), BanyanDB.StreamGroup.RECORDS_LOG.getName());
        BanyandbDatabase.Stream live = client.client.findStream(groupName, "testStream");
        assertEquals("entity=[service_id] tags=[storage-only/service_id:TAG_TYPE_STRING, storage-only/timestamp:TAG_TYPE_INT,"
                + " searchable/tag:TAG_TYPE_STRING]", describe(live));

        assertOutcome(verifyOpt, "stream", groupName + "/testStream",
            StorageManipulationOpt.Outcome.RUNTIME_PEER_SCHEMA_DIFFERENT);
        // The binding and index rules are left alone too: no rule for the new tag, the binding unchanged.
        assertNull(client.client.findIndexRule(groupName, "new_tag"));
        assertEquals("subject=CATALOG_STREAM/testStream rules=[tag]",
            describe(client.client.findIndexRuleBinding(groupName, "testStream")));
    }

    /**
     * A difference that is not purely additive (here, the {@code tag} column flips from
     * {@code String} → {@code long}, i.e. {@code TAG_TYPE_STRING} → {@code TAG_TYPE_INT})
     * is refused at boot. {@link StorageManipulationOpt.Outcome#BOOT_REFUSED_BREAKING_CHANGE}
     * is recorded and the live stream stays unchanged, forcing the operator to do an
     * explicit drop+recreate (the only safe path for identity-breaking changes).
     */
    @Test
    public void testStreamNonAdditiveBootReshape_refused() throws Exception {
        DownSamplingConfigService downSamplingConfigService = new DownSamplingConfigService(Arrays.asList("minute"));
        ModuleManager moduleManager = mock(ModuleManager.class);
        ModuleProviderHolder moduleProviderHolder = mock(ModuleProviderHolder.class);
        ModuleServiceHolder moduleServiceHolder = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(moduleProviderHolder);
        when(moduleProviderHolder.provider()).thenReturn(moduleServiceHolder);
        when(moduleServiceHolder.getService(DownSamplingConfigService.class)).thenReturn(downSamplingConfigService);

        StorageModels models = new StorageModels();
        Model baseModel = models.add(TestStream.class, DefaultScopeDefine.SERVICE,
                                     new Storage("testStream", true, DownSampling.Second),
                                     StorageManipulationOpt.runtimeApply());
        BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        installer.isExists(baseModel, StorageManipulationOpt.runtimeApply());
        installer.createTable(baseModel);

        models.remove(TestStream.class, StorageManipulationOpt.runtimeApply());
        Model reshapedModel = models.add(TestStreamNonAdditive.class, DefaultScopeDefine.SERVICE,
                                         new Storage("testStream", true, DownSampling.Second),
                                         StorageManipulationOpt.runtimeApply());

        StorageManipulationOpt bootOpt = StorageManipulationOpt.bootInstall();
        new BanyanDBIndexInstaller(client, moduleManager, config).isExists(reshapedModel, bootOpt);

        String groupName = MetadataRegistry.convertGroupName(
            config.getGlobal().getNamespace(), BanyanDB.StreamGroup.RECORDS_LOG.getName());
        BanyandbDatabase.Stream live = client.client.findStream(groupName, "testStream");
        assertEquals("entity=[service_id] tags=[storage-only/service_id:TAG_TYPE_STRING, storage-only/timestamp:TAG_TYPE_INT,"
                + " searchable/tag:TAG_TYPE_STRING]", describe(live));

        boolean refusalRecorded = bootOpt.getOutcomes().stream()
            .anyMatch(o -> "stream".equals(o.getResourceType())
                && (groupName + "/testStream").equals(o.getResourceName())
                && o.getStatus() == StorageManipulationOpt.Outcome.BOOT_REFUSED_BREAKING_CHANGE);
        assertTrue(refusalRecorded,
            "expected BOOT_REFUSED_BREAKING_CHANGE for a non-additive (type-change) diff, got " + bootOpt.getOutcomes());
    }

    /**
     * Toggling {@code storageOnly} on an existing {@code @Column} moves the tag from
     * {@code storage-only} → {@code searchable} (or vice versa). Although the live tag
     * family no longer contains the tag at its old position, the tag identity + type are
     * preserved, so {@link BanyanDBIndexInstaller#isPurelyAdditiveStream} (via
     * {@code isPurelyAdditiveTagFamilies}) accepts the relocation at boot on an init or default-mode OAP. The
     * dependent IndexRule for the now-indexed tag should also be created.
     */
    @Test
    public void testStreamStorageOnlyTogglePathBootReshape() throws Exception {
        DownSamplingConfigService downSamplingConfigService = new DownSamplingConfigService(Arrays.asList("minute"));
        ModuleManager moduleManager = mock(ModuleManager.class);
        ModuleProviderHolder moduleProviderHolder = mock(ModuleProviderHolder.class);
        ModuleServiceHolder moduleServiceHolder = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(moduleProviderHolder);
        when(moduleProviderHolder.provider()).thenReturn(moduleServiceHolder);
        when(moduleServiceHolder.getService(DownSamplingConfigService.class)).thenReturn(downSamplingConfigService);

        StorageModels models = new StorageModels();
        Model baseModel = models.add(TestStreamStorageOnly.class, DefaultScopeDefine.SERVICE,
                                     new Storage("relocStream", true, DownSampling.Second),
                                     StorageManipulationOpt.runtimeApply());
        BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        installer.isExists(baseModel, StorageManipulationOpt.runtimeApply());
        installer.createTable(baseModel);

        String groupName = MetadataRegistry.convertGroupName(
            config.getGlobal().getNamespace(), BanyanDB.StreamGroup.RECORDS_LOG.getName());
        BanyandbDatabase.Stream initial = client.client.findStream(groupName, "relocStream");
        // payload starts in storage-only family
        assertEquals("entity=[service_id] tags=[storage-only/service_id:TAG_TYPE_STRING,"
                + " storage-only/payload:TAG_TYPE_STRING, storage-only/timestamp:TAG_TYPE_INT]",
            describe(initial));

        models.remove(TestStreamStorageOnly.class, StorageManipulationOpt.runtimeApply());
        Model reshapedModel = models.add(TestStreamStorageOnlyOff.class, DefaultScopeDefine.SERVICE,
                                         new Storage("relocStream", true, DownSampling.Second),
                                         StorageManipulationOpt.runtimeApply());

        StorageManipulationOpt bootOpt = StorageManipulationOpt.bootInstall();
        new BanyanDBIndexInstaller(client, moduleManager, config).isExists(reshapedModel, bootOpt);

        BanyandbDatabase.Stream reshaped = client.client.findStream(groupName, "relocStream");
        // payload is now in searchable family
        assertEquals("entity=[service_id] tags=[storage-only/service_id:TAG_TYPE_STRING,"
                + " storage-only/timestamp:TAG_TYPE_INT, searchable/payload:TAG_TYPE_STRING]",
            describe(reshaped));

        boolean updatedRecorded = bootOpt.getOutcomes().stream()
            .anyMatch(o -> "stream".equals(o.getResourceType())
                && (groupName + "/relocStream").equals(o.getResourceName())
                && o.getStatus() == StorageManipulationOpt.Outcome.UPDATED);
        assertTrue(updatedRecorded, "expected UPDATED outcome for storageOnly relocation, got " + bootOpt.getOutcomes());

        // Relocation is "additive" → checkStream returns true → dependent index-rule
        // reconciliation runs. Verify the IndexRule for the newly-indexed `payload` tag was
        // created and that the IndexRuleBinding now references it. This is the behavior the
        // dependent-reconcile gate is supposed to permit when the primary shape change is
        // accepted.
        BanyandbDatabase.IndexRule payloadIndexRule = client.client.findIndexRule(groupName, "payload");
        assertNotNull(payloadIndexRule, "expected IndexRule 'payload' to be created after relocation");
        assertEquals("tags=[payload] type=TYPE_INVERTED analyzer= noSort=true", describe(payloadIndexRule));
        BanyandbDatabase.IndexRuleBinding binding = client.client.findIndexRuleBinding(groupName, "relocStream");
        assertNotNull(binding, "expected IndexRuleBinding for relocStream to be present after relocation");
        assertEquals("subject=CATALOG_STREAM/relocStream rules=[payload]", describe(binding));
    }

    /**
     * Boot applies an additive measure change: {@link TestMetric} is installed first, then {@link TestMetricAdditive}
     * (one more tag and one more field) is registered under {@link StorageManipulationOpt#bootInstall()}. The measure
     * is updated in place and {@link StorageManipulationOpt.Outcome#UPDATED} recorded.
     */
    @Test
    public void testMeasureAdditiveBootReshape() throws Exception {
        DownSamplingConfigService downSamplingConfigService = new DownSamplingConfigService(Arrays.asList("minute"));
        ModuleManager moduleManager = mock(ModuleManager.class);
        ModuleProviderHolder moduleProviderHolder = mock(ModuleProviderHolder.class);
        ModuleServiceHolder moduleServiceHolder = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(moduleProviderHolder);
        when(moduleProviderHolder.provider()).thenReturn(moduleServiceHolder);
        when(moduleServiceHolder.getService(DownSamplingConfigService.class)).thenReturn(downSamplingConfigService);

        StorageModels models = new StorageModels();
        Model baseModel = models.add(TestMetric.class, DefaultScopeDefine.SERVICE,
                                     new Storage("testMetric", true, DownSampling.Minute),
                                     StorageManipulationOpt.runtimeApply());
        BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        installer.isExists(baseModel, StorageManipulationOpt.runtimeApply());
        installer.createTable(baseModel);

        models.remove(TestMetric.class, StorageManipulationOpt.runtimeApply());
        Model reshapedModel = models.add(TestMetricAdditive.class, DefaultScopeDefine.SERVICE,
                                         new Storage("testMetric", true, DownSampling.Minute),
                                         StorageManipulationOpt.runtimeApply());
        StorageManipulationOpt bootOpt = StorageManipulationOpt.bootInstall();
        new BanyanDBIndexInstaller(client, moduleManager, config).isExists(reshapedModel, bootOpt);

        String groupName = MetadataRegistry.convertGroupName(
            config.getGlobal().getNamespace(), BanyanDB.MeasureGroup.METRICS_MINUTE.getName());
        BanyandbDatabase.Measure reshaped = client.client.findMeasure(groupName, "testMetric_minute");
        assertEquals("entity=[service_id] shardingKey=[service_id] interval=1m indexMode=false"
                + " tags=[storage-only/service_id:TAG_TYPE_STRING, searchable/tag:TAG_TYPE_STRING,"
                + " searchable/new_tag:TAG_TYPE_STRING]"
                + " fields=[value:FIELD_TYPE_INT:ENCODING_METHOD_GORILLA:COMPRESSION_METHOD_ZSTD,"
                + " new_value:FIELD_TYPE_INT:ENCODING_METHOD_GORILLA:COMPRESSION_METHOD_ZSTD]",
            describe(reshaped));
        assertOutcome(bootOpt, "measure", groupName + "/testMetric_minute", StorageManipulationOpt.Outcome.UPDATED);
    }

    /**
     * Boot refuses a breaking measure change: {@link TestMetricRetyped} stores {@code value} as a double where the
     * live measure has a long field. The live measure keeps its shape and records
     * {@link StorageManipulationOpt.Outcome#BOOT_REFUSED_BREAKING_CHANGE}; its index rules and binding are left as
     * they are.
     */
    @Test
    public void testMeasureNonAdditiveBootReshape_refused() throws Exception {
        DownSamplingConfigService downSamplingConfigService = new DownSamplingConfigService(Arrays.asList("minute"));
        ModuleManager moduleManager = mock(ModuleManager.class);
        ModuleProviderHolder moduleProviderHolder = mock(ModuleProviderHolder.class);
        ModuleServiceHolder moduleServiceHolder = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(moduleProviderHolder);
        when(moduleProviderHolder.provider()).thenReturn(moduleServiceHolder);
        when(moduleServiceHolder.getService(DownSamplingConfigService.class)).thenReturn(downSamplingConfigService);

        StorageModels models = new StorageModels();
        Model baseModel = models.add(TestMetric.class, DefaultScopeDefine.SERVICE,
                                     new Storage("testMetric", true, DownSampling.Minute),
                                     StorageManipulationOpt.runtimeApply());
        BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        installer.isExists(baseModel, StorageManipulationOpt.runtimeApply());
        installer.createTable(baseModel);

        models.remove(TestMetric.class, StorageManipulationOpt.runtimeApply());
        Model reshapedModel = models.add(TestMetricRetyped.class, DefaultScopeDefine.SERVICE,
                                         new Storage("testMetric", true, DownSampling.Minute),
                                         StorageManipulationOpt.runtimeApply());
        StorageManipulationOpt bootOpt = StorageManipulationOpt.bootInstall();
        new BanyanDBIndexInstaller(client, moduleManager, config).isExists(reshapedModel, bootOpt);

        String groupName = MetadataRegistry.convertGroupName(
            config.getGlobal().getNamespace(), BanyanDB.MeasureGroup.METRICS_MINUTE.getName());
        BanyandbDatabase.Measure live = client.client.findMeasure(groupName, "testMetric_minute");
        assertEquals("entity=[service_id] shardingKey=[service_id] interval=1m indexMode=false"
                + " tags=[storage-only/service_id:TAG_TYPE_STRING, searchable/tag:TAG_TYPE_STRING]"
                + " fields=[value:FIELD_TYPE_INT:ENCODING_METHOD_GORILLA:COMPRESSION_METHOD_ZSTD]",
            describe(live));
        assertOutcome(bootOpt, "measure", groupName + "/testMetric_minute",
            StorageManipulationOpt.Outcome.BOOT_REFUSED_BREAKING_CHANGE);
        assertTrue(bootOpt.getOutcomes().stream().anyMatch(o -> o.getDiff() != null
                && o.getDiff().contains("its index rules and binding are left as they are")
                && o.getDiff().contains("delete the measure with BanyanDB's own tools")),
            "expected the refusal to say its index rules and binding are left, and how to reach the declared "
                + "shape, got " + bootOpt.getOutcomes());
        assertEquals("subject=CATALOG_MEASURE/testMetric_minute rules=[tag]",
            describe(client.client.findIndexRuleBinding(groupName, "testMetric_minute")));
    }

    /**
     * Two measures of one group share the index rule {@code tag}. One installer checks {@link TestMetric} first, then
     * {@link TestMetricSharedAdditive}, which gained {@code new_tag} since it was created. The shared rule was already
     * checked for the first measure, and the boot must still define the second measure's new rule and bind it.
     */
    @Test
    public void testMeasureAdditiveBootReshape_afterSharedIndexRule() throws Exception {
        DownSamplingConfigService downSamplingConfigService = new DownSamplingConfigService(Arrays.asList("minute"));
        ModuleManager moduleManager = mock(ModuleManager.class);
        ModuleProviderHolder moduleProviderHolder = mock(ModuleProviderHolder.class);
        ModuleServiceHolder moduleServiceHolder = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(moduleProviderHolder);
        when(moduleProviderHolder.provider()).thenReturn(moduleServiceHolder);
        when(moduleServiceHolder.getService(DownSamplingConfigService.class)).thenReturn(downSamplingConfigService);

        StorageModels models = new StorageModels();
        Model first = models.add(TestMetric.class, DefaultScopeDefine.SERVICE,
                                 new Storage("testMetric", true, DownSampling.Minute),
                                 StorageManipulationOpt.runtimeApply());
        Model secondBase = models.add(TestMetricShared.class, DefaultScopeDefine.SERVICE,
                                      new Storage("testMetricShared", true, DownSampling.Minute),
                                      StorageManipulationOpt.runtimeApply());
        BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        installer.isExists(first, StorageManipulationOpt.runtimeApply());
        installer.createTable(first);
        installer.isExists(secondBase, StorageManipulationOpt.runtimeApply());
        installer.createTable(secondBase);

        models.remove(TestMetricShared.class, StorageManipulationOpt.runtimeApply());
        Model secondAdditive = models.add(TestMetricSharedAdditive.class, DefaultScopeDefine.SERVICE,
                                          new Storage("testMetricShared", true, DownSampling.Minute),
                                          StorageManipulationOpt.runtimeApply());
        StorageManipulationOpt bootOpt = StorageManipulationOpt.bootInstall();
        BanyanDBIndexInstaller bootInstaller = new BanyanDBIndexInstaller(client, moduleManager, config);
        bootInstaller.isExists(first, bootOpt);
        bootInstaller.isExists(secondAdditive, bootOpt);

        String groupName = MetadataRegistry.convertGroupName(
            config.getGlobal().getNamespace(), BanyanDB.MeasureGroup.METRICS_MINUTE.getName());
        assertOutcome(bootOpt, "measure", groupName + "/testMetricShared_minute", StorageManipulationOpt.Outcome.UPDATED);
        assertEquals("tags=[new_tag] type=TYPE_INVERTED analyzer= noSort=true",
            describe(client.client.findIndexRule(groupName, "new_tag")));
        assertEquals("subject=CATALOG_MEASURE/testMetricShared_minute rules=[tag, new_tag]",
            describe(client.client.findIndexRuleBinding(groupName, "testMetricShared_minute")));
    }

    /**
     * Two measures of one group share the index rule {@code tag}, which the backend lacks. A not-main OAP's startup
     * check reports it absent for each measure: a rule whose check failed is checked again for the next model.
     */
    @Test
    public void testPeerVerifyReportsASharedMissingIndexRuleForEachMeasure() throws Exception {
        ModuleManager moduleManager = moduleManagerWithMinuteDownSampling();
        StorageModels models = new StorageModels();
        Model first = models.add(TestMetric.class, DefaultScopeDefine.SERVICE,
                                 new Storage("testMetric", true, DownSampling.Minute),
                                 StorageManipulationOpt.runtimeApply());
        Model second = models.add(TestMetricShared.class, DefaultScopeDefine.SERVICE,
                                  new Storage("testMetricShared", true, DownSampling.Minute),
                                  StorageManipulationOpt.runtimeApply());
        BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        installer.isExists(first, StorageManipulationOpt.runtimeApply());
        installer.createTable(first);
        installer.isExists(second, StorageManipulationOpt.runtimeApply());
        installer.createTable(second);
        String groupName = MetadataRegistry.convertGroupName(
            config.getGlobal().getNamespace(), BanyanDB.MeasureGroup.METRICS_MINUTE.getName());
        client.client.deleteIndexRule(groupName, "tag");

        StorageManipulationOpt verifyOpt = StorageManipulationOpt.runtimePeerVerify();
        BanyanDBIndexInstaller peerInstaller = new BanyanDBIndexInstaller(client, moduleManager, config);
        peerInstaller.isExists(first, verifyOpt);
        peerInstaller.isExists(second, verifyOpt);

        assertEquals(2, verifyOpt.getOutcomes().stream()
                                 .filter(o -> "indexRule".equals(o.getResourceType())
                                     && (groupName + "/tag").equals(o.getResourceName())
                                     && o.getStatus() == StorageManipulationOpt.Outcome.RUNTIME_PEER_SCHEMA_ABSENT)
                                 .count(),
            "expected the missing rule reported for both measures, got " + verifyOpt.getOutcomes());

        // Put the rule back for the other tests of this group.
        new BanyanDBIndexInstaller(client, moduleManager, config).isExists(first, StorageManipulationOpt.runtimeApply());
        assertEquals("tags=[tag] type=TYPE_INVERTED analyzer=url noSort=true",
            describe(client.client.findIndexRule(groupName, "tag")));
    }

    /**
     * Dropping a measure drops its index rule binding before the measure, and records each drop. A group the
     * installer compares with its declaration records a match.
     */
    @Test
    public void testDropMeasureRecordsItsBindingAndItself() throws Exception {
        ModuleManager moduleManager = moduleManagerWithMinuteDownSampling();
        Model model = new StorageModels().add(TestMetric.class, DefaultScopeDefine.SERVICE,
                                              new Storage("testMetricDropped", true, DownSampling.Minute),
                                              StorageManipulationOpt.runtimeApply());
        StorageManipulationOpt createOpt = StorageManipulationOpt.runtimeApply();
        BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        installer.isExists(model, createOpt);
        installer.createTable(model, createOpt);
        String groupName = MetadataRegistry.convertGroupName(
            config.getGlobal().getNamespace(), BanyanDB.MeasureGroup.METRICS_MINUTE.getName());
        StorageManipulationOpt checkOpt = StorageManipulationOpt.runtimeApply();
        new BanyanDBIndexInstaller(client, moduleManager, config).isExists(model, checkOpt);
        assertOutcome(checkOpt, "group", groupName, StorageManipulationOpt.Outcome.MATCHED);

        StorageManipulationOpt dropOpt = StorageManipulationOpt.runtimeApply();
        installer.dropTable(model, dropOpt);

        assertOutcome(dropOpt, "indexRuleBinding", groupName + "/testMetricDropped_minute",
            StorageManipulationOpt.Outcome.DROPPED);
        assertOutcome(dropOpt, "measure", groupName + "/testMetricDropped_minute",
            StorageManipulationOpt.Outcome.DROPPED);
        assertNull(client.client.findIndexRuleBinding(groupName, "testMetricDropped_minute"));
    }

    /**
     * Boot applies an additive trace change: {@link TestTrace} is installed first, then {@link TestTraceAdditive}
     * (one more tag) is registered under {@link StorageManipulationOpt#bootInstall()}. The trace is updated in place
     * and {@link StorageManipulationOpt.Outcome#UPDATED} recorded.
     */
    @Test
    public void testTraceAdditiveBootReshape() throws Exception {
        DownSamplingConfigService downSamplingConfigService = new DownSamplingConfigService(Arrays.asList("minute"));
        ModuleManager moduleManager = mock(ModuleManager.class);
        ModuleProviderHolder moduleProviderHolder = mock(ModuleProviderHolder.class);
        ModuleServiceHolder moduleServiceHolder = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(moduleProviderHolder);
        when(moduleProviderHolder.provider()).thenReturn(moduleServiceHolder);
        when(moduleServiceHolder.getService(DownSamplingConfigService.class)).thenReturn(downSamplingConfigService);

        StorageModels models = new StorageModels();
        Model baseModel = models.add(TestTrace.class, DefaultScopeDefine.SERVICE,
                                     new Storage("testTrace", true, DownSampling.Second),
                                     StorageManipulationOpt.runtimeApply());
        BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        installer.isExists(baseModel, StorageManipulationOpt.runtimeApply());
        installer.createTable(baseModel);

        String groupName = MetadataRegistry.convertGroupName(
            config.getGlobal().getNamespace(), BanyanDB.TraceGroup.TRACE.getName());
        assertEquals("traceId=trace_id spanId=span_id timestamp=timestamp"
                + " tags=[trace_id:TAG_TYPE_STRING, span_id:TAG_TYPE_STRING, service_id:TAG_TYPE_STRING,"
                + " timestamp:TAG_TYPE_TIMESTAMP]",
            describe(client.client.findTrace(groupName, "testTrace")));

        models.remove(TestTrace.class, StorageManipulationOpt.runtimeApply());
        Model reshapedModel = models.add(TestTraceAdditive.class, DefaultScopeDefine.SERVICE,
                                         new Storage("testTrace", true, DownSampling.Second),
                                         StorageManipulationOpt.runtimeApply());
        StorageManipulationOpt bootOpt = StorageManipulationOpt.bootInstall();
        new BanyanDBIndexInstaller(client, moduleManager, config).isExists(reshapedModel, bootOpt);

        assertEquals("traceId=trace_id spanId=span_id timestamp=timestamp"
                + " tags=[trace_id:TAG_TYPE_STRING, span_id:TAG_TYPE_STRING, service_id:TAG_TYPE_STRING,"
                + " endpoint_id:TAG_TYPE_STRING, timestamp:TAG_TYPE_TIMESTAMP]",
            describe(client.client.findTrace(groupName, "testTrace")));
        assertOutcome(bootOpt, "trace", groupName + "/testTrace", StorageManipulationOpt.Outcome.UPDATED);
    }

    private static ModuleManager moduleManagerWithMinuteDownSampling() {
        DownSamplingConfigService downSamplingConfigService = new DownSamplingConfigService(Arrays.asList("minute"));
        ModuleManager moduleManager = mock(ModuleManager.class);
        ModuleProviderHolder moduleProviderHolder = mock(ModuleProviderHolder.class);
        ModuleServiceHolder moduleServiceHolder = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(moduleProviderHolder);
        when(moduleProviderHolder.provider()).thenReturn(moduleServiceHolder);
        when(moduleServiceHolder.getService(DownSamplingConfigService.class)).thenReturn(downSamplingConfigService);
        return moduleManager;
    }

    private static void assertOutcome(final StorageManipulationOpt opt, final String resourceType,
                                      final String resourceName, final StorageManipulationOpt.Outcome status) {
        assertTrue(opt.getOutcomes().stream().anyMatch(o -> resourceType.equals(o.getResourceType())
                && resourceName.equals(o.getResourceName()) && o.getStatus() == status),
            "expected " + resourceType + "(" + resourceName + ")=" + status + ", got " + opt.getOutcomes());
    }

    // The describe() helpers render a schema object as BanyanDB holds it, with every property the installer sets, so
    // an assertion on one fails on any change to the stored shape, whatever annotation produced it.

    private static String describe(final BanyandbDatabase.Measure measure) {
        return "entity=" + measure.getEntity().getTagNamesList()
            + " shardingKey=" + measure.getShardingKey().getTagNamesList()
            + " interval=" + measure.getInterval()
            + " indexMode=" + measure.getIndexMode()
            + " tags=" + tags(measure.getTagFamiliesList())
            + " fields=" + measure.getFieldsList().stream()
                                  .map(f -> f.getName() + ":" + f.getFieldType() + ":" + f.getEncodingMethod() + ":"
                                      + f.getCompressionMethod())
                                  .collect(Collectors.toList());
    }

    private static String describe(final BanyandbDatabase.Stream stream) {
        return "entity=" + stream.getEntity().getTagNamesList() + " tags=" + tags(stream.getTagFamiliesList());
    }

    private static String describe(final BanyandbDatabase.Trace trace) {
        return "traceId=" + trace.getTraceIdTagName() + " spanId=" + trace.getSpanIdTagName()
            + " timestamp=" + trace.getTimestampTagName()
            + " tags=" + trace.getTagsList().stream().map(t -> t.getName() + ":" + t.getType()).collect(Collectors.toList());
    }

    private static String describe(final BanyandbDatabase.IndexRule rule) {
        return "tags=" + rule.getTagsList() + " type=" + rule.getType() + " analyzer=" + rule.getAnalyzer()
            + " noSort=" + rule.getNoSort();
    }

    private static String describe(final BanyandbDatabase.IndexRuleBinding binding) {
        return "subject=" + binding.getSubject().getCatalog() + "/" + binding.getSubject().getName()
            + " rules=" + binding.getRulesList();
    }

    private static List<String> tags(final List<BanyandbDatabase.TagFamilySpec> families) {
        return families.stream()
                       .flatMap(f -> f.getTagsList().stream().map(t -> f.getName() + "/" + t.getName() + ":" + t.getType()))
                       .collect(Collectors.toList());
    }

    /**
     * Initial state for {@link #testStreamStorageOnlyTogglePathBootReshape}: {@code payload}
     * declared with {@code storageOnly = true}, so it lands in the {@code storage-only}
     * tag family.
     */
    @Stream(name = "relocStream", scopeId = DefaultScopeDefine.SERVICE,
        builder = TestStreamStorageOnly.Builder.class, processor = RecordStreamProcessor.class)
    @BanyanDB.Stream.Group(BanyanDB.StreamGroup.RECORDS_LOG)
    @BanyanDB.Stream.TimestampColumn("timestamp")
    private static class TestStreamStorageOnly extends Record {
        @Column(name = "service_id")
        @BanyanDB.Stream.SeriesID(index = 0)
        private String serviceId;
        @Column(name = "payload", storageOnly = true)
        private String payload;
        @Column(name = "timestamp")
        private long timestamp;

        @Override
        public StorageID id() {
            return new StorageID();
        }

        static class Builder implements StorageBuilder<StorageData> {
            @Override
            public StorageData storage2Entity(final Convert2Entity converter) {
                return null;
            }

            @Override
            public void entity2Storage(final StorageData entity, final Convert2Storage converter) {
            }
        }
    }

    /**
     * Reshape target: same {@code payload} column, but {@code storageOnly} is gone so the
     * tag relocates to the {@code searchable} family.
     */
    @Stream(name = "relocStream", scopeId = DefaultScopeDefine.SERVICE,
        builder = TestStreamStorageOnlyOff.Builder.class, processor = RecordStreamProcessor.class)
    @BanyanDB.Stream.Group(BanyanDB.StreamGroup.RECORDS_LOG)
    @BanyanDB.Stream.TimestampColumn("timestamp")
    private static class TestStreamStorageOnlyOff extends Record {
        @Column(name = "service_id")
        @BanyanDB.Stream.SeriesID(index = 0)
        private String serviceId;
        @Column(name = "payload")
        private String payload;
        @Column(name = "timestamp")
        private long timestamp;

        @Override
        public StorageID id() {
            return new StorageID();
        }

        static class Builder implements StorageBuilder<StorageData> {
            @Override
            public StorageData storage2Entity(final Convert2Entity converter) {
                return null;
            }

            @Override
            public void entity2Storage(final StorageData entity, final Convert2Storage converter) {
            }
        }
    }

    /**
     * Non-additive variant: {@code tag} is now {@code long} (TAG_TYPE_INT) where the live
     * stream has it as {@code String} (TAG_TYPE_STRING). Boot refuses it.
     */
    @Stream(name = "testStream", scopeId = DefaultScopeDefine.SERVICE,
        builder = TestStreamNonAdditive.Builder.class, processor = RecordStreamProcessor.class)
    @BanyanDB.Stream.Group(BanyanDB.StreamGroup.RECORDS_LOG)
    @BanyanDB.Stream.TimestampColumn("timestamp")
    private static class TestStreamNonAdditive extends Record {
        @Column(name = "service_id")
        @BanyanDB.Stream.SeriesID(index = 0)
        private String serviceId;
        @Column(name = "tag")
        private long tag;
        @Column(name = "timestamp")
        private long timestamp;

        @Override
        public StorageID id() {
            return new StorageID();
        }

        static class Builder implements StorageBuilder<StorageData> {
            @Override
            public StorageData storage2Entity(final Convert2Entity converter) {
                return null;
            }

            @Override
            public void entity2Storage(final StorageData entity, final Convert2Storage converter) {
            }
        }
    }

    /**
     * Mirror of {@link UpdateTestStream} but trimmed to a purely-additive shape change
     * (only {@code new_tag} is added; {@code tag}'s index settings stay matched to
     * {@link TestStream}).
     */
    @Stream(name = "testStream", scopeId = DefaultScopeDefine.SERVICE,
        builder = TestStreamAdditive.Builder.class, processor = RecordStreamProcessor.class)
    @BanyanDB.Stream.Group(BanyanDB.StreamGroup.RECORDS_LOG)
    @BanyanDB.Stream.TimestampColumn("timestamp")
    private static class TestStreamAdditive extends Record {
        @Column(name = "service_id")
        @BanyanDB.Stream.SeriesID(index = 0)
        private String serviceId;
        @Column(name = "tag")
        @BanyanDB.Stream.MatchQuery(analyzer = BanyanDB.AnalyzerType.URL)
        private String tag;
        @Column(name = "new_tag")
        private String newTag;
        @Column(name = "timestamp")
        private long timestamp;

        @Override
        public StorageID id() {
            return new StorageID();
        }

        static class Builder implements StorageBuilder<StorageData> {
            @Override
            public StorageData storage2Entity(final Convert2Entity converter) {
                return null;
            }

            @Override
            public void entity2Storage(final StorageData entity, final Convert2Storage converter) {
            }
        }
    }

    @Stream(name = "testStream", scopeId = DefaultScopeDefine.SERVICE,
        builder = TestStream.Builder.class, processor = RecordStreamProcessor.class)
    @BanyanDB.Stream.Group(BanyanDB.StreamGroup.RECORDS_LOG)
    @BanyanDB.Stream.TimestampColumn("timestamp")
    private static class TestStream extends Record {
        @Column(name = "service_id")
        @BanyanDB.Stream.SeriesID(index = 0)
        private String serviceId;
        @Column(name = "tag")
        @BanyanDB.Stream.MatchQuery(analyzer = BanyanDB.AnalyzerType.URL)
        private String tag;
        @Column(name = "timestamp")
        private long timestamp;

        @Override
        public StorageID id() {
            return new StorageID();
        }

        static class Builder implements StorageBuilder<StorageData> {
            @Override
            public StorageData storage2Entity(final Convert2Entity converter) {
                return null;
            }

            @Override
            public void entity2Storage(final StorageData entity, final Convert2Storage converter) {

            }
        }
    }

    @Stream(name = "testStream", scopeId = DefaultScopeDefine.SERVICE,
        builder = UpdateTestStream.Builder.class, processor = RecordStreamProcessor.class)
    @BanyanDB.Stream.Group(BanyanDB.StreamGroup.RECORDS_LOG)
    @BanyanDB.Stream.TimestampColumn("timestamp")
    private static class UpdateTestStream extends Record {
        @Column(name = "service_id")
        @BanyanDB.Stream.SeriesID(index = 0)
        private String serviceId;
        @Column(name = "tag")
        @BanyanDB.Stream.EnableSort
        private String tag;
        @Column(name = "new_tag")
        private String newTag;
        @Column(name = "timestamp")
        private long timestamp;

        @Override
        public StorageID id() {
            return new StorageID();
        }

        static class Builder implements StorageBuilder<StorageData> {
            @Override
            public StorageData storage2Entity(final Convert2Entity converter) {
                return null;
            }

            @Override
            public void entity2Storage(final StorageData entity, final Convert2Storage converter) {

            }
        }
    }

    @Stream(name = "testMetric", scopeId = DefaultScopeDefine.SERVICE,
        builder = TestMetric.Builder.class, processor = MetricsStreamProcessor.class)
    private static class TestMetric {
        @Column(name = "service_id")
        @BanyanDB.Measure.SeriesID(index = 0)
        @BanyanDB.Measure.ShardingKey(index = 0)
        private String serviceId;
        @Column(name = "tag")
        @BanyanDB.Measure.MatchQuery(analyzer = BanyanDB.AnalyzerType.URL)
        private String tag;
        @Column(name = "value", dataType = Column.ValueDataType.COMMON_VALUE)
        @BanyanDB.Measure.Field
        private long value;

        static class Builder implements StorageBuilder<StorageData> {
            @Override
            public StorageData storage2Entity(final Convert2Entity converter) {
                return null;
            }

            @Override
            public void entity2Storage(final StorageData entity, final Convert2Storage converter) {

            }
        }
    }

    @Stream(name = "testMetric", scopeId = DefaultScopeDefine.SERVICE,
        builder = UpdateTestMetric.Builder.class, processor = MetricsStreamProcessor.class)
    private static class UpdateTestMetric {
        @Column(name = "service_id")
        @BanyanDB.Measure.SeriesID(index = 0)
        private String serviceId;
        @Column(name = "tag")
        @BanyanDB.Measure.EnableSort
        private String tag;
        @Column(name = "new_tag")
        private String newTag;
        @Column(name = "value", dataType = Column.ValueDataType.COMMON_VALUE)
        @BanyanDB.Measure.Field
        private long value;
        @Column(name = "new_value", storageOnly = true)
        @BanyanDB.Measure.Field
        private long newValue;

        static class Builder implements StorageBuilder<StorageData> {
            @Override
            public StorageData storage2Entity(final Convert2Entity converter) {
                return null;
            }

            @Override
            public void entity2Storage(final StorageData entity, final Convert2Storage converter) {

            }
        }
    }

    /**
     * {@link TestMetric} plus one tag and one field: an additive measure change.
     */
    @Stream(name = "testMetric", scopeId = DefaultScopeDefine.SERVICE,
        builder = TestMetricAdditive.Builder.class, processor = MetricsStreamProcessor.class)
    private static class TestMetricAdditive {
        @Column(name = "service_id")
        @BanyanDB.Measure.SeriesID(index = 0)
        @BanyanDB.Measure.ShardingKey(index = 0)
        private String serviceId;
        @Column(name = "tag")
        @BanyanDB.Measure.MatchQuery(analyzer = BanyanDB.AnalyzerType.URL)
        private String tag;
        @Column(name = "new_tag")
        private String newTag;
        @Column(name = "value", dataType = Column.ValueDataType.COMMON_VALUE)
        @BanyanDB.Measure.Field
        private long value;
        @Column(name = "new_value")
        @BanyanDB.Measure.Field
        private long newValue;

        static class Builder implements StorageBuilder<StorageData> {
            @Override
            public StorageData storage2Entity(final Convert2Entity converter) {
                return null;
            }

            @Override
            public void entity2Storage(final StorageData entity, final Convert2Storage converter) {

            }
        }
    }

    /**
     * {@link TestMetric} with {@code value} stored as a double: a breaking measure change (the field's type).
     */
    @Stream(name = "testMetric", scopeId = DefaultScopeDefine.SERVICE,
        builder = TestMetricRetyped.Builder.class, processor = MetricsStreamProcessor.class)
    private static class TestMetricRetyped {
        @Column(name = "service_id")
        @BanyanDB.Measure.SeriesID(index = 0)
        @BanyanDB.Measure.ShardingKey(index = 0)
        private String serviceId;
        @Column(name = "tag")
        @BanyanDB.Measure.MatchQuery(analyzer = BanyanDB.AnalyzerType.URL)
        private String tag;
        @Column(name = "value", dataType = Column.ValueDataType.COMMON_VALUE)
        @BanyanDB.Measure.Field
        private double value;

        static class Builder implements StorageBuilder<StorageData> {
            @Override
            public StorageData storage2Entity(final Convert2Entity converter) {
                return null;
            }

            @Override
            public void entity2Storage(final StorageData entity, final Convert2Storage converter) {

            }
        }
    }

    @Stream(name = "testTrace", scopeId = DefaultScopeDefine.SERVICE,
        builder = TestTrace.Builder.class, processor = RecordStreamProcessor.class)
    @BanyanDB.Trace.Group(BanyanDB.TraceGroup.TRACE)
    @BanyanDB.Trace.TimestampColumn("timestamp")
    @BanyanDB.Trace.TraceIdColumn("trace_id")
    @BanyanDB.Trace.SpanIdColumn("span_id")
    @BanyanDB.Trace.IndexRule(name = "timestamp", columns = {"service_id"}, orderByColumn = "timestamp")
    private static class TestTrace extends Record {
        @Column(name = "trace_id")
        private String traceId;
        @Column(name = "span_id")
        private String spanId;
        @Column(name = "service_id")
        private String serviceId;
        @Column(name = "timestamp")
        private long timestamp;

        @Override
        public StorageID id() {
            return new StorageID();
        }

        static class Builder implements StorageBuilder<StorageData> {
            @Override
            public StorageData storage2Entity(final Convert2Entity converter) {
                return null;
            }

            @Override
            public void entity2Storage(final StorageData entity, final Convert2Storage converter) {

            }
        }
    }

    /**
     * {@link TestTrace} plus one tag: an additive trace change.
     */
    @Stream(name = "testTrace", scopeId = DefaultScopeDefine.SERVICE,
        builder = TestTraceAdditive.Builder.class, processor = RecordStreamProcessor.class)
    @BanyanDB.Trace.Group(BanyanDB.TraceGroup.TRACE)
    @BanyanDB.Trace.TimestampColumn("timestamp")
    @BanyanDB.Trace.TraceIdColumn("trace_id")
    @BanyanDB.Trace.SpanIdColumn("span_id")
    @BanyanDB.Trace.IndexRule(name = "timestamp", columns = {"service_id"}, orderByColumn = "timestamp")
    private static class TestTraceAdditive extends Record {
        @Column(name = "trace_id")
        private String traceId;
        @Column(name = "span_id")
        private String spanId;
        @Column(name = "service_id")
        private String serviceId;
        @Column(name = "endpoint_id")
        private String endpointId;
        @Column(name = "timestamp")
        private long timestamp;

        @Override
        public StorageID id() {
            return new StorageID();
        }

        static class Builder implements StorageBuilder<StorageData> {
            @Override
            public StorageData storage2Entity(final Convert2Entity converter) {
                return null;
            }

            @Override
            public void entity2Storage(final StorageData entity, final Convert2Storage converter) {

            }
        }
    }

    /**
     * A second measure of {@link TestMetric}'s group, sharing its {@code tag} index rule.
     */
    @Stream(name = "testMetricShared", scopeId = DefaultScopeDefine.SERVICE,
        builder = TestMetricShared.Builder.class, processor = MetricsStreamProcessor.class)
    private static class TestMetricShared {
        @Column(name = "service_id")
        @BanyanDB.Measure.SeriesID(index = 0)
        @BanyanDB.Measure.ShardingKey(index = 0)
        private String serviceId;
        @Column(name = "tag")
        @BanyanDB.Measure.MatchQuery(analyzer = BanyanDB.AnalyzerType.URL)
        private String tag;
        @Column(name = "value", dataType = Column.ValueDataType.COMMON_VALUE)
        @BanyanDB.Measure.Field
        private long value;

        static class Builder implements StorageBuilder<StorageData> {
            @Override
            public StorageData storage2Entity(final Convert2Entity converter) {
                return null;
            }

            @Override
            public void entity2Storage(final StorageData entity, final Convert2Storage converter) {

            }
        }
    }

    /**
     * {@link TestMetricShared} plus {@code new_tag}, whose index rule comes after the shared {@code tag}.
     */
    @Stream(name = "testMetricShared", scopeId = DefaultScopeDefine.SERVICE,
        builder = TestMetricSharedAdditive.Builder.class, processor = MetricsStreamProcessor.class)
    private static class TestMetricSharedAdditive {
        @Column(name = "service_id")
        @BanyanDB.Measure.SeriesID(index = 0)
        @BanyanDB.Measure.ShardingKey(index = 0)
        private String serviceId;
        @Column(name = "tag")
        @BanyanDB.Measure.MatchQuery(analyzer = BanyanDB.AnalyzerType.URL)
        private String tag;
        @Column(name = "new_tag")
        private String newTag;
        @Column(name = "value", dataType = Column.ValueDataType.COMMON_VALUE)
        @BanyanDB.Measure.Field
        private long value;

        static class Builder implements StorageBuilder<StorageData> {
            @Override
            public StorageData storage2Entity(final Convert2Entity converter) {
                return null;
            }

            @Override
            public void entity2Storage(final StorageData entity, final Convert2Storage converter) {

            }
        }
    }
}
