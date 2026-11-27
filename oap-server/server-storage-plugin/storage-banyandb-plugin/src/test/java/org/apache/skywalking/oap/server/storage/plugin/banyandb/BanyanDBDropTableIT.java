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

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.banyandb.schema.v1.BanyandbSchema.SchemaKey;
import org.apache.skywalking.library.banyandb.v1.client.BanyanDBClient;
import org.apache.skywalking.library.banyandb.v1.client.SchemaWatcher;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.database.TopNDatabaseStatement;
import org.apache.skywalking.oap.server.core.analysis.manual.database.TopNServiceDatabaseStatement;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ServiceLabelRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.metrics.CPMMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongAvgMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.management.runtimerule.RuntimeRule;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingPolicy;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.PointOfTime;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.input.RecordCondition;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.query.type.Record;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBMetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBServiceLabelDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBContinuousProfilingPolicyDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBStorageDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.trace.BanyanDBTraceQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.trace.BanyanDBZipkinQueryDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Drops models the way a runtime rule's removal does ({@code dropTable}), one of each kind of BanyanDB resource OAP
 * keeps a model in — a measure with its pre-built TopN, an index-mode measure, a stream, a trace and a property —
 * each beside another model that holds data too. Exactly the dropped resource must go, with what is named after it or
 * built on it (its index rule binding, its TopN aggregations), while the rest of its group keeps its schema and its
 * data, and the model must install again under the same name.
 *
 * <p>A drop returns only once every BanyanDB node has applied the deletion: it waits on the deletion's schema
 * revision, or on the schema key when the deletion records none, as every deletion does on this BanyanDB. So nothing
 * here waits between a drop and what it checks.
 */
@Testcontainers
public class BanyanDBDropTableIT {
    private static final String ENDPOINT_CPM = "endpoint_cpm";
    private static final String RESP_TIME = "service_resp_time";
    private static final String SERVICE = "drop-it";
    private static final String SERVICE_ID = IDManager.ServiceID.buildId(SERVICE, true);
    private static final String ENDPOINT = "GET:/drop";
    private static final String ENDPOINT_ID = IDManager.EndpointID.buildId(SERVICE_ID, ENDPOINT);
    private static final String DATABASE = "drop-it-db";
    private static final String OTHER_DATABASE = "drop-it-db-other";
    private static final String DROPPED_TRACE = "00000000000000000000000000000a01";
    private static final String NEW_TRACE = "00000000000000000000000000000a02";
    private static final java.time.Duration FENCE_TIMEOUT = java.time.Duration.ofSeconds(2);
    /** Applied on every node, none lagging. */
    private static final String DELETED = BanyanDBQueryITSupport.json(true, Collections.emptyList());
    /**
     * What a creation assigns a resource afresh, by field name: the metadata's mod_revision and the created_at and
     * updated_at times, which BanyanDB sets on every write, and a binding's begin_at, which the client sets to the time
     * it defines the binding. The rest stays compared: expire_at is a constant, and this BanyanDB leaves
     * create_revision unset and gives an id to index rules only, which a drop does not re-create.
     */
    private static final List<String> RECREATED = Arrays.asList("mod_revision", "created_at", "updated_at", "begin_at");

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBClient CLIENT;
    /** The first minute and the first hour written, aligned. */
    private static long FIRST_MINUTE;
    private static long FIRST_HOUR;

    @BeforeAll
    public static void connect() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        CLIENT = SUPPORT.getClient().client;
        final long now = System.currentTimeMillis();
        FIRST_MINUTE = (now - TimeUnit.HOURS.toMillis(1)) / 60_000L * 60_000L;
        FIRST_HOUR = (now - TimeUnit.HOURS.toMillis(5)) / 3_600_000L * 3_600_000L;
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void dropsAMeasureWithItsTopNAndBindingOnly() throws Exception {
        final Model cpmMinute = SUPPORT.installOAL(ENDPOINT_CPM, DownSampling.Minute);
        final Model cpmHour = SUPPORT.installOAL(ENDPOINT_CPM, DownSampling.Hour);
        final Model respTime = SUPPORT.installOAL(RESP_TIME, DownSampling.Minute);
        // Another measure of the group with a pre-built TopN of its own: the endpoint_cpm class under the name the
        // test rule testMetric-service is for.
        final Class<? extends Metrics> cpmClass = OALTestMetrics.metricsClass(ENDPOINT_CPM);
        final Model topNSibling = SUPPORT.installMetrics(cpmClass, OALTestMetrics.scopeId(cpmClass), "testMetric");
        final long hourBucket = TimeBucket.getTimeBucket(FIRST_HOUR, DownSampling.Hour);
        SUPPORT.writeMetrics(cpmMinute, OALTestMetrics.builder(OALTestMetrics.metricsClass(ENDPOINT_CPM)),
                             Arrays.asList(cpm(minuteBucket(0), 10), cpm(minuteBucket(1), 20)));
        // 180 calls in the hour are 3 a minute.
        SUPPORT.writeMetrics(cpmHour, OALTestMetrics.builder(OALTestMetrics.metricsClass(ENDPOINT_CPM)),
                             Collections.singletonList(cpm(hourBucket, 180)));
        SUPPORT.writeMetrics(respTime, OALTestMetrics.builder(OALTestMetrics.metricsClass(RESP_TIME)),
                             Arrays.asList(respTime(minuteBucket(0), 100), respTime(minuteBucket(1), 200)));
        final List<String> minuteCpm = series(point(minuteBucket(0), ENDPOINT_ID, "10"),
                                              point(minuteBucket(1), ENDPOINT_ID, "20"));
        final List<String> hourCpm = series(point(hourBucket, ENDPOINT_ID, "3"));
        final List<String> respTimes = series(point(minuteBucket(0), SERVICE_ID, "100"),
                                              point(minuteBucket(1), SERVICE_ID, "200"));
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(minuteCpm, endpointCpm(minutes(0, 1)));
            assertEquals(hourCpm, endpointCpm(firstHour()));
            assertEquals(respTimes, serviceRespTime());
        });
        final String minuteGroup = group(cpmMinute);
        final String hourGroup = group(cpmHour);
        final Map<String, Message> minuteSchema = measureSchema(minuteGroup);
        final Map<String, Message> hourSchema = measureSchema(hourGroup);
        assertTrue(minuteSchema.containsKey("topN testMetric-service of " + name(topNSibling)), minuteSchema.keySet().toString());

        SUPPORT.installer().dropTable(cpmMinute, StorageManipulationOpt.runtimeApply());

        assertThrows(IOException.class, () -> endpointCpm(minutes(0, 1)));
        assertEquals(without(minuteSchema, name(cpmMinute)), whole(measureSchema(minuteGroup)));
        assertEquals(whole(hourSchema), whole(measureSchema(hourGroup)));
        assertEquals(Arrays.asList(installed(cpmMinute, "metric", "MEASURE", false),
                                   installed(cpmHour, "metric", "MEASURE", true),
                                   installed(respTime, "metric", "MEASURE", true),
                                   installed(topNSibling, "metric", "MEASURE", true)),
                     Arrays.asList(exists(cpmMinute), exists(cpmHour), exists(respTime), exists(topNSibling)));
        assertEquals(DELETED, deletedEverywhere("measure", minuteGroup, name(cpmMinute)));
        assertEquals(hourCpm, endpointCpm(firstHour()));
        assertEquals(respTimes, serviceRespTime());

        final Model reinstalled = SUPPORT.installOAL(ENDPOINT_CPM, DownSampling.Minute);
        assertEquals(reinstalled(minuteSchema, name(cpmMinute)), reinstalled(measureSchema(minuteGroup), name(cpmMinute)));
        assertEquals(whole(hourSchema), whole(measureSchema(hourGroup)));
        assertEquals(installed(reinstalled, "metric", "MEASURE", true), exists(reinstalled));
        SUPPORT.writeMetrics(reinstalled, OALTestMetrics.builder(OALTestMetrics.metricsClass(ENDPOINT_CPM)),
                             Collections.singletonList(cpm(minuteBucket(2), 50)));
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(
            series(point(minuteBucket(2), ENDPOINT_ID, "50")), endpointCpm(minutes(2, 2))));
    }

    @Test
    public void dropsAnIndexModeMeasureOnly() throws Exception {
        final Model aliases = installAliases();
        final Model labels = SUPPORT.installMetrics(
            ServiceLabelRecord.class, DefaultScopeDefine.SERVICE_LABEL, ServiceLabelRecord.INDEX_NAME);
        final List<NetworkAddressAlias> written = Arrays.asList(alias("10.0.0.1:80", 0), alias("10.0.0.2:80", 1));
        SUPPORT.writeMetrics(aliases, new NetworkAddressAlias.Builder(), written);
        SUPPORT.writeMetrics(labels, new ServiceLabelRecord.Builder(), Arrays.asList(label("env:prod"), label("team:a")));
        final BanyanDBServiceLabelDAO labelDAO = new BanyanDBServiceLabelDAO(SUPPORT.getClient(), SUPPORT.getConfig());
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(BanyanDBQueryITSupport.stored(new NetworkAddressAlias.Builder(), written), aliases(aliases, written));
            assertEquals(Arrays.asList("env:prod", "team:a"), sorted(labelDAO.queryAllLabels(SERVICE_ID)));
        });
        final String group = group(aliases);
        final Map<String, Message> schema = measureSchema(group);

        SUPPORT.installer().dropTable(aliases);

        assertThrows(IOException.class, () -> aliases(aliases, written));
        assertEquals(without(schema, name(aliases)), whole(measureSchema(group)));
        assertEquals(Arrays.asList(installed(aliases, "metric", "MEASURE", false), installed(labels, "metric", "MEASURE", true)),
                     Arrays.asList(exists(aliases), exists(labels)));
        assertEquals(DELETED, deletedEverywhere("measure", group, name(aliases)));
        assertEquals(Arrays.asList("env:prod", "team:a"), sorted(labelDAO.queryAllLabels(SERVICE_ID)));

        final Model reinstalled = installAliases();
        assertEquals(reinstalled(schema, name(aliases)), reinstalled(measureSchema(group), name(aliases)));
        assertEquals(installed(reinstalled, "metric", "MEASURE", true), exists(reinstalled));
        final List<NetworkAddressAlias> again = Collections.singletonList(alias("10.0.0.3:80", 2));
        SUPPORT.writeMetrics(reinstalled, new NetworkAddressAlias.Builder(), again);
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(
            () -> assertEquals(BanyanDBQueryITSupport.stored(new NetworkAddressAlias.Builder(), again), aliases(reinstalled, again)));
    }

    @Test
    public void dropsAStreamOnly() throws Exception {
        final Model statements = installStatements();
        final Model serviceStatements = SUPPORT.install(
            TopNServiceDatabaseStatement.class, DefaultScopeDefine.SERVICE_DATABASE_SLOW_STATEMENT,
            TopNServiceDatabaseStatement.INDEX_NAME);
        final TopNDatabaseStatement slower = statement(DATABASE, 0, 30);
        final TopNDatabaseStatement faster = statement(DATABASE, 1, 20);
        final TopNServiceDatabaseStatement ofService = serviceStatement(2, 40);
        SUPPORT.write(statements, new TopNDatabaseStatement.Builder(), Arrays.asList(faster, slower));
        SUPPORT.write(serviceStatements, new TopNServiceDatabaseStatement.Builder(), Collections.singletonList(ofService));
        final List<String> slowestFirst = Arrays.asList(
            record(slower.getStatement(), slower), record(faster.getStatement(), faster));
        final List<String> serviceSlowest = Collections.singletonList(record(ofService.getStatement(), ofService));
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(slowestFirst, slowest(TopNDatabaseStatement.INDEX_NAME, DATABASE));
            assertEquals(serviceSlowest, slowest(TopNServiceDatabaseStatement.INDEX_NAME, DATABASE));
        });
        final String group = group(statements);
        final Map<String, Message> schema = streamSchema(group);

        SUPPORT.installer().dropTable(statements, StorageManipulationOpt.runtimeApply());

        assertThrows(IOException.class, () -> slowest(TopNDatabaseStatement.INDEX_NAME, DATABASE));
        assertEquals(without(schema, name(statements)), whole(streamSchema(group)));
        assertEquals(Arrays.asList(installed(statements, "record", "STREAM", false),
                                   installed(serviceStatements, "record", "STREAM", true)),
                     Arrays.asList(exists(statements), exists(serviceStatements)));
        assertEquals(DELETED, deletedEverywhere("stream", group, name(statements)));
        assertEquals(serviceSlowest, slowest(TopNServiceDatabaseStatement.INDEX_NAME, DATABASE));

        final Model reinstalled = installStatements();
        assertEquals(reinstalled(schema, name(statements)), reinstalled(streamSchema(group), name(statements)));
        assertEquals(installed(reinstalled, "record", "STREAM", true), exists(reinstalled));
        final TopNDatabaseStatement again = statement(OTHER_DATABASE, 3, 10);
        SUPPORT.write(reinstalled, new TopNDatabaseStatement.Builder(), Collections.singletonList(again));
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(
            Collections.singletonList(record(again.getStatement(), again)),
            slowest(TopNDatabaseStatement.INDEX_NAME, OTHER_DATABASE)));
    }

    @Test
    public void dropsATraceOnly() throws Exception {
        final Model zipkinSpans = installZipkinSpans();
        final Model segments = SUPPORT.install(SegmentRecord.class, DefaultScopeDefine.SEGMENT, SegmentRecord.INDEX_NAME);
        final List<ZipkinSpanRecord> dropped = Arrays.asList(
            zipkinSpan(DROPPED_TRACE, "0000000000000a01", null, "get /drop"),
            zipkinSpan(DROPPED_TRACE, "0000000000000a02", "0000000000000a01", "get /drop/child"));
        final SegmentRecord kept = segment("trace-kept");
        SUPPORT.write(zipkinSpans, new ZipkinSpanRecord.Builder(), dropped);
        SUPPORT.write(segments, new SegmentRecord.Builder(), Collections.singletonList(kept));
        final BanyanDBTraceQueryDAO traceDAO = new BanyanDBTraceQueryDAO(SUPPORT.getClient(), 200, namingControl());
        final List<String> keptTrace = BanyanDBQueryITSupport.describe(Collections.singletonList(kept.getSpanWrapper()));
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(describeZipkin(dropped), zipkinTrace(DROPPED_TRACE));
            assertEquals(keptTrace, BanyanDBQueryITSupport.describe(traceDAO.queryByTraceIdV2("trace-kept", minutes(0, 1))));
        });
        final String group = group(zipkinSpans);
        final String segmentGroup = group(segments);
        final Map<String, Message> schema = traceSchema(group);
        final Map<String, Message> segmentSchema = traceSchema(segmentGroup);

        SUPPORT.installer().dropTable(zipkinSpans);

        assertThrows(IOException.class, () -> zipkinTrace(DROPPED_TRACE));
        assertEquals(without(schema, name(zipkinSpans)), whole(traceSchema(group)));
        assertEquals(whole(segmentSchema), whole(traceSchema(segmentGroup)));
        assertEquals(Arrays.asList(installed(zipkinSpans, "record", "TRACE", false), installed(segments, "record", "TRACE", true)),
                     Arrays.asList(exists(zipkinSpans), exists(segments)));
        assertEquals(DELETED, deletedEverywhere("trace", group, name(zipkinSpans)));
        assertEquals(keptTrace, BanyanDBQueryITSupport.describe(traceDAO.queryByTraceIdV2("trace-kept", minutes(0, 1))));

        final Model reinstalled = installZipkinSpans();
        assertEquals(reinstalled(schema, name(zipkinSpans)), reinstalled(traceSchema(group), name(zipkinSpans)));
        assertEquals(whole(segmentSchema), whole(traceSchema(segmentGroup)));
        assertEquals(installed(reinstalled, "record", "TRACE", true), exists(reinstalled));
        final List<ZipkinSpanRecord> again =
            Collections.singletonList(zipkinSpan(NEW_TRACE, "0000000000000a03", null, "get /drop/again"));
        SUPPORT.write(reinstalled, new ZipkinSpanRecord.Builder(), again);
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(
            () -> assertEquals(describeZipkin(again), zipkinTrace(NEW_TRACE)));
    }

    @Test
    public void dropsAPropertyDefinitionOnly() throws Exception {
        final Model policies = installPolicies();
        final Model templates = SUPPORT.installManagement(UITemplate.class, DefaultScopeDefine.UI_TEMPLATE, UITemplate.INDEX_NAME);
        final Model rules = SUPPORT.installManagement(RuntimeRule.class, DefaultScopeDefine.RUNTIME_RULE, RuntimeRule.INDEX_NAME);
        final BanyanDBContinuousProfilingPolicyDAO policyDAO = new BanyanDBContinuousProfilingPolicyDAO(SUPPORT.getClient());
        final BanyanDBUITemplateManagementDAO templateDAO = new BanyanDBUITemplateManagementDAO(SUPPORT.getClient());
        final BanyanDBRuntimeRuleManagementDAO ruleDAO = new BanyanDBRuntimeRuleManagementDAO(SUPPORT.getClient());
        final ContinuousProfilingPolicy dropped = policy(policyService(0), "uuid-0", "{\"targets\":[\"ON_CPU\"]}");
        final ContinuousProfilingPolicy kept = policy(policyService(1), "uuid-1", "{\"targets\":[\"OFF_CPU\"]}");
        policyDAO.savePolicy(dropped);
        policyDAO.savePolicy(kept);
        final DashboardSetting template = setting("template-kept", "{\"kept\":true}");
        final RuntimeRule rule = rule("rule-kept");
        templateDAO.addTemplate(template);
        ruleDAO.save(rule);
        final List<String> keptTemplates = Collections.singletonList(
            BanyanDBQueryITSupport.json(template.getId(), template.getConfiguration(), false));
        final List<String> keptRules = Collections.singletonList(BanyanDBQueryITSupport.json(
            rule.getCatalog(), rule.getName(), rule.getContent(), rule.getStatus(), rule.getUpdateTime()));
        assertEquals(BanyanDBQueryITSupport.stored(new ContinuousProfilingPolicy.Builder(), Arrays.asList(dropped, kept)),
                     policies(policyDAO, dropped.getServiceId(), kept.getServiceId()));
        assertEquals(keptTemplates, templates(templateDAO));
        assertEquals(keptRules, rules(ruleDAO));
        final String group = group(policies);
        final Map<String, Message> schema = propertySchema(group);

        SUPPORT.installer().dropTable(policies, StorageManipulationOpt.runtimeApply());

        assertThrows(IOException.class,
                     () -> SUPPORT.getClient().queryProperty(ContinuousProfilingPolicy.INDEX_NAME, dropped.getServiceId()));
        assertEquals(without(schema, name(policies)), whole(propertySchema(group)));
        assertEquals(Arrays.asList(installed(policies, "unknown", "PROPERTY", false),
                                   installed(templates, "unknown", "PROPERTY", true),
                                   installed(rules, "unknown", "PROPERTY", true)),
                     Arrays.asList(exists(policies), exists(templates), exists(rules)));
        assertEquals(DELETED, deletedEverywhere("property", group, name(policies)));
        assertEquals(keptTemplates, templates(templateDAO));
        assertEquals(keptRules, rules(ruleDAO));

        final Model reinstalled = installPolicies();
        assertEquals(reinstalled(schema, name(policies)), reinstalled(propertySchema(group), name(policies)));
        assertEquals(installed(reinstalled, "unknown", "PROPERTY", true), exists(reinstalled));
        final ContinuousProfilingPolicy again = policy(policyService(2), "uuid-2", "{\"targets\":[\"NETWORK\"]}");
        policyDAO.savePolicy(again);
        assertEquals(BanyanDBQueryITSupport.stored(new ContinuousProfilingPolicy.Builder(), Collections.singletonList(again)),
                     policies(policyDAO, again.getServiceId()));
    }

    private static Model installAliases() throws Exception {
        return SUPPORT.installMetrics(
            NetworkAddressAlias.class, DefaultScopeDefine.NETWORK_ADDRESS_ALIAS, NetworkAddressAlias.INDEX_NAME);
    }

    private static Model installStatements() throws Exception {
        return SUPPORT.install(
            TopNDatabaseStatement.class, DefaultScopeDefine.DATABASE_SLOW_STATEMENT, TopNDatabaseStatement.INDEX_NAME);
    }

    private static Model installZipkinSpans() throws Exception {
        return SUPPORT.install(ZipkinSpanRecord.class, DefaultScopeDefine.ZIPKIN_SPAN, ZipkinSpanRecord.INDEX_NAME);
    }

    private static Model installPolicies() throws Exception {
        return SUPPORT.installManagement(ContinuousProfilingPolicy.class, DefaultScopeDefine.CONTINUOUS_PROFILING_POLICY,
                                         ContinuousProfilingPolicy.INDEX_NAME);
    }

    /** What the installer finds of the model, as the runtime-rule apply verifies it: its whole install info. */
    private static String exists(Model model) throws Exception {
        return SUPPORT.installer().isExists(model, StorageManipulationOpt.bootInstall()).buildInstallInfoMsg();
    }

    /** The install info of a model whose group is there, and whose resource is there or not. */
    private static String installed(Model model, String modelType, String kind, boolean exists) {
        return "InstallInfoBanyanDB{modelName=" + model.getName() + ", modelType=" + modelType
            + ", timeSeries=" + model.isTimeSeries() + ", superDataset=" + model.isSuperDataset()
            + ", downSampling=" + model.getDownsampling().name() + ", tableName=" + name(model) + ", kind=" + kind
            + ", group=" + group(model) + ", allResourcesExist=" + exists + " [groupExist=true, tableExist=" + exists + "]}";
    }

    /** What every BanyanDB node answers about the resource being gone. */
    private static String deletedEverywhere(String kind, String group, String name) throws BanyanDBException {
        final SchemaKey key = SchemaKey.newBuilder().setKind(kind).setGroup(group).setName(name).build();
        final SchemaWatcher.Result result = CLIENT.getSchemaWatcher().awaitSchemaDeleted(key, FENCE_TIMEOUT);
        return BanyanDBQueryITSupport.json(
            result.isApplied(), result.getLaggards().stream().map(BanyanDBQueryITSupport::text).collect(Collectors.toList()));
    }

    private static String group(Model model) {
        return MetadataRegistry.INSTANCE.findMetadata(model).getMetadata().getGroup();
    }

    private static String name(Model model) {
        return MetadataRegistry.INSTANCE.findMetadata(model).getMetadata().name();
    }

    /** Every resource of the schema whole, revisions and stamps included, as "kind name|definition". */
    private static List<String> whole(Map<String, Message> schema) {
        return schema.entrySet().stream().map(resource -> resource.getKey() + "|" + BanyanDBQueryITSupport.text(resource.getValue()))
                     .collect(Collectors.toList());
    }

    /**
     * The schema with the dropped model's resources taken out — its own and those named after it or built on it (its
     * index rule binding, its TopN aggregations) — and every other one whole: a drop must not change them.
     */
    private static List<String> without(Map<String, Message> schema, String name) {
        final Map<String, Message> rest = new TreeMap<>(schema);
        rest.keySet().removeIf(key -> ofModel(key, name));
        return whole(rest);
    }

    /**
     * The schema as it must read once the dropped model is installed again: every other resource whole, as it was
     * before the drop, to its revisions; the model's own resources created anew, so each without what a creation
     * assigns afresh — {@link #RECREATED} — and as it was otherwise.
     */
    private static List<String> reinstalled(Map<String, Message> schema, String name) {
        final Map<String, Message> read = new TreeMap<>(schema);
        read.replaceAll((key, resource) -> ofModel(key, name) ? withoutCreation(resource) : resource);
        return whole(read);
    }

    private static boolean ofModel(String key, String name) {
        return key.endsWith(" " + name) || key.endsWith(" of " + name);
    }

    private static Message withoutCreation(Message resource) {
        final Message.Builder builder = resource.toBuilder();
        for (final String field : RECREATED) {
            final Descriptors.FieldDescriptor top = builder.getDescriptorForType().findFieldByName(field);
            if (top != null) {
                builder.clearField(top);
            }
        }
        final Descriptors.FieldDescriptor metadata = builder.getDescriptorForType().findFieldByName("metadata");
        final Message.Builder identity = ((Message) builder.getField(metadata)).toBuilder();
        for (final String field : RECREATED) {
            final Descriptors.FieldDescriptor ofIdentity = identity.getDescriptorForType().findFieldByName(field);
            if (ofIdentity != null) {
                identity.clearField(ofIdentity);
            }
        }
        return builder.setField(metadata, identity.build()).build();
    }

    /**
     * Every measure of the group, with its TopN aggregations, index rule bindings and index rules, by "kind name".
     */
    private static Map<String, Message> measureSchema(String group) throws BanyanDBException {
        final Map<String, Message> schema = new TreeMap<>();
        CLIENT.findMeasures(group).forEach(measure -> schema.put("measure " + measure.getMetadata().getName(), measure));
        CLIENT.findTopNAggregations(group).forEach(topN -> schema.put(
            "topN " + topN.getMetadata().getName() + " of " + topN.getSourceMeasure().getName(), topN));
        return withIndexes(group, schema);
    }

    private static Map<String, Message> streamSchema(String group) throws BanyanDBException {
        final Map<String, Message> schema = new TreeMap<>();
        CLIENT.findStreams(group).forEach(stream -> schema.put("stream " + stream.getMetadata().getName(), stream));
        return withIndexes(group, schema);
    }

    private static Map<String, Message> traceSchema(String group) throws BanyanDBException {
        final Map<String, Message> schema = new TreeMap<>();
        CLIENT.findTraces(group).forEach(trace -> schema.put("trace " + trace.getMetadata().getName(), trace));
        return withIndexes(group, schema);
    }

    private static Map<String, Message> propertySchema(String group) throws BanyanDBException {
        final Map<String, Message> schema = new TreeMap<>();
        CLIENT.findPropertiesDefinition(group).forEach(
            property -> schema.put("property " + property.getMetadata().getName(), property));
        return schema;
    }

    /** Index rules are shared by the group's resources, so a drop leaves them; a binding is named after its resource. */
    private static Map<String, Message> withIndexes(String group, Map<String, Message> schema) throws BanyanDBException {
        CLIENT.findIndexRuleBindings(group).forEach(
            binding -> schema.put("binding " + binding.getMetadata().getName(), binding));
        CLIENT.findIndexRules(group).forEach(
            rule -> schema.put("index rule (shared) " + rule.getMetadata().getName(), rule));
        return schema;
    }

    private static List<String> endpointCpm(Duration duration) throws IOException {
        final Entity entity = new Entity();
        entity.setScope(Scope.Endpoint);
        entity.setServiceName(SERVICE);
        entity.setEndpointName(ENDPOINT);
        entity.setNormal(true);
        return values(ENDPOINT_CPM, entity, duration);
    }

    private static List<String> serviceRespTime() throws IOException {
        final Entity entity = new Entity();
        entity.setScope(Scope.Service);
        entity.setServiceName(SERVICE);
        entity.setNormal(true);
        return values(RESP_TIME, entity, minutes(0, 1));
    }

    /**
     * The answer of the metrics query whole: its label, then every point of the range, its id and its value, or that
     * it has none.
     */
    private static List<String> values(String metric, Entity entity, Duration duration) throws IOException {
        final MetricsCondition condition = new MetricsCondition();
        condition.setName(metric);
        condition.setEntity(entity);
        final MetricsValues values = new BanyanDBMetricsQueryDAO(SUPPORT.getClient())
            .readMetricsValues(condition, ValueColumnMetadata.INSTANCE.getValueCName(metric), duration);
        final List<String> read = new ArrayList<>();
        read.add(BanyanDBQueryITSupport.json(values.getLabel()));
        values.getValues().getValues().stream()
              .map(kv -> BanyanDBQueryITSupport.json(kv.getId(), kv.getValue(), kv.isEmptyValue()))
              .forEach(read::add);
        return read;
    }

    /** What the metrics query must return: no label, then these points. */
    private static List<String> series(String... points) {
        final List<String> series = new ArrayList<>();
        series.add(BanyanDBQueryITSupport.json((Object) null));
        series.addAll(Arrays.asList(points));
        return series;
    }

    /** A point the metrics query must return: the id it gives the point of an entity, its value, and not empty. */
    private static String point(long timeBucket, String entityId, String value) {
        return BanyanDBQueryITSupport.json(new PointOfTime(timeBucket).id(entityId), Long.parseLong(value), false);
    }

    /** These aliases as the alias worker reads them back by id, whole. */
    private static List<String> aliases(Model model, List<NetworkAddressAlias> asked) throws Exception {
        return BanyanDBQueryITSupport.stored(new NetworkAddressAlias.Builder(),
                      new BanyanDBStorageDAO(SUPPORT.getClient()).newMetricsDao(new NetworkAddressAlias.Builder())
                                                                .multiGet(model, new ArrayList<>(asked)));
    }

    /** The statements of the database, slowest first, each record whole. */
    private static List<String> slowest(String recordName, String databaseName) throws IOException {
        final Entity database = new Entity();
        database.setScope(Scope.Service);
        database.setServiceName(databaseName);
        database.setNormal(false);
        final RecordCondition condition = new RecordCondition();
        condition.setName(recordName);
        condition.setParentEntity(database);
        condition.setTopN(10);
        condition.setOrder(Order.DES);
        final List<Record> records = new BanyanDBRecordsQueryDAO(SUPPORT.getClient())
            .readRecords(condition, TopN.LATENCY, minutes(0, 1));
        return records.stream()
                      .map(record -> BanyanDBQueryITSupport.json(record.getName(), record.getId(), record.getRefId(), record.getValue()))
                      .collect(Collectors.toList());
    }

    /** The record the records query must return for a written statement: it refers to the statement's trace. */
    private static String record(String statementText, TopN statement) {
        return BanyanDBQueryITSupport.json(statementText, statement.getTraceId(), statement.getTraceId(),
                                           String.valueOf(statement.getLatency()));
    }

    private static List<String> policies(BanyanDBContinuousProfilingPolicyDAO dao, String... serviceIds)
        throws IOException {
        return BanyanDBQueryITSupport.stored(new ContinuousProfilingPolicy.Builder(), dao.queryPolicies(Arrays.asList(serviceIds)));
    }

    private static List<String> templates(BanyanDBUITemplateManagementDAO dao) throws IOException {
        return dao.getAllTemplates(true).stream()
                  .map(template -> BanyanDBQueryITSupport.json(template.getId(), template.getConfiguration(),
                                                               template.isDisabled()))
                  .sorted().collect(Collectors.toList());
    }

    private static List<String> rules(BanyanDBRuntimeRuleManagementDAO dao) throws IOException {
        return dao.getAll().stream()
                  .map(rule -> BanyanDBQueryITSupport.json(rule.getCatalog(), rule.getName(), rule.getContent(),
                                                           rule.getStatus(), rule.getUpdateTime()))
                  .sorted().collect(Collectors.toList());
    }

    /** Every span of a Zipkin trace read by its id, each wrapper whole. */
    private static List<String> zipkinTrace(String traceId) throws IOException {
        return BanyanDBQueryITSupport.describe(new BanyanDBZipkinQueryDAO(SUPPORT.getClient()).getTraceV2(traceId, minutes(0, 1)));
    }

    /** The wrappers OAP writes for these Zipkin spans. */
    private static List<String> describeZipkin(List<ZipkinSpanRecord> spans) {
        return BanyanDBQueryITSupport.describe(spans.stream().map(ZipkinSpanRecord::getSpanWrapper).collect(Collectors.toList()));
    }

    private static String policyService(int index) {
        return IDManager.ServiceID.buildId("drop-it-policy-" + index, true);
    }

    private static List<String> sorted(List<String> values) {
        return values.stream().sorted().collect(Collectors.toList());
    }

    private static Metrics cpm(long timeBucket, long calls) throws Exception {
        final CPMMetrics metrics = (CPMMetrics) OALTestMetrics.newMetrics(
            OALTestMetrics.metricsClass(ENDPOINT_CPM), "entityId", IDManager.EndpointID.buildId(SERVICE_ID, ENDPOINT),
            "serviceId", SERVICE_ID, "attr0", "GENERAL", "timeBucket", timeBucket);
        for (int i = 0; i < calls; i++) {
            metrics.combine(1);
        }
        metrics.calculate();
        return metrics;
    }

    private static Metrics respTime(long timeBucket, long value) throws Exception {
        final LongAvgMetrics metrics = (LongAvgMetrics) OALTestMetrics.newMetrics(
            OALTestMetrics.metricsClass(RESP_TIME), "entityId", SERVICE_ID, "timeBucket", timeBucket);
        metrics.combine(value, 1);
        metrics.calculate();
        return metrics;
    }

    private static NetworkAddressAlias alias(String address, int index) {
        final NetworkAddressAlias alias = new NetworkAddressAlias();
        alias.setAddress(address);
        alias.setRepresentServiceId(SERVICE_ID);
        alias.setRepresentServiceInstanceId(IDManager.ServiceInstanceID.buildId(SERVICE_ID, "instance-" + index));
        alias.setLastUpdateTimeBucket(minuteBucket(index));
        alias.setTimeBucket(minuteBucket(0));
        return alias;
    }

    private static ServiceLabelRecord label(String label) {
        final ServiceLabelRecord record = new ServiceLabelRecord();
        record.setServiceId(SERVICE_ID);
        record.setLabel(label);
        record.setTimeBucket(minuteBucket(0));
        return record;
    }

    private static TopNDatabaseStatement statement(String database, int index, long latency) {
        final TopNDatabaseStatement statement = new TopNDatabaseStatement();
        statement.setId("statement-" + index);
        statement.setStatement("select " + index);
        return slow(statement, database, index, latency);
    }

    private static TopNServiceDatabaseStatement serviceStatement(int index, long latency) {
        final TopNServiceDatabaseStatement statement = new TopNServiceDatabaseStatement();
        statement.setId("statement-" + index);
        statement.setStatement("select " + index);
        return slow(statement, DATABASE, index, latency);
    }

    private static <T extends TopN> T slow(T statement, String database, int index, long latency) {
        final long timestamp = FIRST_MINUTE + index * 1000L;
        statement.setLatency(latency);
        statement.setTraceId("trace-" + index);
        statement.setEntityId(IDManager.ServiceID.buildId(database, false));
        statement.setTimestamp(timestamp);
        statement.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
        return statement;
    }

    private static ZipkinSpanRecord zipkinSpan(String traceId, String spanId, String parentId, String name) {
        final long timestamp = FIRST_MINUTE + 1_000L;
        final ZipkinSpanRecord span = new ZipkinSpanRecord();
        span.setTraceId(traceId);
        span.setSpanId(spanId);
        span.setParentId(parentId);
        span.setKind("SERVER");
        span.setName(name);
        span.setLocalEndpointServiceName(SERVICE);
        span.setTimestamp(timestamp * 1000L);
        span.setTimestampMillis(timestamp);
        span.setDuration(10_000L);
        span.setQuery(Collections.emptyList());
        span.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
        return span;
    }

    private static SegmentRecord segment(String traceId) {
        final long startTime = FIRST_MINUTE + 1_000L;
        final SegmentObject segmentObject = SegmentObject.newBuilder()
            .setTraceId(traceId)
            .setTraceSegmentId(traceId + ".segment")
            .setService(SERVICE)
            .setServiceInstance("instance")
            .addSpans(SpanObject.newBuilder()
                                .setSpanId(0)
                                .setParentSpanId(-1)
                                .setStartTime(startTime)
                                .setEndTime(startTime + 10)
                                .setOperationName(ENDPOINT)
                                .setSpanType(SpanType.Entry))
            .build();
        final SegmentRecord segment = new SegmentRecord();
        segment.setSegmentId(segmentObject.getTraceSegmentId());
        segment.setTraceId(traceId);
        segment.setServiceId(SERVICE_ID);
        segment.setServiceInstanceId(IDManager.ServiceInstanceID.buildId(SERVICE_ID, "instance"));
        segment.setEndpointId(IDManager.EndpointID.buildId(SERVICE_ID, ENDPOINT));
        segment.setStartTime(startTime);
        segment.setLatency(10);
        segment.setIsError(0);
        segment.setTimeBucket(TimeBucket.getRecordTimeBucket(startTime));
        segment.setDataBinary(segmentObject.toByteArray());
        segment.setTags(Collections.emptyList());
        return segment;
    }

    private static ContinuousProfilingPolicy policy(String serviceId, String uuid, String configuration) {
        final ContinuousProfilingPolicy policy = new ContinuousProfilingPolicy();
        policy.setServiceId(serviceId);
        policy.setConfigurationJson(configuration);
        policy.setUuid(uuid);
        return policy;
    }

    private static DashboardSetting setting(String id, String configuration) {
        final DashboardSetting setting = new DashboardSetting();
        setting.setId(id);
        setting.setConfiguration(configuration);
        return setting;
    }

    private static RuntimeRule rule(String name) {
        final RuntimeRule rule = new RuntimeRule();
        rule.setCatalog("otel-rules");
        rule.setName(name);
        rule.setContent("content");
        rule.setStatus("ACTIVE");
        rule.setUpdateTime(1000L);
        return rule;
    }

    private static Duration minutes(int first, int last) {
        final Duration duration = new Duration();
        duration.setStep(Step.MINUTE);
        duration.setStart(DurationUtils.YYYY_MM_DD_HHMM.print(FIRST_MINUTE + first * 60_000L));
        duration.setEnd(DurationUtils.YYYY_MM_DD_HHMM.print(FIRST_MINUTE + last * 60_000L));
        return duration;
    }

    private static Duration firstHour() {
        final Duration duration = new Duration();
        duration.setStep(Step.HOUR);
        duration.setStart(DurationUtils.YYYY_MM_DD_HH.print(FIRST_HOUR));
        duration.setEnd(DurationUtils.YYYY_MM_DD_HH.print(FIRST_HOUR));
        return duration;
    }

    private static long minuteBucket(int index) {
        return TimeBucket.getMinuteTimeBucket(FIRST_MINUTE + index * 60_000L);
    }

    /** The trace DAO names each segment's service, instance and endpoint the way the core module does. */
    private static ModuleManager namingControl() {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleProviderHolder coreProvider = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder coreServices = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(coreProvider);
        when(coreProvider.provider()).thenReturn(coreServices);
        when(coreServices.getService(NamingControl.class))
            .thenReturn(new NamingControl(70, 100, 150, new EndpointNameGrouping()));
        return moduleManager;
    }
}
