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
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ServiceLabelRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteData;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingScheduleRecord;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBEBPFProfilingScheduleQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBNetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBServiceLabelDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBTagAutocompleteQueryDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Reads the smaller index-mode measures through their DAOs: tag autocomplete keys and values, grouped and bounded to
 * the days of the window; network address aliases updated since a minute, an update included; the labels of a
 * service; and the eBPF profiling schedules of a task, latest start first.
 */
@Testcontainers
public class BanyanDBIndexModeQueryIT {
    private static final String SERVICE_ID = IDManager.ServiceID.buildId("index-mode-it", true);
    private static final String OTHER_SERVICE_ID = IDManager.ServiceID.buildId("index-mode-it-other", true);

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static long NOW_MINUTE;

    @BeforeAll
    public static void writeIndexModeData() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        NOW_MINUTE = System.currentTimeMillis() / 60_000L * 60_000L;

        final List<TagAutocompleteData> tags = new ArrayList<>();
        for (final String[] tag : new String[][] {
            {"TRACE", "http.method", "GET"}, {"TRACE", "http.method", "POST"}, {"TRACE", "status_code", "200"},
            {"TRACE", "db.type", "mysql"}, {"LOG", "level", "ERROR"}}) {
            // Each tag is seen twice today: the autocomplete groups them.
            tags.add(tag(tag, NOW_MINUTE - 60_000L));
            tags.add(tag(tag, NOW_MINUTE - 120_000L));
        }
        // Seen only three days ago: outside the days of the window.
        tags.add(tag(new String[] {"TRACE", "old.key", "old"}, NOW_MINUTE - TimeUnit.DAYS.toMillis(3)));
        SUPPORT.writeMetrics(SUPPORT.installMetrics(TagAutocompleteData.class, DefaultScopeDefine.TAG_AUTOCOMPLETE,
                                                    TagAutocompleteData.INDEX_NAME),
                             new TagAutocompleteData.Builder(), tags);

        final var aliasModel = SUPPORT.installMetrics(
            NetworkAddressAlias.class, DefaultScopeDefine.NETWORK_ADDRESS_ALIAS, NetworkAddressAlias.INDEX_NAME);
        SUPPORT.writeMetrics(aliasModel, new NetworkAddressAlias.Builder(), Arrays.asList(
            alias("10.0.0.1:80", 10), alias("10.0.0.2:80", 20), alias("10.0.0.3:80", 30)));
        SUPPORT.updateMetrics(aliasModel, new NetworkAddressAlias.Builder(),
                              Collections.singletonList(alias("10.0.0.1:80", 25)));

        SUPPORT.writeMetrics(SUPPORT.installMetrics(ServiceLabelRecord.class, DefaultScopeDefine.SERVICE_LABEL,
                                                    ServiceLabelRecord.INDEX_NAME),
                             new ServiceLabelRecord.Builder(), Arrays.asList(
                label(SERVICE_ID, "env:prod"), label(SERVICE_ID, "team:a"), label(OTHER_SERVICE_ID, "env:test")));

        SUPPORT.writeMetrics(SUPPORT.installMetrics(EBPFProfilingScheduleRecord.class, DefaultScopeDefine.EBPF_PROFILING_SCHEDULE,
                                                    EBPFProfilingScheduleRecord.INDEX_NAME),
                             new EBPFProfilingScheduleRecord.Builder(), Arrays.asList(
                schedule("task-read", "schedule-a", 5), schedule("task-read", "schedule-b", 1),
                schedule("task-read", "schedule-c", 3), schedule("task-other", "schedule-d", 4)));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(2,
            new BanyanDBServiceLabelDAO(SUPPORT.getClient(), SUPPORT.getConfig()).queryAllLabels(SERVICE_ID).size()));
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void autocompletesTagKeysAndValuesOfTheWindow() throws IOException {
        final BanyanDBTagAutocompleteQueryDAO dao = new BanyanDBTagAutocompleteQueryDAO(SUPPORT.getClient());
        assertEquals(Arrays.asList("db.type", "http.method", "status_code"),
                     new ArrayList<>(new TreeSet<>(dao.queryTagAutocompleteKeys(List.of(TagType.TRACE.name()), 10, today()))));
        assertEquals(Collections.singletonList("level"),
                     new ArrayList<>(new TreeSet<>(dao.queryTagAutocompleteKeys(List.of(TagType.LOG.name()), 10, today()))));
        assertEquals(Arrays.asList("GET", "POST"),
                     new ArrayList<>(new TreeSet<>(dao.queryTagAutocompleteValues(List.of(TagType.TRACE.name()), "http.method", 10, today()))));
        assertEquals(2, dao.queryTagAutocompleteKeys(List.of(TagType.TRACE.name()), 2, today()).size());
    }

    @Test
    public void loadsTheAliasesUpdatedSinceAMinute() {
        final BanyanDBNetworkAddressAliasDAO dao = new BanyanDBNetworkAddressAliasDAO(SUPPORT.getClient(), SUPPORT.getConfig());
        assertEquals(Arrays.asList("10.0.0.1:80|" + minuteBucket(25), "10.0.0.2:80|" + minuteBucket(20),
                                   "10.0.0.3:80|" + minuteBucket(30)),
                     dao.loadLastUpdate(minuteBucket(20)).stream()
                        .map(a -> a.getAddress() + "|" + a.getLastUpdateTimeBucket())
                        .sorted().collect(Collectors.toList()));
        assertEquals(Collections.singletonList("10.0.0.3:80"),
                     dao.loadLastUpdate(minuteBucket(26)).stream().map(NetworkAddressAlias::getAddress).collect(Collectors.toList()));
    }

    @Test
    public void readsTheLabelsOfAService() throws IOException {
        assertEquals(Arrays.asList("env:prod", "team:a"),
                     new BanyanDBServiceLabelDAO(SUPPORT.getClient(), SUPPORT.getConfig()).queryAllLabels(SERVICE_ID)
                                                                                          .stream().sorted().collect(Collectors.toList()));
    }

    @Test
    public void readsTheSchedulesOfATaskLatestFirst() throws IOException {
        assertEquals(Arrays.asList("schedule-a", "schedule-c", "schedule-b"),
                     new BanyanDBEBPFProfilingScheduleQueryDAO(SUPPORT.getClient()).querySchedules("task-read")
                         .stream().map(s -> s.getScheduleId()).collect(Collectors.toList()));
    }

    private static Duration today() {
        final Duration duration = new Duration();
        duration.setStep(Step.MINUTE);
        duration.setStart(DurationUtils.YYYY_MM_DD_HHMM.print(NOW_MINUTE - 30 * 60_000L));
        duration.setEnd(DurationUtils.YYYY_MM_DD_HHMM.print(NOW_MINUTE));
        return duration;
    }

    private static long minuteBucket(int minutesAgo) {
        return TimeBucket.getMinuteTimeBucket(NOW_MINUTE - TimeUnit.HOURS.toMillis(2) + minutesAgo * 60_000L);
    }

    private static TagAutocompleteData tag(String[] tag, long time) {
        final TagAutocompleteData data = new TagAutocompleteData();
        data.setTagType(tag[0]);
        data.setTagKey(tag[1]);
        data.setTagValue(tag[2]);
        data.setTimeBucket(TimeBucket.getMinuteTimeBucket(time));
        return data;
    }

    private static NetworkAddressAlias alias(String address, int updatedMinute) {
        final NetworkAddressAlias alias = new NetworkAddressAlias();
        alias.setAddress(address);
        alias.setRepresentServiceId(SERVICE_ID);
        alias.setRepresentServiceInstanceId(IDManager.ServiceInstanceID.buildId(SERVICE_ID, "instance-0"));
        alias.setLastUpdateTimeBucket(minuteBucket(updatedMinute));
        alias.setTimeBucket(minuteBucket(0));
        return alias;
    }

    private static ServiceLabelRecord label(String serviceId, String label) {
        final ServiceLabelRecord record = new ServiceLabelRecord();
        record.setServiceId(serviceId);
        record.setLabel(label);
        record.setTimeBucket(minuteBucket(0));
        return record;
    }

    private static EBPFProfilingScheduleRecord schedule(String taskId, String scheduleId, int startMinute) {
        final EBPFProfilingScheduleRecord schedule = new EBPFProfilingScheduleRecord();
        schedule.setTaskId(taskId);
        schedule.setScheduleId(scheduleId);
        schedule.setProcessId("process-" + scheduleId);
        schedule.setStartTime(NOW_MINUTE - TimeUnit.HOURS.toMillis(1) + startMinute * 60_000L);
        schedule.setEndTime(NOW_MINUTE - TimeUnit.HOURS.toMillis(1) + (startMinute + 1) * 60_000L);
        schedule.setTimeBucket(minuteBucket(0));
        return schedule;
    }
}
