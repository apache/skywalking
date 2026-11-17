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

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessDetectType;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteData;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.query.enumeration.ProfilingSupportStatus;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Process;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCQueryITSupport.MIDNIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads instances, the processes an eBPF agent reports and searchable tags the way the metadata query, the eBPF
 * receiver and tag autocompletion do, on traffic first stored on both sides of midnight, so in two day tables: a
 * traffic row stays in the table of the day it was first stored, however long it keeps pinging. A subclass names the
 * server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JDBCMetadataQueryITBase {
    private static final int METADATA_QUERY_MAX_SIZE = 5000;
    private static final String SERVICE_ID = IDManager.ServiceID.buildId("checkout", true);
    private static final String INSTANCE_FIRST_SEEN_YESTERDAY = "checkout-1";
    private static final String INSTANCE_FIRST_SEEN_YESTERDAY_ID =
        IDManager.ServiceInstanceID.buildId(SERVICE_ID, INSTANCE_FIRST_SEEN_YESTERDAY);
    private static final String INSTANCE_FIRST_SEEN_TODAY = "checkout-2";
    private static final String INSTANCE_FIRST_SEEN_TODAY_ID =
        IDManager.ServiceInstanceID.buildId(SERVICE_ID, INSTANCE_FIRST_SEEN_TODAY);
    private static final String AGENT_ID = "rover-node-1";
    /** Searchable trace tags first seen yesterday, as key and value. */
    private static final String[][] TAGS_YESTERDAY = {
        {"http.method", "GET"},
        {"http.status_code", "200"},
        {"http.status_code", "404"},
        {"db.type", "MySQL"},
        {"db.instance", "orders"},
    };
    /** Searchable trace tags first seen today, as key and value. */
    private static final String[][] TAGS_TODAY = {
        {"rpc.status_code", "OK"},
        {"mq.queue", "orders"},
        {"mq.topic", "payments"},
        {"mq.broker", "kafka:9092"},
        {"http.status_code", "500"},
        {"http.status_code", "503"},
    };

    private final JdbcDatabaseContainer<?> server;
    private final JDBCQueryITSupport.Target target;
    private JDBCQueryITSupport support;
    private JDBCMetadataQueryDAO metadataDAO;
    private JDBCTagAutoCompleteQueryDAO tagDAO;
    private long now;
    private Duration aroundMidnight;

    JDBCMetadataQueryITBase(final JdbcDatabaseContainer<?> server, final JDBCQueryITSupport.Target target) {
        this.server = server;
        this.target = target;
    }

    @BeforeAll
    public void writeTraffic() throws Exception {
        support = JDBCQueryITSupport.connect(server, target);
        // The fixtures' "now", anchored to the captured midnight, not the clock, so a run that crosses midnight
        // writes its rows into the day tables its reads look at.
        now = MIDNIGHT + 20 * 60_000L;
        final long yesterday = MIDNIGHT - 2 * 3_600_000L;

        support.writeMetrics(
            support.install(InstanceTraffic.class, DefaultScopeDefine.SERVICE_INSTANCE, InstanceTraffic.INDEX_NAME,
                            DownSampling.Minute),
            new InstanceTraffic.Builder(),
            Arrays.asList(
                instance(INSTANCE_FIRST_SEEN_YESTERDAY, yesterday, now),
                instance(INSTANCE_FIRST_SEEN_TODAY, now, now)));

        support.writeMetrics(
            support.install(ProcessTraffic.class, DefaultScopeDefine.PROCESS, ProcessTraffic.INDEX_NAME,
                            DownSampling.Minute),
            new ProcessTraffic.Builder(),
            Arrays.asList(
                process(INSTANCE_FIRST_SEEN_YESTERDAY_ID, "java-app", yesterday, now),
                process(INSTANCE_FIRST_SEEN_TODAY_ID, "envoy", now, now),
                process(INSTANCE_FIRST_SEEN_YESTERDAY_ID, "batch-job", yesterday, now - 30 * 60_000L)));

        final List<TagAutocompleteData> tags = new ArrayList<>();
        for (final String[] tag : TAGS_YESTERDAY) {
            tags.add(tag(tag, MIDNIGHT - 30 * 60_000L));
        }
        for (final String[] tag : TAGS_TODAY) {
            tags.add(tag(tag, now));
        }
        support.writeMetrics(
            support.install(TagAutocompleteData.class, DefaultScopeDefine.TAG_AUTOCOMPLETE,
                            TagAutocompleteData.INDEX_NAME, DownSampling.Minute),
            new TagAutocompleteData.Builder(), tags);

        metadataDAO = new JDBCMetadataQueryDAO(support.getClient(), METADATA_QUERY_MAX_SIZE, support.getModuleManager());
        tagDAO = new JDBCTagAutoCompleteQueryDAO(support.getClient(), support.getTableHelper());
        aroundMidnight = JDBCQueryITSupport.duration(MIDNIGHT - 30 * 60_000L, MIDNIGHT + 30 * 60_000L, Step.MINUTE);
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    @Test
    public void readsTheInstancesOfEveryDayTable() throws Exception {
        assertEquals(new TreeSet<>(Arrays.asList(INSTANCE_FIRST_SEEN_YESTERDAY, INSTANCE_FIRST_SEEN_TODAY)),
                     instanceNames(INSTANCE_FIRST_SEEN_YESTERDAY_ID, INSTANCE_FIRST_SEEN_TODAY_ID));
        assertEquals(Collections.singleton(INSTANCE_FIRST_SEEN_YESTERDAY),
                     instanceNames(INSTANCE_FIRST_SEEN_YESTERDAY_ID));
    }

    /**
     * The eBPF receiver asks for the processes of an agent that pinged in the last five minutes; batch-job stopped
     * pinging half an hour ago.
     */
    @Test
    public void listsTheProcessesOfAnAgentThatPingedInTheRange() throws Exception {
        final List<Process> processes = metadataDAO.listProcesses(
            AGENT_ID, TimeBucket.getMinuteTimeBucket(now - 5 * 60_000L), TimeBucket.getMinuteTimeBucket(now));
        assertEquals(new TreeSet<>(Arrays.asList("envoy", "java-app")),
                     processes.stream().map(Process::getName).collect(Collectors.toCollection(TreeSet::new)));
    }

    @Test
    public void limitsTheTagKeysOfBothDays() throws Exception {
        final Set<String> keys = new TreeSet<>();
        for (final String[] tag : TAGS_YESTERDAY) {
            keys.add(tag[0]);
        }
        for (final String[] tag : TAGS_TODAY) {
            keys.add(tag[0]);
        }
        assertEquals(keys, tagDAO.queryTagAutocompleteKeys(TagType.TRACE, 100, aroundMidnight));

        final Set<String> limited = tagDAO.queryTagAutocompleteKeys(TagType.TRACE, 5, aroundMidnight);
        assertEquals(5, limited.size());
        assertTrue(keys.containsAll(limited));
    }

    @Test
    public void limitsTheTagValuesOfBothDays() throws Exception {
        final Set<String> values = new TreeSet<>(Arrays.asList("200", "404", "500", "503"));
        assertEquals(values, tagDAO.queryTagAutocompleteValues(TagType.TRACE, "http.status_code", 100, aroundMidnight));

        final Set<String> limited = tagDAO.queryTagAutocompleteValues(TagType.TRACE, "http.status_code", 3, aroundMidnight);
        assertEquals(3, limited.size());
        assertTrue(values.containsAll(limited));
    }

    private Set<String> instanceNames(final String... ids) throws Exception {
        return metadataDAO.getInstances(Arrays.asList(ids))
                          .stream()
                          .map(ServiceInstance::getName)
                          .collect(Collectors.toCollection(TreeSet::new));
    }

    private static InstanceTraffic instance(final String name, final long firstSeen, final long lastPing) {
        final JsonObject properties = new JsonObject();
        properties.addProperty(InstanceTraffic.PropertyUtil.LANGUAGE, "java");
        final InstanceTraffic instance = new InstanceTraffic();
        instance.setServiceId(SERVICE_ID);
        instance.setName(name);
        instance.setProperties(properties);
        instance.setTimeBucket(TimeBucket.getMinuteTimeBucket(firstSeen));
        instance.setLastPingTimestamp(TimeBucket.getMinuteTimeBucket(lastPing));
        return instance;
    }

    private static ProcessTraffic process(final String instanceId, final String name, final long firstSeen,
                                          final long lastPing) {
        final ProcessTraffic process = new ProcessTraffic();
        process.setServiceId(SERVICE_ID);
        process.setInstanceId(instanceId);
        process.setName(name);
        process.setAgentId(AGENT_ID);
        process.setDetectType(ProcessDetectType.VM.value());
        process.setProfilingSupportStatus(ProfilingSupportStatus.SUPPORT_EBPF_PROFILING.value());
        process.setLabelsJson(Const.EMPTY_STRING);
        process.setTimeBucket(TimeBucket.getMinuteTimeBucket(firstSeen));
        process.setLastPingTimestamp(TimeBucket.getMinuteTimeBucket(lastPing));
        return process;
    }

    private static TagAutocompleteData tag(final String[] tag, final long firstSeen) {
        final TagAutocompleteData data = new TagAutocompleteData();
        data.setTagKey(tag[0]);
        data.setTagValue(tag[1]);
        data.setTagType(TagType.TRACE.name());
        data.setTimeBucket(TimeBucket.retainToDay4MinuteBucket(TimeBucket.getMinuteTimeBucket(firstSeen)));
        return data;
    }
}
