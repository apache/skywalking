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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteData;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.query.type.TraceBrief;
import org.apache.skywalking.oap.server.core.query.type.TraceState;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCMetadataQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCMetricsDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCRecordDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCTagAutoCompleteQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql.MySQLTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao.ShardingAggregationQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao.ShardingHistoryDeleteDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao.ShardingMetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao.ShardingTopologyQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao.ShardingTraceQueryDAO;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.powermock.reflect.Whitebox;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Slf4j
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(Parameterized.class)
@PowerMockIgnore({
    "javax.net.*",
    "javax.management.*",
    "com.sun.org.apache.xerces.*",
    "javax.xml.*", "org.xml.*",
    "javax.management.*",
    "org.w3c.*"
})
@PrepareForTest({DefaultScopeDefine.class})
public class TCITShardingSphere {
    @BeforeClass
    public static void setup() {
        PowerMockito.mockStatic(DefaultScopeDefine.class);
        PowerMockito.when(DefaultScopeDefine.nameOf(1)).thenReturn("any");
    }

    @Parameterized.Parameter
    public String version;

    @Parameterized.Parameter(1)
    public DataSourceType dsType;

    @Parameterized.Parameters(name = "version: {0}")
    public static Collection<Object[]> versions() {
        return Arrays.asList(new Object[][] {
            {
                "5.1.2",
                DataSourceType.MYSQL
            }
        });
    }

    public  DockerComposeContainer<?> environment;
    private JDBCHikariCPClient ssClient;
    private JDBCHikariCPClient dsClient0;
    private JDBCHikariCPClient dsClient1;
    private final Set<String> dataSources = new HashSet<>(Arrays.asList("ds_0", "ds_1"));
    private final int ttlTestCreate = 3;
    private final int ttlTestDrop = 2;
    private final String searchableTag = "http.method";
    private final long timeBucketSec = Long.parseLong(DateTime.now().toString("yyyyMMddHHmmss"));
    private final long timeBucketMin = Long.parseLong(DateTime.now().toString("yyyyMMddHHmm"));
    private final long timeBucketDay = Long.parseLong(DateTime.now().toString("yyyyMMdd"));
    private Duration duration;
    private Entity entityA;
    private Entity entityB;
    private String serviceIdA;
    private String serviceIdB;

    private ModuleManager moduleManager;
    private MySQLTableInstaller mySQLTableInstaller;
    private ShardingSphereTableInstaller installer;
    private DurationWithinTTL durationWithinTTL = DurationWithinTTL.INSTANCE;
    private final String countQuery = "SELECT COUNT(*) AS rc FROM ";

    @Before
    public void init() {
        if (dsType.equals(DataSourceType.MYSQL)) {
            startEnv("docker-compose-mysql.yml", 3306);
            initConnection("mysql", "/swtest?rewriteBatchedStatements=true", 3306, "root", "root@1234");
        }
        initTestData();
    }

    private void startEnv(String dockerComposeName, int dsServicePort) {
        environment = new DockerComposeContainer<>(new File(TCITShardingSphere.class
                                                                .getClassLoader()
                                                                .getResource(dockerComposeName).getPath()))
            .withExposedService("sharding-proxy", 3307,
                                Wait.defaultWaitStrategy().withStartupTimeout(java.time.Duration.ofMinutes(20))
            )
            .withExposedService("data-source-0", dsServicePort,
                                Wait.defaultWaitStrategy().withStartupTimeout(java.time.Duration.ofMinutes(20))
            )
            .withExposedService("data-source-1", dsServicePort,
                                Wait.defaultWaitStrategy().withStartupTimeout(java.time.Duration.ofMinutes(20))
            )
            .withEnv("SS_VERSION", version);
        environment.start();
    }

    private void initConnection(String driverType,
                                String urlSuffix,
                                int dsServicePort,
                                String dsUserName,
                                String dsPassword) {
        String ssUrl = "jdbc:" + driverType + "://" +
            environment.getServiceHost("sharding-proxy", 3307) + ":" +
            environment.getServicePort("sharding-proxy", 3307) +
            urlSuffix;
        Properties properties = new Properties();
        properties.setProperty("jdbcUrl", ssUrl);
        properties.setProperty("dataSource.user", "root");
        properties.setProperty("dataSource.password", "root");

        String dsUrl0 = "jdbc:" + driverType + "://" +
            environment.getServiceHost("data-source-0", dsServicePort) + ":" +
            environment.getServicePort("data-source-0", dsServicePort) +
            urlSuffix;
        Properties propertiesDs0 = new Properties();
        propertiesDs0.setProperty("jdbcUrl", dsUrl0);
        propertiesDs0.setProperty("dataSource.user", dsUserName);
        propertiesDs0.setProperty("dataSource.password", "root@1234");

        String dsUrl1 = "jdbc:" + driverType + "://" +
            environment.getServiceHost("data-source-1", dsServicePort) + ":" +
            environment.getServicePort("data-source-1", dsServicePort) +
            urlSuffix;
        Properties propertiesDs1 = new Properties();
        propertiesDs1.setProperty("jdbcUrl", dsUrl1);
        propertiesDs1.setProperty("dataSource.user", dsUserName);
        propertiesDs1.setProperty("dataSource.password", dsPassword);

        ssClient = new JDBCHikariCPClient(properties);
        dsClient0 = new JDBCHikariCPClient(propertiesDs0);
        dsClient1 = new JDBCHikariCPClient(propertiesDs1);

        ssClient.connect();
        dsClient0.connect();
        dsClient1.connect();
    }

    private void initTestData() {
        moduleManager = mock(ModuleManager.class);
        ConfigService configService = mock(ConfigService.class);
        Whitebox.setInternalState(moduleManager, "isInPrepareStage", false);
        when(configService.getMetricsDataTTL()).thenReturn(ttlTestCreate);
        when(configService.getRecordDataTTL()).thenReturn(ttlTestCreate);
        when(configService.getSearchableTracesTags()).thenReturn(searchableTag);
        when(moduleManager.find(anyString())).thenReturn(mock(ModuleProviderHolder.class));
        when(moduleManager.find(CoreModule.NAME).provider()).thenReturn(mock(ModuleServiceHolder.class));
        when(moduleManager.find(CoreModule.NAME).provider().getService(ConfigService.class))
            .thenReturn(configService);
        mySQLTableInstaller = new MySQLTableInstaller(ssClient, moduleManager);
        installer = new ShardingSphereTableInstaller(ssClient, moduleManager,
                                                     dataSources,
                                                     mySQLTableInstaller
        );

        durationWithinTTL.setConfigService(configService);

        duration = new Duration();
        duration.setStart(DateTime.now().minusMinutes(15).toString("yyyy-MM-dd HHmm"));
        duration.setEnd(DateTime.now().plusMinutes(1).toString("yyyy-MM-dd HHmm"));
        duration.setStep(Step.MINUTE);

        entityA = new Entity();
        entityA.setServiceName("Service_A");
        entityA.setNormal(true);
        entityA.setScope(Scope.Service);

        entityB = new Entity();
        entityB.setServiceName("Service_B");
        entityB.setNormal(true);
        entityB.setScope(Scope.Service);

        serviceIdA = entityA.buildId();
        serviceIdB = entityB.buildId();
    }

    @SneakyThrows
    @After
    public void after() {
        environment.stop();
    }

    @SneakyThrows
    @Test
    public void test() {
        trafficTest();
        metricsTest();
        tagsTest();
        recordsTest();
        topologyTest();
    }

    // @SQLDatabase.Sharding(
    // shardingAlgorithm = ShardingAlgorithm.TIME_MIN_RANGE_SHARDING_ALGORITHM,
    // tableShardingColumn = TIME_BUCKET,
    // dataSourceShardingColumn = ID)
    @SneakyThrows
    private void trafficTest() {
        log.info("Traffic test start...");
        StorageModels models = new StorageModels();
        models.add(
            EndpointTraffic.class, DefaultScopeDefine.SERVICE,
            new Storage(EndpointTraffic.INDEX_NAME, false, DownSampling.Minute), false
        );
        Model model = models.allModels().get(0);
        TableMetaInfo.addModel(model);

        EndpointTraffic endpointTrafficA = new EndpointTraffic();
        endpointTrafficA.setName("Endpoint_A");
        endpointTrafficA.setServiceId(serviceIdA);
        endpointTrafficA.setTimeBucket(timeBucketMin);
        EndpointTraffic endpointTrafficB = new EndpointTraffic();
        endpointTrafficB.setName("Endpoint_B");
        endpointTrafficB.setServiceId(serviceIdB);
        endpointTrafficB.setTimeBucket(timeBucketMin);
        List<Metrics> metricsList = new ArrayList<>();
        metricsList.add(endpointTrafficA);
        metricsList.add(endpointTrafficB);

        createShardingRuleTest(model);
        updateShardingRuleTest(model);
        createShardingTableTest(model);

        insertMetrics(model, metricsList);
        testDataSharding(endpointTrafficA);
        ttlDropTest(model);

        //Test traffic query
        JDBCMetadataQueryDAO metadataQueryDAO = new JDBCMetadataQueryDAO(ssClient, 100);
        List<Endpoint> endpoints = metadataQueryDAO.findEndpoint("", endpointTrafficA.getServiceId(), 100);
        Assert.assertEquals(endpointTrafficA.getName(), endpoints.get(0).getName());
        log.info("Traffic test passed.");
    }

    // @SQLDatabase.Sharding(
    // shardingAlgorithm = ShardingAlgorithm.TIME_RELATIVE_ID_SHARDING_ALGORITHM,
    // tableShardingColumn = ID,
    // dataSourceShardingColumn = ENTITY_ID)
    @SneakyThrows
    private void metricsTest() {
        log.info("Metrics test start...");
        StorageModels models = new StorageModels();
        models.add(
            ServiceCpmMetrics.class, DefaultScopeDefine.SERVICE,
            new Storage(ServiceCpmMetrics.INDEX_NAME, true, DownSampling.Minute), false
        );
        Model model = models.allModels().get(0);
        TableMetaInfo.addModel(model);

        ServiceCpmMetrics serviceCpmMetricsA = new ServiceCpmMetrics();
        serviceCpmMetricsA.setEntityId(serviceIdA);
        serviceCpmMetricsA.setValue(100);
        serviceCpmMetricsA.setTotal(100);
        serviceCpmMetricsA.setTimeBucket(timeBucketMin);
        ServiceCpmMetrics serviceCpmMetricsB = new ServiceCpmMetrics();
        serviceCpmMetricsB.setEntityId(serviceIdB);
        serviceCpmMetricsB.setValue(200);
        serviceCpmMetricsB.setTotal(200);
        serviceCpmMetricsB.setTimeBucket(timeBucketMin);

        createShardingRuleTest(model);
        updateShardingRuleTest(model);
        createShardingTableTest(model);

        insertMetrics(model, Arrays.asList(serviceCpmMetricsA, serviceCpmMetricsB));
        testDataSharding(serviceCpmMetricsA);

        ttlDropTest(model);

        //Test topN
        ShardingAggregationQueryDAO aggregationQueryDAO = new ShardingAggregationQueryDAO(ssClient);
        TopNCondition topNCondition = new TopNCondition();
        topNCondition.setName(ServiceCpmMetrics.INDEX_NAME);
        topNCondition.setTopN(1);
        topNCondition.setOrder(Order.DES);

        SelectedRecord top1Record = aggregationQueryDAO.sortMetrics(topNCondition, "value", duration, null).get(0);
        Assert.assertEquals(serviceCpmMetricsB.getEntityId(), top1Record.getId());
        Assert.assertEquals("200.0000", top1Record.getValue());

        //Test metrics query
        ShardingMetricsQueryDAO metricsQueryDAO = new ShardingMetricsQueryDAO(ssClient);
        MetricsCondition metricsCondition = new MetricsCondition();

        metricsCondition.setName(ServiceCpmMetrics.INDEX_NAME);
        metricsCondition.setEntity(entityA);

        long value = metricsQueryDAO.readMetricsValue(metricsCondition, "value", duration);
        Assert.assertEquals(serviceCpmMetricsA.getValue(), value);

        MetricsValues values = metricsQueryDAO.readMetricsValues(metricsCondition, "value", duration);
        String metricsId = serviceCpmMetricsA.getTimeBucket() + "_" + serviceCpmMetricsA.getEntityId();
        Assert.assertEquals(serviceCpmMetricsA.getValue(), values.getValues().findValue(metricsId, 0));
        log.info("Metrics test passed.");
    }

    // @SQLDatabase.Sharding(shardingAlgorithm = ShardingAlgorithm.NO_SHARDING)
    @SneakyThrows
    private void tagsTest() {
        log.info("Tag auto complete data test start...");
        StorageModels models = new StorageModels();
        models.add(
            TagAutocompleteData.class, DefaultScopeDefine.SEGMENT,
            new Storage(TagAutocompleteData.INDEX_NAME, true, DownSampling.Minute), false
        );
        Model model = models.allModels().get(0);
        TableMetaInfo.addModel(model);
        installer.createTable(model);
        TagAutocompleteData tagData1 = new TagAutocompleteData();
        tagData1.setTagType(TagType.TRACE.name());
        tagData1.setTagKey(searchableTag);
        tagData1.setTagValue("GET");
        tagData1.setTimeBucket(timeBucketMin);
        TagAutocompleteData tagData2 = new TagAutocompleteData();
        tagData2.setTagType(TagType.TRACE.name());
        tagData2.setTagKey(searchableTag);
        tagData2.setTagValue("POST");
        //Should be deleted after TTL process
        tagData2.setTimeBucket(timeBucketMin);
        TagAutocompleteData tagData3 = new TagAutocompleteData();
        tagData3.setTagType(TagType.TRACE.name());
        tagData3.setTagKey(searchableTag);
        tagData3.setTagValue("HEAD");
        //Should be deleted after TTL process
        tagData3.setTimeBucket(Long.parseLong(DateTime.now().minusDays(5).toString("yyyyMMddHHmm")));
        insertMetrics(model, Arrays.asList(tagData1, tagData2, tagData3));

        //Test total count
        try (Connection ssConn = ssClient.getConnection()) {
            ResultSet rs = ssClient.executeQuery(ssConn, countQuery + TagAutocompleteData.INDEX_NAME);
            rs.next();
            Assert.assertEquals(3, rs.getInt("rc"));
        }

        // Test query
        JDBCTagAutoCompleteQueryDAO tagQueryDAO = new JDBCTagAutoCompleteQueryDAO(ssClient);
        Set<String> tagKeys = tagQueryDAO.queryTagAutocompleteKeys(TagType.TRACE, 10, duration);
        Assert.assertEquals(searchableTag, tagKeys.iterator().next());
        Set<String> tagValues = tagQueryDAO.queryTagAutocompleteValues(TagType.TRACE, searchableTag, 10, duration);
        Assert.assertEquals(2, tagValues.size());

        // Test TTL
        historyDelete(model);
        try (Connection ssConn = ssClient.getConnection()) {
            ResultSet rs = ssClient.executeQuery(ssConn, countQuery + TagAutocompleteData.INDEX_NAME);
            rs.next();
            Assert.assertEquals(2, rs.getInt("rc"));
        }
        log.info("Tag auto complete data test passed.");
    }

    // @SQLDatabase.Sharding(
    // shardingAlgorithm = ShardingAlgorithm.TIME_SEC_RANGE_SHARDING_ALGORITHM,
    // dataSourceShardingColumn = SERVICE_ID,
    // tableShardingColumn = TIME_BUCKET)
    @SneakyThrows
    private void recordsTest() {
        log.info("Records (Trace) test start...");
        StorageModels models = new StorageModels();
        models.add(
            SegmentRecord.class, DefaultScopeDefine.SEGMENT,
            new Storage(SegmentRecord.INDEX_NAME, false, DownSampling.Second), true
        );
        Model model = models.allModels().get(0);
        TableMetaInfo.addModel(model);
        Tag tag = new Tag(searchableTag, "GET");
        List<String> tags = Collections.singletonList(tag.toString());
        SegmentRecord segmentRecordA = new SegmentRecord();
        segmentRecordA.setSegmentId("segmentA");
        segmentRecordA.setTraceId("traceA");
        segmentRecordA.setServiceId(serviceIdA);
        segmentRecordA.setTimeBucket(timeBucketSec);
        segmentRecordA.setTags(tags);
        segmentRecordA.setStartTime(DateTime.now().getMillis());
        segmentRecordA.setEndpointId(IDManager.EndpointID.buildId(segmentRecordA.getServiceId(), "Endpoint_A"));
        SegmentRecord segmentRecordB = new SegmentRecord();
        segmentRecordB.setSegmentId("segmentB");
        segmentRecordB.setTraceId("traceA");
        segmentRecordB.setServiceId(serviceIdB);
        segmentRecordB.setTimeBucket(timeBucketSec);
        segmentRecordB.setTags(tags);
        segmentRecordB.setStartTime(DateTime.now().getMillis());
        segmentRecordA.setEndpointId(IDManager.EndpointID.buildId(segmentRecordB.getServiceId(), "Endpoint_B"));

        createShardingRuleTest(model);
        updateShardingRuleTest(model);
        createShardingTableTest(model);
        insertRecords(model, Arrays.asList(segmentRecordA, segmentRecordB));
        testDataSharding(segmentRecordA);
        ttlDropTest(model);

        //Test trace query
        ShardingTraceQueryDAO traceQueryDAO = new ShardingTraceQueryDAO(moduleManager, ssClient);
        TraceBrief traceBrief = traceQueryDAO.queryBasicTraces(
            duration, 0, 0,
            segmentRecordA.getServiceId(), null,
            null, null, 10, 0,
            TraceState.SUCCESS, QueryOrder.BY_START_TIME, Collections.singletonList(tag)
        );
        Assert.assertEquals(segmentRecordA.getSegmentId(), traceBrief.getTraces().get(0).getSegmentId());

        List<SegmentRecord> segmentRecords = traceQueryDAO.queryByTraceId(segmentRecordA.getTraceId());
        Assert.assertEquals(2, segmentRecords.size());
        log.info("Records (Trace) test passed.");
    }

    // @SQLDatabase.Sharding(
    // shardingAlgorithm = ShardingAlgorithm.TIME_BUCKET_SHARDING_ALGORITHM,
    // tableShardingColumn = TIME_BUCKET,
    // dataSourceShardingColumn = ENTITY_ID)
    @SneakyThrows
    private void topologyTest() {
        log.info("Topology test start...");
        StorageModels models = new StorageModels();
        models.add(
            ServiceRelationServerSideMetrics.class, DefaultScopeDefine.SERVICE_RELATION,
            new Storage(ServiceRelationServerSideMetrics.INDEX_NAME, true, DownSampling.Minute), false
        );
        models.add(
            ServiceRelationClientSideMetrics.class, DefaultScopeDefine.SERVICE_RELATION,
            new Storage(ServiceRelationClientSideMetrics.INDEX_NAME, true, DownSampling.Minute), false
        );
        Model serverModel = models.allModels().get(0);
        Model clientModel = models.allModels().get(1);
        TableMetaInfo.addModel(serverModel);
        TableMetaInfo.addModel(clientModel);

        ServiceRelationServerSideMetrics serverSideMetricsA = new ServiceRelationServerSideMetrics();
        String clientIdA = IDManager.ServiceID.buildId("HTTP_Client", false);
        serverSideMetricsA.setSourceServiceId(clientIdA);
        serverSideMetricsA.setDestServiceId(serviceIdA);
        serverSideMetricsA.setEntityId(IDManager.ServiceID.buildRelationId(
            new IDManager.ServiceID.ServiceRelationDefine(clientIdA, serviceIdA)));
        serverSideMetricsA.getComponentIds().add(0);
        serverSideMetricsA.setTimeBucket(timeBucketMin);
        ServiceRelationClientSideMetrics clientSideMetricsA = new ServiceRelationClientSideMetrics();
        clientSideMetricsA.setSourceServiceId(clientIdA);
        clientSideMetricsA.setDestServiceId(serviceIdA);
        clientSideMetricsA.setEntityId(IDManager.ServiceID.buildRelationId(
            new IDManager.ServiceID.ServiceRelationDefine(clientIdA, serviceIdA)));
        clientSideMetricsA.getComponentIds().add(0);
        clientSideMetricsA.setTimeBucket(timeBucketMin);

        ServiceRelationServerSideMetrics serverSideMetricsB = new ServiceRelationServerSideMetrics();
        serverSideMetricsB.setSourceServiceId(serviceIdA);
        serverSideMetricsB.setDestServiceId(serviceIdB);
        serverSideMetricsB.setEntityId(IDManager.ServiceID.buildRelationId(
            new IDManager.ServiceID.ServiceRelationDefine(serviceIdA, serviceIdB)));
        serverSideMetricsB.getComponentIds().add(0);
        serverSideMetricsB.setTimeBucket(timeBucketMin);
        ServiceRelationClientSideMetrics clientSideMetricsB = new ServiceRelationClientSideMetrics();
        clientSideMetricsB.setSourceServiceId(clientIdA);
        clientSideMetricsB.setDestServiceId(serviceIdA);
        clientSideMetricsB.setEntityId(IDManager.ServiceID.buildRelationId(
            new IDManager.ServiceID.ServiceRelationDefine(serviceIdA, serviceIdB)));
        clientSideMetricsB.getComponentIds().add(0);
        clientSideMetricsB.setTimeBucket(timeBucketMin);

        createShardingRuleTest(serverModel);
        createShardingRuleTest(clientModel);
        updateShardingRuleTest(serverModel);
        updateShardingRuleTest(clientModel);
        createShardingTableTest(serverModel);
        createShardingTableTest(clientModel);

        insertMetrics(serverModel, Arrays.asList(serverSideMetricsA, serverSideMetricsB));
        insertMetrics(clientModel, Arrays.asList(clientSideMetricsA, clientSideMetricsB));
        testDataSharding(serverSideMetricsA);
        testDataSharding(clientSideMetricsA);
        ttlDropTest(serverModel);
        ttlDropTest(clientModel);

        //Test topology query
        ShardingTopologyQueryDAO queryDAO = new ShardingTopologyQueryDAO(ssClient);
        List<Call.CallDetail> callDetailsServerSide = queryDAO.loadServiceRelationsDetectedAtServerSide(
            duration, Arrays.asList(serviceIdB));
        //Service_A -----> Service_B
        Assert.assertEquals(serviceIdB, callDetailsServerSide.get(0).getTarget());
        List<Call.CallDetail> callDetailsClientSide = queryDAO.loadServiceRelationDetectedAtClientSide(
            duration, Arrays.asList(serviceIdA));
        //HTTP_Client -----> Service_A -----> Service_B
        Assert.assertEquals(2, callDetailsClientSide.size());
        log.info("Topology test passed.");
    }

    @SneakyThrows
    private void createShardingRuleTest(Model model) {
        ShardingRulesOperator.INSTANCE.createOrUpdateShardingRule(ssClient, model, dataSources, ttlTestCreate);
        Map<String, ShardingRule> shardingRules = Whitebox.getInternalState(
            ShardingRulesOperator.INSTANCE, "modelShardingRules");
        ShardingRule inputRule = shardingRules.get(model.getName()).toBuilder().build();
        ShardingRule outPutRule = loadShardingRule(model);
        outPutRule.setOperation("CREATE");
        //The rules in the database erased all `"`
        Assert.assertEquals(inputRule.toShardingRuleSQL().replaceAll("\"", ""), outPutRule.toShardingRuleSQL());
    }

    private void updateShardingRuleTest(Model model) throws StorageException {
        ShardingRulesOperator.INSTANCE.createOrUpdateShardingRule(ssClient, model, dataSources, ttlTestCreate);
        Map<String, ShardingRule> shardingRules = Whitebox.getInternalState(
            ShardingRulesOperator.INSTANCE, "modelShardingRules");
        ShardingRule inputRule = shardingRules.get(model.getName()).toBuilder().build();
        ShardingRule outPutRule = loadShardingRule(model);
        outPutRule.setOperation("ALTER");
        //The rules in the database erased all `"`
        Assert.assertEquals(inputRule.toShardingRuleSQL().replaceAll("\"", ""), outPutRule.toShardingRuleSQL());
    }

    /**
     * Load sharding rule from the database, return a clone rule for comparing.
     */
    @SneakyThrows
    private ShardingRule loadShardingRule(Model model) {
        Whitebox.invokeMethod(ShardingRulesOperator.INSTANCE, "initShardingRules", ssClient);
        Map<String, ShardingRule> shardingRules = Whitebox.getInternalState(
            ShardingRulesOperator.INSTANCE, "modelShardingRules");
        return shardingRules.get(model.getName()).toBuilder().build();
    }

    @SneakyThrows
    private void createShardingTableTest(Model model) {
        installer.createTable(model);
        existsTest(dsClient0, model);
        existsTest(dsClient1, model);
    }

    @SneakyThrows
    private void existsTest(JDBCHikariCPClient dsClient, Model model) {
        //TTL is 3 so just test 4 tables
        List<String> tables = new ArrayList<>();
        tables.add(model.getName() + "_" + DateTime.now().minusDays(2).toString("yyyyMMdd"));
        tables.add(model.getName() + "_" + DateTime.now().minusDays(1).toString("yyyyMMdd"));
        tables.add(model.getName() + "_" + DateTime.now().toString("yyyyMMdd"));
        tables.add(model.getName() + "_" + DateTime.now().plusDays(1).toString("yyyyMMdd"));
        try (Connection conn = dsClient.getConnection()) {
            for (String name : tables) {
                ResultSet rset = conn.getMetaData().getTables(conn.getCatalog(), null, name, null);
                Assert.assertTrue(rset.next());
            }
        }
    }

    @SneakyThrows
    private void insertMetrics(Model model, List<Metrics> metricsList) {
        try (Connection conn = ssClient.getConnection()) {
            for (Metrics metrics : metricsList) {
                JDBCMetricsDAO jdbcMetricsDAO = new JDBCMetricsDAO(ssClient, metrics.getClass()
                                                                                    .getAnnotation(Stream.class)
                                                                                    .builder()
                                                                                    .getDeclaredConstructor()
                                                                                    .newInstance());
                jdbcMetricsDAO.prepareBatchInsert(model, metrics, null).invoke(conn);
            }
        }
    }

    @SneakyThrows
    private void insertRecords(Model model, List<Record> recordList) {
        try (Connection conn = ssClient.getConnection()) {
            for (Record record : recordList) {
                JDBCRecordDAO jdbcRecordDAO = new JDBCRecordDAO(ssClient, record.getClass()
                                                                                .getAnnotation(Stream.class)
                                                                                .builder()
                                                                                .getDeclaredConstructor()
                                                                                .newInstance());
                ((SQLExecutor) jdbcRecordDAO.prepareBatchInsert(model, record)).invoke(conn);
            }
        }
    }

    @SneakyThrows
    private void testDataSharding(StorageData data) {
        try (Connection ssConn = ssClient.getConnection();
             Connection ds0Conn = dsClient0.getConnection();
             Connection ds1Conn = dsClient1.getConnection()) {

            String logicIndex = data.getClass().getAnnotation(Stream.class).name();
            String physicalIndex = logicIndex + "_" + timeBucketDay;

            ResultSet logicSet = ssClient.executeQuery(ssConn, countQuery + logicIndex);
            logicSet.next();
            Assert.assertEquals(2, logicSet.getInt("rc"));

            ResultSet physicalSet0 = dsClient0.executeQuery(ds0Conn, countQuery + physicalIndex);
            physicalSet0.next();
            Assert.assertEquals(1, physicalSet0.getInt("rc"));

            ResultSet physicalSet1 = dsClient1.executeQuery(ds1Conn, countQuery + physicalIndex);
            physicalSet1.next();
            Assert.assertEquals(1, physicalSet1.getInt("rc"));

            if (data.getClass().isAnnotationPresent(SQLDatabase.ExtraColumn4AdditionalEntity.class)) {
                String additionalLogicIndex = data.getClass().getAnnotation(Stream.class).name();
                String additionalPhysicalIndex = logicIndex + "_" + timeBucketDay;

                ResultSet additionalLogicSet = ssClient.executeQuery(
                    ssConn, countQuery + additionalLogicIndex);
                additionalLogicSet.next();
                Assert.assertEquals(2, additionalLogicSet.getInt("rc"));

                ResultSet additionalPhysicalSet0 = dsClient0.executeQuery(
                    ds0Conn, countQuery + additionalPhysicalIndex);
                additionalPhysicalSet0.next();
                Assert.assertEquals(1, additionalPhysicalSet0.getInt("rc"));

                ResultSet additionalPhysicalSet1 = dsClient1.executeQuery(
                    ds1Conn, countQuery + additionalPhysicalIndex);
                additionalPhysicalSet1.next();
                Assert.assertEquals(1, additionalPhysicalSet1.getInt("rc"));
            }
        }
    }

    @SneakyThrows
    private void ttlDropTest(Model model) {
        historyDelete(model);

        String droppedTable = model.getName() + "_" + DateTime.now().minusDays(2).toString("yyyyMMdd");
        ResultSet rset0 = dsClient0.getConnection()
                                   .getMetaData()
                                   .getTables(dsClient0.getConnection().getCatalog(), null, droppedTable, null);
        Assert.assertFalse(rset0.next());
        ResultSet rset1 = dsClient1.getConnection()
                                   .getMetaData()
                                   .getTables(dsClient1.getConnection().getCatalog(), null, droppedTable, null);
        Assert.assertFalse(rset1.next());
    }

    @SneakyThrows
    private void historyDelete(Model model) {
        ShardingHistoryDeleteDAO deleteDAO = new ShardingHistoryDeleteDAO(
            ssClient, dataSources, moduleManager, mySQLTableInstaller);
        deleteDAO.deleteHistory(model, Metrics.TIME_BUCKET, ttlTestDrop);
    }

    private enum DataSourceType {
        MYSQL
    }
}
