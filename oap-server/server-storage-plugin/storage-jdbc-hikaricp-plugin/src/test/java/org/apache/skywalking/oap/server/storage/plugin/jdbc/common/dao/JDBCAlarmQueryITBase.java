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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecoveryRecord;
import org.apache.skywalking.oap.server.core.alarm.AlarmSnapshotRecord;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.AlarmQueryCondition;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.mqe.MQEValue;
import org.apache.skywalking.oap.server.core.query.mqe.MQEValues;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCQueryITSupport.MIDNIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Reads alarms the way both alarm queries do on a JDBC storage, through {@link JDBCAlarmQueryDAO}: the legacy
 * {@code getAlarm} by scope, keyword and tags, and {@code queryAlarms} by entity, layer, rule name, keyword and tags,
 * both newest first and page by page, each alarm with the recovery time {@code alarm_recovery_record} holds for it. A
 * subclass names the server.
 *
 * <p>The alarms are written as the alarm persistence writes them, on both sides of midnight, so a read crosses two day
 * tables: the searchable tags in the tag table and every tag in the raw tags, the recovery in the day of the alarm's
 * start. An entity is the primary of some alarms and the destination of a relation alarm, the relation also fires the
 * other way round, and for each other filter an alarm matches every condition but that one; a tag kept in the raw tags
 * only is not searchable. A page holds a dozen recovered alarms. Alarms of their own lie on and just outside the bounds
 * of a duration: on its first millisecond and the one before, on its last millisecond, and just after its end.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JDBCAlarmQueryITBase {
    private static final long MINUTE = 60_000L;
    private static final Duration AROUND_MIDNIGHT = minutes(MIDNIGHT - 5 * MINUTE, MIDNIGHT + 4 * MINUTE);
    private static final Duration BOUNDED = minutes(MIDNIGHT + 10 * MINUTE, MIDNIGHT + 11 * MINUTE);
    private static final Duration RECOVERING = minutes(MIDNIGHT + 30 * MINUTE, MIDNIGHT + 30 * MINUTE);
    private static final int RECOVERED_IN_ONE_PAGE = 12;
    /** The tag keys the IT configures as {@code searchableAlarmTags}. */
    private static final String SEARCHABLE_ALARM_TAGS = "env,team";
    private static final Gson GSON = new Gson();

    private static final String CHECKOUT = "checkout";
    private static final String PAYMENT = "payment";
    private static final String BOUNDS = "bounds";
    private static final String SLA = "Successful rate of service %s is lower than 80%%";
    private static final String RESP = "Response time of service %s is more than 1000ms";

    private static final List<AlarmRow> ALARMS = Arrays.asList(
        AlarmRow.service("a1", MIDNIGHT - 150_000L, CHECKOUT, String.format(RESP, CHECKOUT),
                         "service_resp_time_rule", Layer.GENERAL).tags("env=prod", "owner=alice").recovered()
                .snapshot("avg(service_resp_time) > 1000", "service_resp_time", "1200"),
        AlarmRow.service("a2", MIDNIGHT - 110_000L, PAYMENT, String.format(SLA, PAYMENT), "service_sla_rule",
                         Layer.MESH).tags("env=test"),
        AlarmRow.endpoint("a3", MIDNIGHT - 70_000L, CHECKOUT, "POST /orders",
                          "Response time of endpoint POST /orders is more than 1000ms", "endpoint_resp_time_rule",
                          Layer.GENERAL).tags("env=prod", "team=a").recovered(),
        AlarmRow.relation("a4", MIDNIGHT - 30_000L, CHECKOUT, PAYMENT, Layer.GENERAL),
        AlarmRow.service("a5", MIDNIGHT + 30_000L, CHECKOUT, String.format(SLA, CHECKOUT), "service_sla_rule",
                         Layer.GENERAL).tags("env=prod").recovered(),
        AlarmRow.instance("a6", MIDNIGHT + 70_000L, CHECKOUT, "checkout-1",
                          "Response time of service instance checkout-1 is more than 1000ms",
                          "instance_resp_time_rule", Layer.GENERAL).tags("env=prod", "team=b"),
        AlarmRow.service("a7", MIDNIGHT + 110_000L, PAYMENT, "Success count of service payment is lower than 10",
                         "service_success_rule", Layer.MESH),
        AlarmRow.relation("a8", MIDNIGHT + 150_000L, PAYMENT, CHECKOUT, Layer.MESH)
    );
    private static final List<AlarmRow> BOUND_ALARMS = Arrays.asList(
        AlarmRow.service("before-start", BOUNDED.getStartTimestamp() - 1, BOUNDS, "bounds", "bounds_rule",
                         Layer.GENERAL),
        AlarmRow.service("on-start", BOUNDED.getStartTimestamp(), BOUNDS, "bounds", "bounds_rule", Layer.GENERAL),
        AlarmRow.service("last-millisecond", BOUNDED.getEndTimestamp() - 1, BOUNDS, "bounds", "bounds_rule",
                         Layer.GENERAL),
        AlarmRow.service("after-end", BOUNDED.getEndTimestamp() + 1, BOUNDS, "bounds", "bounds_rule",
                         Layer.GENERAL)
    );

    private final JdbcDatabaseContainer<?> server;
    private final JDBCQueryITSupport.Target target;
    private JDBCQueryITSupport support;
    private JDBCAlarmQueryDAO dao;

    JDBCAlarmQueryITBase(final JdbcDatabaseContainer<?> server, final JDBCQueryITSupport.Target target) {
        this.server = server;
        this.target = target;
    }

    @BeforeAll
    public void writeAlarms() throws Exception {
        support = JDBCQueryITSupport.connect(server, target);
        final List<AlarmRow> alarms = new ArrayList<>(ALARMS);
        alarms.addAll(BOUND_ALARMS);
        for (int i = 0; i < RECOVERED_IN_ONE_PAGE; i++) {
            alarms.add(AlarmRow.service(String.format("recovered-%02d", i), RECOVERING.getStartTimestamp() + i * 1000L,
                                        "recovering", "recovering", "recovering_rule", Layer.GENERAL).recovered());
        }
        final Model alarmModel = support.install(
            AlarmRecord.class, DefaultScopeDefine.ALARM, AlarmRecord.INDEX_NAME, DownSampling.Second);
        final Model recoveryModel = support.install(
            AlarmRecoveryRecord.class, DefaultScopeDefine.ALARM_RECOVERY, AlarmRecoveryRecord.INDEX_NAME,
            DownSampling.Second);
        support.writeRecords(alarmModel, new AlarmRecord.Builder(), alarms.stream()
            .map(AlarmRow::record).collect(Collectors.toList()));
        support.writeRecords(recoveryModel, new AlarmRecoveryRecord.Builder(), alarms.stream()
            .filter(alarm -> alarm.recoveryTime != null).map(AlarmRow::recovery).collect(Collectors.toList()));
        final ConfigService config = support.getModuleManager().find(CoreModule.NAME).provider()
                                            .getService(ConfigService.class);
        when(config.getSearchableAlarmTags()).thenReturn(SEARCHABLE_ALARM_TAGS);
        dao = new JDBCAlarmQueryDAO(support.getClient(), support.getModuleManager(), support.getTableHelper());
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    @Test
    public void pagesThroughEveryAlarmNewestFirst() {
        assertEquals(Arrays.asList("a8", "a7", "a6"), uuids(dao.queryAlarms(condition(), 3, 0)));
        assertEquals(Arrays.asList("a5", "a4", "a3"), uuids(dao.queryAlarms(condition(), 3, 3)));
        assertEquals(Arrays.asList("a2", "a1"), uuids(dao.queryAlarms(condition(), 3, 6)));
        assertEquals(Collections.emptyList(), uuids(dao.queryAlarms(condition(), 3, 9)));
    }

    /**
     * A service is the primary of its own alarms and the destination of a relation alarm.
     */
    @Test
    public void readsTheAlarmsOfAServiceAsThePrimaryOrTheRelationsDestination() {
        assertEquals(Arrays.asList("a8", "a5", "a4", "a1"), uuids(dao.queryAlarms(entities(service(CHECKOUT)), 10, 0)));
        assertEquals(Arrays.asList("a8", "a7", "a4", "a2"), uuids(dao.queryAlarms(entities(service(PAYMENT)), 10, 0)));
    }

    @Test
    public void readsTheAlarmsOfARelationInItsDirectionOnly() {
        assertEquals(Collections.singletonList("a4"), uuids(dao.queryAlarms(
            entities(relation(CHECKOUT, PAYMENT)), 10, 0)));
    }

    @Test
    public void readsTheAlarmsOfAnInstanceAndOfAnEndpoint() {
        final Entity instance = new Entity();
        instance.setScope(Scope.ServiceInstance);
        instance.setServiceName(CHECKOUT);
        instance.setServiceInstanceName("checkout-1");
        assertEquals(Collections.singletonList("a6"), uuids(dao.queryAlarms(entities(instance), 10, 0)));
        final Entity endpoint = new Entity();
        endpoint.setScope(Scope.Endpoint);
        endpoint.setServiceName(CHECKOUT);
        endpoint.setEndpointName("POST /orders");
        assertEquals(Collections.singletonList("a3"), uuids(dao.queryAlarms(entities(endpoint), 10, 0)));
    }

    @Test
    public void readsAnAlarmOnceWhenTwoEntitiesMatchIt() {
        assertEquals(Arrays.asList("a8", "a5", "a4", "a1"), uuids(dao.queryAlarms(
            entities(service(CHECKOUT), service(CHECKOUT)), 10, 0)));
        assertEquals(Arrays.asList("a8", "a5", "a4", "a1"), uuids(dao.queryAlarms(
            entities(service(CHECKOUT), relation(CHECKOUT, PAYMENT)), 10, 0)));
    }

    @Test
    public void pagesThroughTheAlarmsOfAService() {
        final AlarmQueryCondition checkout = entities(service(CHECKOUT));
        assertEquals(Arrays.asList("a8", "a5"), uuids(dao.queryAlarms(checkout, 2, 0)));
        assertEquals(Arrays.asList("a4", "a1"), uuids(dao.queryAlarms(checkout, 2, 2)));
        assertEquals(Collections.emptyList(), uuids(dao.queryAlarms(checkout, 2, 4)));
    }

    /**
     * {@code Success count} is not the keyword {@code Successful}, and {@code owner} is not a searchable tag.
     */
    @Test
    public void filtersByLayerRuleNameKeywordAndTags() {
        final AlarmQueryCondition mesh = condition();
        mesh.setLayer(Layer.MESH.name());
        assertEquals(Arrays.asList("a8", "a7", "a2"), uuids(dao.queryAlarms(mesh, 10, 0)));
        final AlarmQueryCondition sla = condition();
        sla.setRuleNames(Collections.singletonList("service_sla_rule"));
        assertEquals(Arrays.asList("a5", "a2"), uuids(dao.queryAlarms(sla, 10, 0)));
        final AlarmQueryCondition responseTime = condition();
        responseTime.setRuleNames(Arrays.asList("service_resp_time_rule", "endpoint_resp_time_rule"));
        assertEquals(Arrays.asList("a3", "a1"), uuids(dao.queryAlarms(responseTime, 10, 0)));
        final AlarmQueryCondition keyword = condition();
        keyword.setKeyword("Successful");
        assertEquals(Arrays.asList("a5", "a2"), uuids(dao.queryAlarms(keyword, 10, 0)));
        final AlarmQueryCondition prod = condition();
        prod.setTags(tags("env=prod"));
        assertEquals(Arrays.asList("a6", "a5", "a3", "a1"), uuids(dao.queryAlarms(prod, 10, 0)));
        final AlarmQueryCondition teamA = condition();
        teamA.setTags(tags("env=prod", "team=a"));
        assertEquals(Collections.singletonList("a3"), uuids(dao.queryAlarms(teamA, 10, 0)));
        final AlarmQueryCondition owner = condition();
        owner.setTags(tags("owner=alice"));
        assertEquals(Collections.emptyList(), uuids(dao.queryAlarms(owner, 10, 0)));
    }

    @Test
    public void readsTheLegacyAlarmListByScopeKeywordAndTags() {
        assertEquals(Arrays.asList("a7", "a5", "a2", "a1"), uuids(legacy(DefaultScopeDefine.SERVICE, null, null)));
        assertEquals(Collections.singletonList("a6"), uuids(legacy(DefaultScopeDefine.SERVICE_INSTANCE, null, null)));
        assertEquals(Collections.singletonList("a3"), uuids(legacy(DefaultScopeDefine.ENDPOINT, null, null)));
        assertEquals(Arrays.asList("a8", "a4"), uuids(legacy(DefaultScopeDefine.SERVICE_RELATION, null, null)));
        assertEquals(Arrays.asList("a5", "a2"), uuids(legacy(null, "Successful", null)));
        assertEquals(Arrays.asList("a6", "a5", "a3", "a1"), uuids(legacy(null, null, tags("env=prod"))));
        assertEquals(Collections.singletonList("a3"), uuids(legacy(null, null, tags("env=prod", "team=a"))));
        assertEquals(Collections.emptyList(), uuids(legacy(null, null, tags("owner=alice"))));
    }

    @Test
    public void pagesThroughTheLegacyAlarmList() {
        assertEquals(Arrays.asList("a8", "a7", "a6"), uuids(dao.getAlarm(null, null, 3, 0, AROUND_MIDNIGHT, null)));
        assertEquals(Arrays.asList("a5", "a4", "a3"), uuids(dao.getAlarm(null, null, 3, 3, AROUND_MIDNIGHT, null)));
        assertEquals(Arrays.asList("a2", "a1"), uuids(dao.getAlarm(null, null, 3, 6, AROUND_MIDNIGHT, null)));
        assertEquals(Collections.emptyList(), uuids(dao.getAlarm(null, null, 3, 9, AROUND_MIDNIGHT, null)));
    }

    @Test
    public void readsEachAlarmWhole() {
        final List<String> expected = new ArrayList<>();
        for (int i = ALARMS.size() - 1; i >= 0; i--) {
            expected.add(ALARMS.get(i).render());
        }
        assertEquals(expected, render(dao.queryAlarms(condition(), 10, 0)));
        assertEquals(expected, render(dao.getAlarm(null, null, 10, 0, AROUND_MIDNIGHT, null)));
    }

    @Test
    public void readsTheRecoveryTimeOfEveryAlarmOfAPage() {
        final AlarmQueryCondition recovering = condition();
        recovering.setDuration(RECOVERING);
        final List<String> expected = new ArrayList<>();
        for (int i = RECOVERED_IN_ONE_PAGE - 1; i >= 0; i--) {
            final long startTime = RECOVERING.getStartTimestamp() + i * 1000L;
            expected.add(String.format("recovered-%02d", i) + "|" + (startTime + MINUTE));
        }
        assertEquals(expected, recoveries(dao.queryAlarms(recovering, 20, 0)));
        assertEquals(expected, recoveries(dao.getAlarm(null, null, 20, 0, RECOVERING, null)));
    }

    @Test
    public void keepsTheAlarmsWithinTheDuration() {
        final AlarmQueryCondition bounded = entities(service(BOUNDS));
        bounded.setDuration(BOUNDED);
        assertEquals(Arrays.asList("last-millisecond", "on-start"), uuids(dao.queryAlarms(bounded, 10, 0)));
        assertEquals(Arrays.asList("last-millisecond", "on-start"), uuids(dao.getAlarm(
            DefaultScopeDefine.SERVICE, null, 10, 0, BOUNDED, null)));
    }

    private Alarms legacy(final Integer scopeId, final String keyword, final List<Tag> tags) {
        return dao.getAlarm(scopeId, keyword, 10, 0, AROUND_MIDNIGHT, tags);
    }

    private static AlarmQueryCondition condition() {
        final AlarmQueryCondition condition = new AlarmQueryCondition();
        condition.setDuration(AROUND_MIDNIGHT);
        return condition;
    }

    private static AlarmQueryCondition entities(final Entity... entities) {
        final AlarmQueryCondition condition = condition();
        condition.setEntities(Arrays.asList(entities));
        return condition;
    }

    private static Entity service(final String name) {
        final Entity service = new Entity();
        service.setScope(Scope.Service);
        service.setServiceName(name);
        service.setNormal(true);
        return service;
    }

    private static Entity relation(final String source, final String dest) {
        final Entity relation = new Entity();
        relation.setScope(Scope.ServiceRelation);
        relation.setServiceName(source);
        relation.setDestServiceName(dest);
        return relation;
    }

    private static List<Tag> tags(final String... tags) {
        return Arrays.stream(tags).map(tag -> tag.split("=")).map(kv -> new Tag(kv[0], kv[1]))
                     .collect(Collectors.toList());
    }

    private static List<String> uuids(final Alarms alarms) {
        return alarms.getMsgs().stream().map(AlarmMessage::getUuid).collect(Collectors.toList());
    }

    private static List<String> recoveries(final Alarms alarms) {
        return alarms.getMsgs().stream().map(alarm -> alarm.getUuid() + "|" + alarm.getRecoveryTime())
                     .collect(Collectors.toList());
    }

    private static List<String> render(final Alarms alarms) {
        return alarms.getMsgs().stream().map(alarm -> String.join(
            "|", alarm.getUuid(), String.valueOf(alarm.getScopeId()), alarm.getId(), alarm.getId1(), alarm.getName(),
            alarm.getMessage(), String.valueOf(alarm.getStartTime()), String.valueOf(alarm.getRecoveryTime()),
            alarm.getTags().stream().map(tag -> tag.getKey() + "=" + tag.getValue()).collect(Collectors.joining(",")),
            alarm.getSnapshot().getExpression() + ":" + alarm.getSnapshot().getMetrics().stream()
                 .map(metric -> metric.getName() + "=" + metric.getResults().stream()
                     .flatMap(values -> values.getValues().stream()).map(MQEValue::getValue)
                     .collect(Collectors.joining(",")))
                 .collect(Collectors.joining(";"))
        )).collect(Collectors.toList());
    }

    private static Duration minutes(final long start, final long end) {
        return JDBCQueryITSupport.duration(start, end, Step.MINUTE);
    }

    private static String serviceId(final String service) {
        return IDManager.ServiceID.buildId(service, true);
    }

    /**
     * An alarm as the alarm persistence hands it to the record stream; a recovered one recovers a minute after it
     * starts.
     */
    private static final class AlarmRow {
        private static final List<String> SEARCHABLE_TAG_KEYS = Arrays.asList(SEARCHABLE_ALARM_TAGS.split(","));

        private final String uuid;
        private final long startTime;
        private final int scope;
        private final String id0;
        private final String id1;
        private final String name;
        private final String message;
        private final String ruleName;
        private final Layer layer;
        private List<String> tags = Collections.emptyList();
        private Long recoveryTime;
        private AlarmSnapshotRecord snapshot = new AlarmSnapshotRecord();

        private AlarmRow(final String uuid, final long startTime, final int scope, final String id0, final String id1,
                         final String name, final String message, final String ruleName, final Layer layer) {
            this.uuid = uuid;
            this.startTime = startTime;
            this.scope = scope;
            this.id0 = id0;
            this.id1 = id1;
            this.name = name;
            this.message = message;
            this.ruleName = ruleName;
            this.layer = layer;
        }

        static AlarmRow service(final String uuid, final long startTime, final String service, final String message,
                                final String ruleName, final Layer layer) {
            return new AlarmRow(uuid, startTime, DefaultScopeDefine.SERVICE, serviceId(service), "", service,
                                message, ruleName, layer);
        }

        static AlarmRow instance(final String uuid, final long startTime, final String service, final String instance,
                                 final String message, final String ruleName, final Layer layer) {
            return new AlarmRow(uuid, startTime, DefaultScopeDefine.SERVICE_INSTANCE,
                                IDManager.ServiceInstanceID.buildId(serviceId(service), instance), "",
                                instance + " of " + service, message, ruleName, layer);
        }

        static AlarmRow endpoint(final String uuid, final long startTime, final String service, final String endpoint,
                                 final String message, final String ruleName, final Layer layer) {
            return new AlarmRow(uuid, startTime, DefaultScopeDefine.ENDPOINT,
                                IDManager.EndpointID.buildId(serviceId(service), endpoint), "",
                                endpoint + " in " + service, message, ruleName, layer);
        }

        static AlarmRow relation(final String uuid, final long startTime, final String source, final String dest,
                                 final Layer layer) {
            return new AlarmRow(uuid, startTime, DefaultScopeDefine.SERVICE_RELATION, serviceId(source),
                                serviceId(dest), source + " to " + dest,
                                "Response time of service " + source + " to service " + dest + " is more than 1000ms",
                                "service_relation_resp_time_rule", layer);
        }

        AlarmRow tags(final String... tags) {
            this.tags = Arrays.asList(tags);
            return this;
        }

        AlarmRow recovered() {
            this.recoveryTime = startTime + MINUTE;
            return this;
        }

        AlarmRow snapshot(final String expression, final String metric, final String value) {
            final MQEValue point = new MQEValue();
            point.setId(String.valueOf(TimeBucket.getMinuteTimeBucket(startTime)));
            point.setValue(value);
            point.setDoubleValue(Double.parseDouble(value));
            final MQEValues values = new MQEValues();
            values.getValues().add(point);
            final JsonObject metrics = new JsonObject();
            metrics.addProperty(metric, GSON.toJson(Collections.singletonList(values)));
            snapshot.setExpression(expression);
            snapshot.setMetrics(metrics);
            return this;
        }

        AlarmRecord record() {
            final AlarmRecord record = new AlarmRecord();
            record.setUuid(uuid);
            record.setScope(scope);
            record.setId0(id0);
            record.setId1(id1);
            record.setName(name);
            record.setAlarmMessage(message);
            record.setStartTime(startTime);
            record.setTimeBucket(TimeBucket.getRecordTimeBucket(startTime));
            record.setRuleName(ruleName);
            record.setLayer(layer);
            record.setTagsRawData(GSON.toJson(tags.stream().map(tag -> tag.split("="))
                                                  .map(kv -> new Tag(kv[0], kv[1])).collect(Collectors.toList()))
                                      .getBytes(StandardCharsets.UTF_8));
            record.setTagsInString(tags.stream().filter(tag -> SEARCHABLE_TAG_KEYS.contains(tag.split("=")[0]))
                                       .collect(Collectors.toList()));
            record.setSnapshot(GSON.toJson(snapshot));
            return record;
        }

        AlarmRecoveryRecord recovery() {
            final AlarmRecoveryRecord recovery = new AlarmRecoveryRecord();
            recovery.setUuid(uuid);
            recovery.setRecoveryTime(recoveryTime);
            recovery.setTimeBucket(TimeBucket.getRecordTimeBucket(startTime));
            return recovery;
        }

        String render() {
            final String metrics = snapshot.getMetrics() == null ? "" : snapshot.getMetrics().keySet().stream()
                .map(metric -> metric + "=" + GSON.fromJson(snapshot.getMetrics().get(metric).getAsString(),
                                                            MQEValues[].class)[0].getValues().get(0).getValue())
                .collect(Collectors.joining(";"));
            return String.join("|", uuid, String.valueOf(scope), id0, id1, name, message, String.valueOf(startTime),
                               String.valueOf(recoveryTime), String.join(",", tags),
                               snapshot.getExpression() + ":" + metrics);
        }
    }
}
