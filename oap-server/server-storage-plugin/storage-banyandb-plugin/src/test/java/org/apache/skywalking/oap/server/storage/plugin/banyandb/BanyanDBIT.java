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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.banyandb.v1.client.MeasureWrite;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.config.DownSamplingConfigService;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.model.Model;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;
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
        Whitebox.setInternalState(telemetryModule, "loadedProvider", telemetryProvider);
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
                                 new Storage("testMetric", true, DownSampling.Minute)
        );
        BanyanDBIndexInstaller installer = new BanyanDBIndexInstaller(client, moduleManager, config);
        installer.isExists(model);
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
        assertEquals("default", measure.getTagFamilies(0).getName());
        assertEquals("tag", measure.getTagFamilies(0).getTags(0).getName());
        assertEquals(BanyandbDatabase.TagType.TAG_TYPE_STRING, measure.getTagFamilies(0).getTags(0).getType());
        assertEquals("storage-only", measure.getTagFamilies(1).getName());
        assertEquals("service_id", measure.getTagFamilies(1).getTags(0).getName());
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
        measureWrite.tag("service_id", TagAndValue.stringTagValue("service1"))
                    .tag("tag", TagAndValue.stringTagValue("tag1"))
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
                                              ), ImmutableSet.of("service_id", "tag"),
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

        Model updatedModel = models.add(UpdateTestMetric.class, DefaultScopeDefine.SERVICE,
                                        new Storage("testMetric", true, DownSampling.Minute)
        );
        config.getMetricsMin().setShardNum(config.getMetricsMin().getShardNum() + 1);
        config.getMetricsMin().setSegmentInterval(config.getMetricsMin().getSegmentInterval() + 2);
        config.getMetricsMin().setTtl(config.getMetricsMin().getTtl() + 3);
        BanyanDBIndexInstaller newInstaller = new BanyanDBIndexInstaller(client, moduleManager, config);
        newInstaller.isExists(updatedModel);
        //test Group update
        BanyandbCommon.Group updatedGroup = client.client.findGroup(groupName);
        assertEquals(updatedGroup.getResourceOpts().getShardNum(), 3);
        assertEquals(updatedGroup.getResourceOpts().getSegmentInterval().getNum(), 3);
        assertEquals(updatedGroup.getResourceOpts().getTtl().getNum(), 10);
        //test Measure update
        BanyandbDatabase.Measure updatedMeasure = client.client.findMeasure(groupName, "testMetric_minute");
        assertEquals("default", updatedMeasure.getTagFamilies(0).getName());
        assertEquals("tag", updatedMeasure.getTagFamilies(0).getTags(0).getName());
        assertEquals("new_tag", updatedMeasure.getTagFamilies(0).getTags(1).getName());
        assertEquals(BanyandbDatabase.TagType.TAG_TYPE_STRING, updatedMeasure.getTagFamilies(0).getTags(0).getType());
        assertEquals(BanyandbDatabase.TagType.TAG_TYPE_STRING, updatedMeasure.getTagFamilies(0).getTags(1).getType());
        assertEquals("storage-only", updatedMeasure.getTagFamilies(1).getName());
        assertEquals("service_id", updatedMeasure.getTagFamilies(1).getTags(0).getName());
        assertEquals(BanyandbDatabase.TagType.TAG_TYPE_STRING, updatedMeasure.getTagFamilies(1).getTags(0).getType());
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
        client.client.updateMeasureMetadataCacheFromSever(groupName, "testMetric_minute");
        MeasureWrite updatedMeasureWrite = client.createMeasureWrite(groupName, "testMetric_minute", now.plus(10, ChronoUnit.MINUTES).toEpochMilli());
        updatedMeasureWrite.tag("service_id", TagAndValue.stringTagValue("service2"))
                           .tag("tag", TagAndValue.stringTagValue("tag1"))
                           .tag("new_tag", TagAndValue.stringTagValue("new_tag1"))
                           .field("value", TagAndValue.longFieldValue(101))
                           .field("new_value", TagAndValue.longFieldValue(1000));
        CompletableFuture<Void> cf = processor.add(updatedMeasureWrite);
        cf.exceptionally(exp -> {
            Assertions.fail(exp.getMessage());
            return null;
        });
        cf.get(10, TimeUnit.SECONDS);
        MeasureQuery updatedQuery = new MeasureQuery(Lists.newArrayList(groupName), "testMetric_minute",
                                              new TimestampRange(
                                                  begin.toEpochMilli(),
                                                  now.plus(15, ChronoUnit.MINUTES).toEpochMilli()
                                              ), ImmutableSet.of("service_id", "tag", "new_tag"),
                                              ImmutableSet.of("value", "new_value")
        );
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            MeasureQueryResponse updatedResp = client.query(updatedQuery);
            assertNotNull(updatedResp);
            assertEquals(2, updatedResp.getDataPoints().size());
            assertEquals("service1", updatedResp.getDataPoints().get(0).getTagValue("service_id"));
            assertEquals("tag1", updatedResp.getDataPoints().get(0).getTagValue("tag"));
            assertEquals(100, (Long) updatedResp.getDataPoints().get(0).getFieldValue("value"));
            assertEquals("service2", updatedResp.getDataPoints().get(1).getTagValue("service_id"));
            assertEquals("tag1", updatedResp.getDataPoints().get(1).getTagValue("tag"));
            assertEquals("new_tag1", updatedResp.getDataPoints().get(1).getTagValue("new_tag"));
            assertEquals(101, (Long) updatedResp.getDataPoints().get(1).getFieldValue("value"));
            assertEquals(1000, (Long) updatedResp.getDataPoints().get(1).getFieldValue("new_value"));
        });
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
