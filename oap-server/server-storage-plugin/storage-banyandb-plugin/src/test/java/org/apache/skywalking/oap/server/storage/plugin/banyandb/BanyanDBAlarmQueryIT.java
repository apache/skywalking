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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecoveryRecord;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.input.AlarmQueryCondition;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBAlarmQueryDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Reads alarms the way both alarm queries do — the legacy {@code getAlarm} and {@code queryAlarms} — on the real
 * {@code alarm_record} stream, with the recovery time each alarm has in {@code alarm_recovery_record}: newest first,
 * page after page with or without entities, and by scope, message keyword, tags, layer and rule name.
 */
@Testcontainers
public class BanyanDBAlarmQueryIT {
    private static final int PAGE_SIZE = 2;
    private static final String SERVICE = "alarm-query-it";
    private static final String OTHER_SERVICE = "alarm-query-it-other";
    private static final String RESP = "Response time of service %s is more than 1000ms";
    private static final String SLA = "Successful rate of service %s is lower than 80%%";
    /** Alarms one second apart: service, rule, layer, message, tags, and whether it recovered. */
    private static final Object[][] ALARMS = {
        {SERVICE, "resp_rule", Layer.GENERAL, RESP, new String[] {"env=prod"}, true},
        {OTHER_SERVICE, "sla_rule", Layer.MESH, SLA, new String[] {"env=test"}, false},
        {SERVICE, "resp_rule", Layer.GENERAL, RESP, new String[] {"env=prod", "team=a"}, false},
        {OTHER_SERVICE, "resp_rule", Layer.MESH, RESP, new String[0], false},
        {SERVICE, "sla_rule", Layer.GENERAL, SLA, new String[] {"env=prod"}, true},
        {null, "endpoint_rule", Layer.GENERAL, "Response time of endpoint /pay is more than 1000ms", new String[0], false},
    };

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBAlarmQueryDAO QUERY_DAO;
    private static Duration DURATION;
    private static long FIRST;

    @BeforeAll
    public static void writeAlarms() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        final Model model = SUPPORT.install(AlarmRecord.class, DefaultScopeDefine.ALARM, AlarmRecord.INDEX_NAME);
        final Model recoveryModel = SUPPORT.install(
            AlarmRecoveryRecord.class, DefaultScopeDefine.ALARM_RECOVERY, AlarmRecoveryRecord.INDEX_NAME);
        FIRST = System.currentTimeMillis() - ALARMS.length * 1000L;
        final List<AlarmRecord> alarms = new ArrayList<>();
        final List<AlarmRecoveryRecord> recoveries = new ArrayList<>();
        for (int i = 0; i < ALARMS.length; i++) {
            alarms.add(alarm(i));
            if ((Boolean) ALARMS[i][5]) {
                final AlarmRecoveryRecord recovery = new AlarmRecoveryRecord();
                recovery.setUuid(uuid(i));
                recovery.setRecoveryTime(recoveryTime(i));
                recovery.setTimeBucket(TimeBucket.getRecordTimeBucket(recoveryTime(i)));
                recoveries.add(recovery);
            }
        }
        SUPPORT.write(model, new AlarmRecord.Builder(), alarms);
        SUPPORT.write(recoveryModel, new AlarmRecoveryRecord.Builder(), recoveries);

        QUERY_DAO = new BanyanDBAlarmQueryDAO(SUPPORT.getClient());
        DURATION = BanyanDBQueryITSupport.around(FIRST, recoveryTime(ALARMS.length));
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(ALARMS.length, uuids(QUERY_DAO.queryAlarms(condition(), 10, 0)).size());
            assertEquals(recoveryTime(0), (long) QUERY_DAO.queryAlarms(condition(), 10, 0).getMsgs()
                .get(ALARMS.length - 1).getRecoveryTime());
        });
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void pagesThroughEveryAlarm() throws IOException {
        assertPages(condition(), Arrays.asList(uuid(5), uuid(4), uuid(3), uuid(2), uuid(1), uuid(0)));
    }

    @Test
    public void pagesThroughTheAlarmsOfOneService() throws IOException {
        final AlarmQueryCondition condition = condition();
        condition.setEntities(Collections.singletonList(service(SERVICE)));
        assertPages(condition, Arrays.asList(uuid(4), uuid(2), uuid(0)));
    }

    @Test
    public void readsAnAlarmOnceWhenTwoEntitiesMatchIt() throws IOException {
        final AlarmQueryCondition condition = condition();
        condition.setEntities(Arrays.asList(service(SERVICE), service(SERVICE)));
        assertEquals(Arrays.asList(uuid(4), uuid(2), uuid(0)), uuids(QUERY_DAO.queryAlarms(condition, 10, 0)));
    }

    @Test
    public void filtersByLayerRuleKeywordAndTags() throws IOException {
        final AlarmQueryCondition mesh = condition();
        mesh.setLayer(Layer.MESH.name());
        assertEquals(Arrays.asList(uuid(3), uuid(1)), uuids(QUERY_DAO.queryAlarms(mesh, 10, 0)));
        final AlarmQueryCondition sla = condition();
        sla.setRuleNames(Collections.singletonList("sla_rule"));
        assertEquals(Arrays.asList(uuid(4), uuid(1)), uuids(QUERY_DAO.queryAlarms(sla, 10, 0)));
        final AlarmQueryCondition rules = condition();
        rules.setRuleNames(Arrays.asList("resp_rule", "endpoint_rule"));
        assertEquals(Arrays.asList(uuid(5), uuid(3), uuid(2), uuid(0)), uuids(QUERY_DAO.queryAlarms(rules, 10, 0)));
        final AlarmQueryCondition keyword = condition();
        keyword.setKeyword("Successful");
        assertEquals(Arrays.asList(uuid(4), uuid(1)), uuids(QUERY_DAO.queryAlarms(keyword, 10, 0)));
        final AlarmQueryCondition tags = condition();
        tags.setTags(Arrays.asList(new Tag("env", "prod"), new Tag("team", "a")));
        assertEquals(Collections.singletonList(uuid(2)), uuids(QUERY_DAO.queryAlarms(tags, 10, 0)));
    }

    @Test
    public void readsTheLegacyAlarmListByScopeKeywordAndTags() throws IOException {
        assertEquals(Arrays.asList(uuid(4), uuid(3), uuid(2), uuid(1), uuid(0)),
                     uuids(QUERY_DAO.getAlarm(DefaultScopeDefine.SERVICE, null, 10, 0, DURATION, null)));
        assertEquals(Collections.singletonList(uuid(5)),
                     uuids(QUERY_DAO.getAlarm(DefaultScopeDefine.ENDPOINT, null, 10, 0, DURATION, null)));
        assertEquals(Arrays.asList(uuid(4), uuid(1)),
                     uuids(QUERY_DAO.getAlarm(null, "Successful", 10, 0, DURATION, null)));
        assertEquals(Arrays.asList(uuid(4), uuid(2), uuid(0)),
                     uuids(QUERY_DAO.getAlarm(null, null, 10, 0, DURATION, Collections.singletonList(new Tag("env", "prod")))));
        assertEquals(Arrays.asList(uuid(3), uuid(2)), uuids(QUERY_DAO.getAlarm(null, null, 2, 2, DURATION, null)));
    }

    @Test
    public void readsEachAlarmWhole() throws IOException {
        final List<String> expected = new ArrayList<>();
        for (int i = ALARMS.length - 1; i >= 0; i--) {
            expected.add(String.join("|", uuid(i), id0(i), message(i), String.valueOf(FIRST + i * 1000L),
                                     (Boolean) ALARMS[i][5] ? String.valueOf(recoveryTime(i)) : "null"));
        }
        assertEquals(expected, QUERY_DAO.queryAlarms(condition(), 10, 0).getMsgs().stream()
            .map(m -> String.join("|", m.getUuid(), m.getId(), m.getMessage(), String.valueOf(m.getStartTime()),
                                  String.valueOf(m.getRecoveryTime())))
            .collect(Collectors.toList()));
    }

    /** Reads page after page of {@link #PAGE_SIZE}, then one more past the end, which must be empty. */
    private static void assertPages(AlarmQueryCondition condition, List<String> newestFirst) throws IOException {
        for (int from = 0; from < newestFirst.size(); from += PAGE_SIZE) {
            assertEquals(newestFirst.subList(from, Math.min(from + PAGE_SIZE, newestFirst.size())),
                         uuids(QUERY_DAO.queryAlarms(condition, PAGE_SIZE, from)), "from " + from);
        }
        final int pastTheEnd = (newestFirst.size() + PAGE_SIZE - 1) / PAGE_SIZE * PAGE_SIZE;
        assertEquals(Collections.emptyList(), uuids(QUERY_DAO.queryAlarms(condition, PAGE_SIZE, pastTheEnd)));
    }

    private static List<String> uuids(Alarms alarms) {
        return alarms.getMsgs().stream().map(AlarmMessage::getUuid).collect(Collectors.toList());
    }

    private static AlarmQueryCondition condition() {
        final AlarmQueryCondition condition = new AlarmQueryCondition();
        condition.setDuration(DURATION);
        return condition;
    }

    private static Entity service(String name) {
        final Entity service = new Entity();
        service.setScope(Scope.Service);
        service.setServiceName(name);
        service.setNormal(true);
        return service;
    }

    private static String id0(int i) {
        final String service = (String) ALARMS[i][0];
        return service == null
            ? IDManager.EndpointID.buildId(IDManager.ServiceID.buildId(SERVICE, true), "/pay")
            : IDManager.ServiceID.buildId(service, true);
    }

    private static String message(int i) {
        final String service = (String) ALARMS[i][0];
        return service == null ? (String) ALARMS[i][3] : String.format((String) ALARMS[i][3], service);
    }

    private static long recoveryTime(int i) {
        return FIRST + 100_000L + i * 1000L;
    }

    private static AlarmRecord alarm(int i) {
        final String service = (String) ALARMS[i][0];
        final long startTime = FIRST + i * 1000L;
        final AlarmRecord alarm = new AlarmRecord();
        alarm.setUuid(uuid(i));
        alarm.setScope(service == null ? DefaultScopeDefine.ENDPOINT : DefaultScopeDefine.SERVICE);
        alarm.setName(service == null ? "/pay in " + SERVICE : service);
        alarm.setId0(id0(i));
        alarm.setId1("");
        alarm.setAlarmMessage(message(i));
        alarm.setRuleName((String) ALARMS[i][1]);
        alarm.setLayer((Layer) ALARMS[i][2]);
        alarm.setTagsInString(Arrays.asList((String[]) ALARMS[i][4]));
        alarm.setTagsRawData(new byte[0]);
        alarm.setStartTime(startTime);
        alarm.setTimeBucket(TimeBucket.getRecordTimeBucket(startTime));
        return alarm;
    }

    private static String uuid(int index) {
        return "alarm-" + index;
    }
}
