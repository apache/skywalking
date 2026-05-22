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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.library.banyandb.v1.client.DataPoint;
import org.apache.skywalking.library.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.library.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.library.banyandb.v1.client.MeasureWrite;
import org.apache.skywalking.library.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.library.banyandb.v1.client.TimestampRange;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
                                 StorageManipulationOpt.withSchemaChange()
        );
        BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        installer.isExists(model, StorageManipulationOpt.withSchemaChange());
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
        assertEquals("storage-only", measure.getTagFamilies(0).getName());
        assertEquals("service_id", measure.getTagFamilies(0).getTags(0).getName());
        assertEquals(BanyandbDatabase.TagType.TAG_TYPE_STRING, measure.getTagFamilies(0).getTags(0).getType());
        assertEquals("searchable", measure.getTagFamilies(1).getName());
        assertEquals("tag", measure.getTagFamilies(1).getTags(0).getName());
        assertEquals(BanyandbDatabase.TagType.TAG_TYPE_STRING, measure.getTagFamilies(1).getTags(0).getType());
        assertEquals("service_id", measure.getEntity().getTagNames(0));
        assertEquals("value", measure.getFields(0).getName());
        assertEquals(BanyandbDatabase.FieldType.FIELD_TYPE_INT, measure.getFields(0).getFieldType());
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
        assertEquals("url", indexRuleTag.getAnalyzer());
        assertTrue(indexRuleTag.getNoSort());
        //test IndexRuleBinding install
        BanyandbDatabase.IndexRuleBinding indexRuleBinding = client.client.findIndexRuleBinding(
            groupName, "testMetric_minute");
        assertEquals("tag", indexRuleBinding.getRules(0));
        assertEquals("testMetric_minute", indexRuleBinding.getSubject().getName());
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

        MeasureQuery query = new MeasureQuery(Lists.newArrayList(groupName), "testMetric_minute",
                                              new TimestampRange(
                                                  begin.toEpochMilli(),
                                                  now.plus(1, ChronoUnit.MINUTES).toEpochMilli()
                                              ), ImmutableMap.of("service_id", "storage-only", "tag", "searchable"),
                                              ImmutableSet.of("value")
        );
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            MeasureQueryResponse resp = client.query(query);
            assertNotNull(resp);
            assertEquals(1, resp.getDataPoints().size());
            assertEquals("service1", resp.getDataPoints().get(0).getTagValue("service_id"));
            assertEquals("tag1", resp.getDataPoints().get(0).getTagValue("tag"));
            assertEquals(100, (Long) resp.getDataPoints().get(0).getFieldValue("value"));
        });

        // StorageModels.add now dedupes by (name, downsampling); evict the original
        // registration so the UpdateTestMetric registration takes effect.
        models.remove(TestMetric.class, StorageManipulationOpt.withSchemaChange());
        Model updatedModel = models.add(UpdateTestMetric.class, DefaultScopeDefine.SERVICE,
                                        new Storage("testMetric", true, DownSampling.Minute),
                                        StorageManipulationOpt.withSchemaChange()
        );
        config.getMetricsMin().setShardNum(config.getMetricsMin().getShardNum() + 1);
        config.getMetricsMin().setSegmentInterval(config.getMetricsMin().getSegmentInterval() + 2);
        config.getMetricsMin().setTtl(config.getMetricsMin().getTtl() + 3);
        BanyanDBIndexInstaller newInstaller = new BanyanDBIndexInstaller(client, moduleManager, config);
        newInstaller.isExists(updatedModel, StorageManipulationOpt.withSchemaChange());
        //test Group update — assert the live group now reflects the values we set on config,
        //rather than hard-coding the post-mutation numbers (which would couple the test to the
        //defaults shipped in bydb.yml).
        BanyandbCommon.Group updatedGroup = client.client.findGroup(groupName);
        assertEquals(config.getMetricsMin().getShardNum(), updatedGroup.getResourceOpts().getShardNum());
        assertEquals(config.getMetricsMin().getSegmentInterval(), updatedGroup.getResourceOpts().getSegmentInterval().getNum());
        assertEquals(config.getMetricsMin().getTtl(), updatedGroup.getResourceOpts().getTtl().getNum());
        //test Measure update
        BanyandbDatabase.Measure updatedMeasure = client.client.findMeasure(groupName, "testMetric_minute");
        assertEquals("storage-only", updatedMeasure.getTagFamilies(0).getName());
        assertEquals("service_id", updatedMeasure.getTagFamilies(0).getTags(0).getName());
        assertEquals(BanyandbDatabase.TagType.TAG_TYPE_STRING, updatedMeasure.getTagFamilies(0).getTags(0).getType());
        assertEquals("searchable", updatedMeasure.getTagFamilies(1).getName());
        assertEquals("tag", updatedMeasure.getTagFamilies(1).getTags(0).getName());
        assertEquals("new_tag", updatedMeasure.getTagFamilies(1).getTags(1).getName());
        assertEquals(BanyandbDatabase.TagType.TAG_TYPE_STRING, updatedMeasure.getTagFamilies(1).getTags(0).getType());
        assertEquals(BanyandbDatabase.TagType.TAG_TYPE_STRING, updatedMeasure.getTagFamilies(1).getTags(1).getType());
        assertEquals("service_id", updatedMeasure.getEntity().getTagNames(0));
        assertEquals("value", updatedMeasure.getFields(0).getName());
        assertEquals(BanyandbDatabase.FieldType.FIELD_TYPE_INT, updatedMeasure.getFields(0).getFieldType());
        assertEquals("new_value", updatedMeasure.getFields(1).getName());
        assertEquals(BanyandbDatabase.FieldType.FIELD_TYPE_INT, updatedMeasure.getFields(1).getFieldType());
        //test IndexRule update
        BanyandbDatabase.IndexRule updatedIndexRuleTag = client.client.findIndexRule(groupName, "tag");
        assertEquals("", updatedIndexRuleTag.getAnalyzer());
        assertFalse(updatedIndexRuleTag.getNoSort());
        BanyandbDatabase.IndexRule updatedIndexRuleNewTag = client.client.findIndexRule(groupName, "new_tag");
        assertTrue(updatedIndexRuleNewTag.getNoSort());
        //test IndexRuleBinding update
        BanyandbDatabase.IndexRuleBinding updatedIndexRuleBinding = client.client.findIndexRuleBinding(
            groupName, "testMetric_minute");
        assertEquals("tag", updatedIndexRuleBinding.getRules(0));
        assertEquals("new_tag", updatedIndexRuleBinding.getRules(1));
        assertEquals("testMetric_minute", updatedIndexRuleBinding.getSubject().getName());
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
        MeasureQuery updatedQuery = new MeasureQuery(
            Lists.newArrayList(groupName), "testMetric_minute",
            new TimestampRange(begin.toEpochMilli(), now.plus(15, ChronoUnit.MINUTES).toEpochMilli()),
            ImmutableMap.of("service_id", "storage-only", "tag", "searchable", "new_tag", "searchable"),
                                                     ImmutableSet.of("value", "new_value")
        );
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            MeasureQueryResponse updatedResp = client.query(updatedQuery);
            assertNotNull(updatedResp);
            assertEquals(2, updatedResp.getDataPoints().size());
            // Index by service_id so the assertions don't depend on server-side ordering
            // (MeasureQuery doesn't set an orderBy and BanyanDB result order is not contractually stable).
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
                                 StorageManipulationOpt.withSchemaChange()
        );
        BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        installer.isExists(model, StorageManipulationOpt.withSchemaChange());
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
        assertEquals("storage-only", stream.getTagFamilies(0).getName());
        assertEquals("service_id", stream.getTagFamilies(0).getTags(0).getName());
        assertEquals(BanyandbDatabase.TagType.TAG_TYPE_STRING, stream.getTagFamilies(0).getTags(0).getType());
        assertEquals("timestamp", stream.getTagFamilies(0).getTags(1).getName());
        assertEquals(BanyandbDatabase.TagType.TAG_TYPE_INT, stream.getTagFamilies(0).getTags(1).getType());
        assertEquals("searchable", stream.getTagFamilies(1).getName());
        assertEquals("tag", stream.getTagFamilies(1).getTags(0).getName());
        assertEquals(BanyandbDatabase.TagType.TAG_TYPE_STRING, stream.getTagFamilies(1).getTags(0).getType());
        assertEquals("service_id", stream.getEntity().getTagNames(0));
        // test IndexRule install
        BanyandbDatabase.IndexRule indexRuleTag = client.client.findIndexRule(groupName, "tag");
        assertEquals("url", indexRuleTag.getAnalyzer());
        assertTrue(indexRuleTag.getNoSort());
        // test IndexRuleBinding install
        BanyandbDatabase.IndexRuleBinding indexRuleBinding = client.client.findIndexRuleBinding(
            groupName, "testStream");
        assertEquals("tag", indexRuleBinding.getRules(0));
        assertEquals("testStream", indexRuleBinding.getSubject().getName());

        // StorageModels.add now dedupes by (name, downsampling); evict the original
        // registration so the UpdateTestStream registration takes effect.
        models.remove(TestStream.class, StorageManipulationOpt.withSchemaChange());
        Model updatedModel = models.add(UpdateTestStream.class, DefaultScopeDefine.SERVICE,
                                        new Storage("testStream", true, DownSampling.Second),
                                        StorageManipulationOpt.withSchemaChange()
        );
        BanyanDBIndexInstaller newInstaller = new BanyanDBIndexInstaller(client, moduleManager, config);
        newInstaller.isExists(updatedModel, StorageManipulationOpt.withSchemaChange());
        // test Stream update
        BanyandbDatabase.Stream updatedStream = client.client.findStream(groupName, "testStream");
        assertEquals("storage-only", updatedStream.getTagFamilies(0).getName());
        assertEquals("service_id", updatedStream.getTagFamilies(0).getTags(0).getName());
        assertEquals(BanyandbDatabase.TagType.TAG_TYPE_STRING, updatedStream.getTagFamilies(0).getTags(0).getType());
        assertEquals("timestamp", updatedStream.getTagFamilies(0).getTags(1).getName());
        assertEquals(BanyandbDatabase.TagType.TAG_TYPE_INT, updatedStream.getTagFamilies(0).getTags(1).getType());
        assertEquals("searchable", updatedStream.getTagFamilies(1).getName());
        assertEquals("tag", updatedStream.getTagFamilies(1).getTags(0).getName());
        assertEquals("new_tag", updatedStream.getTagFamilies(1).getTags(1).getName());
        assertEquals(BanyandbDatabase.TagType.TAG_TYPE_STRING, updatedStream.getTagFamilies(1).getTags(0).getType());
        assertEquals(BanyandbDatabase.TagType.TAG_TYPE_STRING, updatedStream.getTagFamilies(1).getTags(1).getType());
        assertEquals("service_id", updatedStream.getEntity().getTagNames(0));
        // test IndexRule update
        BanyandbDatabase.IndexRule updatedIndexRuleTag = client.client.findIndexRule(groupName, "tag");
        assertEquals("", updatedIndexRuleTag.getAnalyzer());
        assertFalse(updatedIndexRuleTag.getNoSort());
        BanyandbDatabase.IndexRule updatedIndexRuleNewTag = client.client.findIndexRule(groupName, "new_tag");
        assertTrue(updatedIndexRuleNewTag.getNoSort());
        // test IndexRuleBinding update
        BanyandbDatabase.IndexRuleBinding updatedIndexRuleBinding = client.client.findIndexRuleBinding(
            groupName, "testStream");
        assertEquals("tag", updatedIndexRuleBinding.getRules(0));
        assertEquals("new_tag", updatedIndexRuleBinding.getRules(1));
        assertEquals("testStream", updatedIndexRuleBinding.getSubject().getName());
    }

    /**
     * Drive the allowBootReshape path: install {@link TestStream} first under
     * {@link StorageManipulationOpt#withSchemaChange()} so the stream lives on the backend
     * with the original shape, then re-install a class that adds {@code new_tag} and opts in
     * via {@code allowBootReshape = true} under {@link StorageManipulationOpt#schemaCreateIfAbsent()}.
     * The installer should apply the additive update during boot rather than recording
     * {@link StorageManipulationOpt.Outcome#SKIPPED_SHAPE_MISMATCH}.
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
                                     StorageManipulationOpt.withSchemaChange());
        BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        installer.isExists(baseModel, StorageManipulationOpt.withSchemaChange());
        installer.createTable(baseModel);

        String groupName = MetadataRegistry.convertGroupName(
            config.getGlobal().getNamespace(), BanyanDB.StreamGroup.RECORDS_LOG.getName());
        BanyandbDatabase.Stream initial = client.client.findStream(groupName, "testStream");
        assertEquals(1, initial.getTagFamilies(1).getTagsCount());

        models.remove(TestStream.class, StorageManipulationOpt.withSchemaChange());
        Model reshapedModel = models.add(TestStreamAdditiveReshapeOn.class, DefaultScopeDefine.SERVICE,
                                         new Storage("testStream", true, DownSampling.Second),
                                         StorageManipulationOpt.withSchemaChange());
        assertTrue(reshapedModel.isAllowBootReshape());

        StorageManipulationOpt bootOpt = StorageManipulationOpt.schemaCreateIfAbsent();
        new BanyanDBIndexInstaller(client, moduleManager, config).isExists(reshapedModel, bootOpt);

        BanyandbDatabase.Stream reshaped = client.client.findStream(groupName, "testStream");
        assertEquals(2, reshaped.getTagFamilies(1).getTagsCount());
        assertEquals("new_tag", reshaped.getTagFamilies(1).getTags(1).getName());
        assertEquals(BanyandbDatabase.TagType.TAG_TYPE_STRING, reshaped.getTagFamilies(1).getTags(1).getType());

        boolean updatedRecorded = bootOpt.getOutcomes().stream()
            .anyMatch(o -> "stream".equals(o.getResourceType())
                && "testStream".equals(o.getResourceName())
                && o.getStatus() == StorageManipulationOpt.Outcome.UPDATED);
        assertTrue(updatedRecorded, "expected UPDATED outcome for additive boot reshape, got " + bootOpt.getOutcomes());
    }

    /**
     * Same setup as {@link #testStreamAdditiveBootReshape} but the re-install class leaves
     * {@code allowBootReshape} at its default ({@code false}). Boot must refuse to reshape
     * even though the diff is additive — record {@link StorageManipulationOpt.Outcome#SKIPPED_SHAPE_MISMATCH}
     * and leave the live stream untouched.
     */
    @Test
    public void testStreamAdditiveBootReshape_optOutSkips() throws Exception {
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
                                     StorageManipulationOpt.withSchemaChange());
        BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        installer.isExists(baseModel, StorageManipulationOpt.withSchemaChange());
        installer.createTable(baseModel);

        models.remove(TestStream.class, StorageManipulationOpt.withSchemaChange());
        Model reshapedModel = models.add(TestStreamAdditiveReshapeOff.class, DefaultScopeDefine.SERVICE,
                                         new Storage("testStream", true, DownSampling.Second),
                                         StorageManipulationOpt.withSchemaChange());
        assertFalse(reshapedModel.isAllowBootReshape());

        StorageManipulationOpt bootOpt = StorageManipulationOpt.schemaCreateIfAbsent();
        new BanyanDBIndexInstaller(client, moduleManager, config).isExists(reshapedModel, bootOpt);

        String groupName = MetadataRegistry.convertGroupName(
            config.getGlobal().getNamespace(), BanyanDB.StreamGroup.RECORDS_LOG.getName());
        BanyandbDatabase.Stream live = client.client.findStream(groupName, "testStream");
        assertEquals(1, live.getTagFamilies(1).getTagsCount());

        boolean skipRecorded = bootOpt.getOutcomes().stream()
            .anyMatch(o -> "stream".equals(o.getResourceType())
                && "testStream".equals(o.getResourceName())
                && o.getStatus() == StorageManipulationOpt.Outcome.SKIPPED_SHAPE_MISMATCH);
        assertTrue(skipRecorded, "expected SKIPPED_SHAPE_MISMATCH without allowBootReshape opt-in, got " + bootOpt.getOutcomes());
    }

    /**
     * Measure variant of {@link #testStreamAdditiveBootReshape}: install {@link TestMetric},
     * then re-install {@link TestMetricAdditiveReshapeOn} which adds {@code new_tag} and
     * {@code new_value} (a field) and opts in via {@code allowBootReshape = true}. The boot
     * installer should apply the additive update and record
     * {@link StorageManipulationOpt.Outcome#UPDATED}.
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
                                     StorageManipulationOpt.withSchemaChange());
        BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        installer.isExists(baseModel, StorageManipulationOpt.withSchemaChange());
        installer.createTable(baseModel);

        models.remove(TestMetric.class, StorageManipulationOpt.withSchemaChange());
        Model reshapedModel = models.add(TestMetricAdditiveReshapeOn.class, DefaultScopeDefine.SERVICE,
                                         new Storage("testMetric", true, DownSampling.Minute),
                                         StorageManipulationOpt.withSchemaChange());
        assertTrue(reshapedModel.isAllowBootReshape());

        StorageManipulationOpt bootOpt = StorageManipulationOpt.schemaCreateIfAbsent();
        new BanyanDBIndexInstaller(client, moduleManager, config).isExists(reshapedModel, bootOpt);

        String groupName = MetadataRegistry.convertGroupName(
            config.getGlobal().getNamespace(), BanyanDB.MeasureGroup.METRICS_MINUTE.getName());
        BanyandbDatabase.Measure reshaped = client.client.findMeasure(groupName, "testMetric_minute");
        assertEquals(2, reshaped.getTagFamilies(1).getTagsCount());
        assertEquals("new_tag", reshaped.getTagFamilies(1).getTags(1).getName());
        assertEquals(2, reshaped.getFieldsCount());
        assertEquals("new_value", reshaped.getFields(1).getName());

        boolean updatedRecorded = bootOpt.getOutcomes().stream()
            .anyMatch(o -> "measure".equals(o.getResourceType())
                && "testMetric_minute".equals(o.getResourceName())
                && o.getStatus() == StorageManipulationOpt.Outcome.UPDATED);
        assertTrue(updatedRecorded, "expected UPDATED outcome for additive measure boot reshape, got " + bootOpt.getOutcomes());
    }

    /**
     * Opt-in is necessary but not sufficient: even with {@code allowBootReshape = true} a
     * diff that is not purely additive (here, the {@code tag} column flips from
     * {@code String} → {@code long}, i.e. {@code TAG_TYPE_STRING} → {@code TAG_TYPE_INT})
     * must be refused at boot. {@link StorageManipulationOpt.Outcome#SKIPPED_SHAPE_MISMATCH}
     * is recorded and the live stream stays unchanged, forcing the operator to do an
     * explicit drop+recreate (the only safe path for identity-breaking changes).
     */
    @Test
    public void testStreamNonAdditiveBootReshape_optInStillSkips() throws Exception {
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
                                     StorageManipulationOpt.withSchemaChange());
        BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        installer.isExists(baseModel, StorageManipulationOpt.withSchemaChange());
        installer.createTable(baseModel);

        models.remove(TestStream.class, StorageManipulationOpt.withSchemaChange());
        Model reshapedModel = models.add(TestStreamNonAdditiveReshapeOn.class, DefaultScopeDefine.SERVICE,
                                         new Storage("testStream", true, DownSampling.Second),
                                         StorageManipulationOpt.withSchemaChange());
        assertTrue(reshapedModel.isAllowBootReshape());

        StorageManipulationOpt bootOpt = StorageManipulationOpt.schemaCreateIfAbsent();
        new BanyanDBIndexInstaller(client, moduleManager, config).isExists(reshapedModel, bootOpt);

        String groupName = MetadataRegistry.convertGroupName(
            config.getGlobal().getNamespace(), BanyanDB.StreamGroup.RECORDS_LOG.getName());
        BanyandbDatabase.Stream live = client.client.findStream(groupName, "testStream");
        assertEquals("tag", live.getTagFamilies(1).getTags(0).getName());
        assertEquals(BanyandbDatabase.TagType.TAG_TYPE_STRING, live.getTagFamilies(1).getTags(0).getType());

        boolean skipRecorded = bootOpt.getOutcomes().stream()
            .anyMatch(o -> "stream".equals(o.getResourceType())
                && "testStream".equals(o.getResourceName())
                && o.getStatus() == StorageManipulationOpt.Outcome.SKIPPED_SHAPE_MISMATCH);
        assertTrue(skipRecorded,
            "expected SKIPPED_SHAPE_MISMATCH for non-additive (type-change) diff even with opt-in, got " + bootOpt.getOutcomes());
    }

    /**
     * Toggling {@code storageOnly} on an existing {@code @Column} moves the tag from
     * {@code storage-only} → {@code searchable} (or vice versa). Although the live tag
     * family no longer contains the tag at its old position, the tag identity + type are
     * preserved, so {@link BanyanDBIndexInstaller#isPurelyAdditiveStream} (via
     * {@code isPurelyAdditiveTagFamilies}) should accept the relocation when
     * {@code allowBootReshape = true} and the OAP is in the init / standalone path. The
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
                                     StorageManipulationOpt.withSchemaChange());
        BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        installer.isExists(baseModel, StorageManipulationOpt.withSchemaChange());
        installer.createTable(baseModel);

        String groupName = MetadataRegistry.convertGroupName(
            config.getGlobal().getNamespace(), BanyanDB.StreamGroup.RECORDS_LOG.getName());
        BanyandbDatabase.Stream initial = client.client.findStream(groupName, "relocStream");
        // payload starts in storage-only family
        assertTrue(initial.getTagFamiliesList().stream()
                .filter(f -> "storage-only".equals(f.getName()))
                .flatMap(f -> f.getTagsList().stream())
                .anyMatch(t -> "payload".equals(t.getName())),
            "expected payload tag in storage-only family initially, got " + initial);

        models.remove(TestStreamStorageOnly.class, StorageManipulationOpt.withSchemaChange());
        Model reshapedModel = models.add(TestStreamStorageOnlyOff.class, DefaultScopeDefine.SERVICE,
                                         new Storage("relocStream", true, DownSampling.Second),
                                         StorageManipulationOpt.withSchemaChange());
        assertTrue(reshapedModel.isAllowBootReshape());

        StorageManipulationOpt bootOpt = StorageManipulationOpt.schemaCreateIfAbsent();
        new BanyanDBIndexInstaller(client, moduleManager, config).isExists(reshapedModel, bootOpt);

        BanyandbDatabase.Stream reshaped = client.client.findStream(groupName, "relocStream");
        // payload is now in searchable family
        assertTrue(reshaped.getTagFamiliesList().stream()
                .filter(f -> "searchable".equals(f.getName()))
                .flatMap(f -> f.getTagsList().stream())
                .anyMatch(t -> "payload".equals(t.getName())),
            "expected payload tag relocated to searchable family after reshape, got " + reshaped);
        assertFalse(reshaped.getTagFamiliesList().stream()
                .filter(f -> "storage-only".equals(f.getName()))
                .flatMap(f -> f.getTagsList().stream())
                .anyMatch(t -> "payload".equals(t.getName())),
            "expected payload tag no longer in storage-only family after reshape, got " + reshaped);

        boolean updatedRecorded = bootOpt.getOutcomes().stream()
            .anyMatch(o -> "stream".equals(o.getResourceType())
                && "relocStream".equals(o.getResourceName())
                && o.getStatus() == StorageManipulationOpt.Outcome.UPDATED);
        assertTrue(updatedRecorded, "expected UPDATED outcome for storageOnly relocation, got " + bootOpt.getOutcomes());

        // Relocation is "additive" → checkStream returns true → dependent index-rule
        // reconciliation runs. Verify the IndexRule for the newly-indexed `payload` tag was
        // created and that the IndexRuleBinding now references it. This is the behavior the
        // dependent-reconcile gate is supposed to permit when the primary shape change is
        // accepted.
        BanyandbDatabase.IndexRule payloadIndexRule = client.client.findIndexRule(groupName, "payload");
        assertNotNull(payloadIndexRule, "expected IndexRule 'payload' to be created after relocation");
        BanyandbDatabase.IndexRuleBinding binding = client.client.findIndexRuleBinding(groupName, "relocStream");
        assertNotNull(binding, "expected IndexRuleBinding for relocStream to be present after relocation");
        assertTrue(binding.getRulesList().contains("payload"),
            "expected IndexRuleBinding to reference 'payload', got rules=" + binding.getRulesList());
    }

    /**
     * Initial state for {@link #testStreamStorageOnlyTogglePathBootReshape}: {@code payload}
     * declared with {@code storageOnly = true}, so it lands in the {@code storage-only}
     * tag family.
     */
    @Stream(name = "relocStream", scopeId = DefaultScopeDefine.SERVICE,
        builder = TestStreamStorageOnly.Builder.class, processor = RecordStreamProcessor.class)
    @BanyanDB.Group(streamGroup = BanyanDB.StreamGroup.RECORDS_LOG)
    @BanyanDB.TimestampColumn("timestamp")
    private static class TestStreamStorageOnly extends Record {
        @Column(name = "service_id")
        @BanyanDB.SeriesID(index = 0)
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
     * tag relocates to the {@code searchable} family. Opted in via
     * {@code allowBootReshape = true}.
     */
    @Stream(name = "relocStream", scopeId = DefaultScopeDefine.SERVICE,
        builder = TestStreamStorageOnlyOff.Builder.class, processor = RecordStreamProcessor.class,
        allowBootReshape = true)
    @BanyanDB.Group(streamGroup = BanyanDB.StreamGroup.RECORDS_LOG)
    @BanyanDB.TimestampColumn("timestamp")
    private static class TestStreamStorageOnlyOff extends Record {
        @Column(name = "service_id")
        @BanyanDB.SeriesID(index = 0)
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
     * stream has it as {@code String} (TAG_TYPE_STRING). Boot must refuse to reshape even
     * with {@code allowBootReshape = true}.
     */
    @Stream(name = "testStream", scopeId = DefaultScopeDefine.SERVICE,
        builder = TestStreamNonAdditiveReshapeOn.Builder.class, processor = RecordStreamProcessor.class,
        allowBootReshape = true)
    @BanyanDB.Group(streamGroup = BanyanDB.StreamGroup.RECORDS_LOG)
    @BanyanDB.TimestampColumn("timestamp")
    private static class TestStreamNonAdditiveReshapeOn extends Record {
        @Column(name = "service_id")
        @BanyanDB.SeriesID(index = 0)
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
     * {@link TestStream}) and with the new {@code allowBootReshape} opt-in flipped on.
     */
    @Stream(name = "testStream", scopeId = DefaultScopeDefine.SERVICE,
        builder = TestStreamAdditiveReshapeOn.Builder.class, processor = RecordStreamProcessor.class,
        allowBootReshape = true)
    @BanyanDB.Group(streamGroup = BanyanDB.StreamGroup.RECORDS_LOG)
    @BanyanDB.TimestampColumn("timestamp")
    private static class TestStreamAdditiveReshapeOn extends Record {
        @Column(name = "service_id")
        @BanyanDB.SeriesID(index = 0)
        private String serviceId;
        @Column(name = "tag")
        @BanyanDB.MatchQuery(analyzer = BanyanDB.MatchQuery.AnalyzerType.URL)
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

    /**
     * Same additive shape as {@link TestStreamAdditiveReshapeOn} but without the opt-in —
     * boot must refuse to reshape and record SKIPPED_SHAPE_MISMATCH.
     */
    @Stream(name = "testStream", scopeId = DefaultScopeDefine.SERVICE,
        builder = TestStreamAdditiveReshapeOff.Builder.class, processor = RecordStreamProcessor.class)
    @BanyanDB.Group(streamGroup = BanyanDB.StreamGroup.RECORDS_LOG)
    @BanyanDB.TimestampColumn("timestamp")
    private static class TestStreamAdditiveReshapeOff extends Record {
        @Column(name = "service_id")
        @BanyanDB.SeriesID(index = 0)
        private String serviceId;
        @Column(name = "tag")
        @BanyanDB.MatchQuery(analyzer = BanyanDB.MatchQuery.AnalyzerType.URL)
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

    /**
     * Mirror of {@link UpdateTestMetric} but trimmed to a purely-additive shape change
     * (new tag, new field) and with {@code allowBootReshape = true}.
     */
    @Stream(name = "testMetric", scopeId = DefaultScopeDefine.SERVICE,
        builder = TestMetricAdditiveReshapeOn.Builder.class, processor = MetricsStreamProcessor.class,
        allowBootReshape = true)
    private static class TestMetricAdditiveReshapeOn {
        @Column(name = "service_id")
        @BanyanDB.SeriesID(index = 0)
        @BanyanDB.ShardingKey(index = 0)
        private String serviceId;
        @Column(name = "tag")
        @BanyanDB.MatchQuery(analyzer = BanyanDB.MatchQuery.AnalyzerType.URL)
        private String tag;
        @Column(name = "new_tag")
        private String newTag;
        @Column(name = "value", dataType = Column.ValueDataType.COMMON_VALUE)
        @BanyanDB.MeasureField
        private long value;
        @Column(name = "new_value", storageOnly = true)
        @BanyanDB.MeasureField
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

    @Stream(name = "testStream", scopeId = DefaultScopeDefine.SERVICE,
        builder = TestStream.Builder.class, processor = RecordStreamProcessor.class)
    @BanyanDB.Group(streamGroup = BanyanDB.StreamGroup.RECORDS_LOG)
    @BanyanDB.TimestampColumn("timestamp")
    private static class TestStream extends Record {
        @Column(name = "service_id")
        @BanyanDB.SeriesID(index = 0)
        private String serviceId;
        @Column(name = "tag")
        @BanyanDB.MatchQuery(analyzer = BanyanDB.MatchQuery.AnalyzerType.URL)
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
    @BanyanDB.Group(streamGroup = BanyanDB.StreamGroup.RECORDS_LOG)
    @BanyanDB.TimestampColumn("timestamp")
    private static class UpdateTestStream extends Record {
        @Column(name = "service_id")
        @BanyanDB.SeriesID(index = 0)
        private String serviceId;
        @Column(name = "tag")
        @BanyanDB.EnableSort
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
        @BanyanDB.SeriesID(index = 0)
        @BanyanDB.ShardingKey(index = 0)
        private String serviceId;
        @Column(name = "tag")
        @BanyanDB.MatchQuery(analyzer = BanyanDB.MatchQuery.AnalyzerType.URL)
        private String tag;
        @Column(name = "value", dataType = Column.ValueDataType.COMMON_VALUE)
        @BanyanDB.MeasureField
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
        @BanyanDB.SeriesID(index = 0)
        private String serviceId;
        @Column(name = "tag")
        @BanyanDB.EnableSort
        private String tag;
        @Column(name = "new_tag")
        private String newTag;
        @Column(name = "value", dataType = Column.ValueDataType.COMMON_VALUE)
        @BanyanDB.MeasureField
        private long value;
        @Column(name = "new_value", storageOnly = true)
        @BanyanDB.MeasureField
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
}
