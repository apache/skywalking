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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.NoneStreamProcessor;
import org.apache.skywalking.oap.server.core.profiling.ebpf.EBPFProfilingMutationService;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingDataRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingScheduleRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.query.type.EBPFNetworkKeepProfilingResult;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingSchedule;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTaskContinuousProfiling;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelRegistry;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ElasticSearchQueryITSupport;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexNames;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.StorageEsDAO;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reads eBPF profiling tasks, schedules and data the way the eBPF receiver, the profiling query and mutation
 * services and the profiling analysis do, through {@link EBPFProfilingTaskEsDAO}, {@link EBPFProfilingScheduleEsDAO}
 * and {@link EBPFProfilingDataEsDAO}, from what OAP's own write paths stored in Elasticsearch or OpenSearch. A nested
 * class of a server's IT names the server and the storage mode.
 *
 * <p>The tasks are created on both sides of midnight, so in two daily indices: fixed-time tasks of two services, a
 * network task of an instance with the copy that extends it after midnight (a second row with the same logical id),
 * and continuous-profiling tasks of two instances. Each filter has a near miss, and the start and update times lie
 * on and just outside the bounds asked for. A task search returns the tasks newest created first, cut to
 * {@code profileTaskQueryMaxSize}, set here below the tasks of a service. The schedules of a task start on both sides
 * of midnight beside a schedule of another task. The eBPF data of a schedule is uploaded every half second through a
 * window that spans midnight, with uploads just before the window and on its end, beside the uploads of other
 * schedules, and a schedule has more stacks in the window than a scroll page ({@code profileDataQueryBatchSize}).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class EBPFProfilingQueryITBase {
    /** A fixed local midnight, so the daily indices the rows are written to are known. */
    private static final long MIDNIGHT = new DateTime(2026, 1, 15, 0, 0).getMillis();
    /** Fewer than the tasks of the service, which are six. */
    private static final int TASKS_PER_QUERY = 5;
    /** Fewer than one schedule's stacks in the window. */
    private static final int STACKS_PER_PAGE = 7;
    private static final Gson GSON = new Gson();
    private static final String SERVICE_ID = IDManager.ServiceID.buildId("checkout", true);
    private static final String OTHER_SERVICE_ID = IDManager.ServiceID.buildId("payment", true);
    private static final String INSTANCE_ID = IDManager.ServiceInstanceID.buildId(SERVICE_ID, "checkout-1");
    private static final String OTHER_INSTANCE_ID = IDManager.ServiceInstanceID.buildId(SERVICE_ID, "checkout-2");

    private static final EBPFProfilingTaskRecord ON_CPU = fixedTimeTask(
        SERVICE_ID, Collections.emptyList(), EBPFProfilingTargetType.ON_CPU, minutes(-30), minutes(-30));
    /** Created five minutes after midnight to start five minutes later, for processes with a label. */
    private static final EBPFProfilingTaskRecord OFF_CPU = fixedTimeTask(
        SERVICE_ID, Collections.singletonList("env:prod"), EBPFProfilingTargetType.OFF_CPU, minutes(5), minutes(10));
    private static final EBPFProfilingTaskRecord OTHER_SERVICE_ON_CPU = fixedTimeTask(
        OTHER_SERVICE_ID, Collections.emptyList(), EBPFProfilingTargetType.ON_CPU, minutes(-10), minutes(-10));
    private static final EBPFProfilingTaskRecord NETWORK = networkTask(INSTANCE_ID, minutes(-20));
    private static final EBPFProfilingTaskRecord NETWORK_EXTENDED = extended(NETWORK, minutes(1));
    private static final EBPFProfilingTaskRecord CONTINUOUS_ON_CPU = continuousProfilingTask(
        INSTANCE_ID, EBPFProfilingTargetType.ON_CPU, minutes(-5));
    private static final EBPFProfilingTaskRecord CONTINUOUS_OFF_CPU = continuousProfilingTask(
        OTHER_INSTANCE_ID, EBPFProfilingTargetType.OFF_CPU, minutes(3));
    /** Neither in creation order nor against it. */
    private static final List<EBPFProfilingTaskRecord> TASKS = Arrays.asList(
        NETWORK_EXTENDED, ON_CPU, CONTINUOUS_OFF_CPU, OTHER_SERVICE_ON_CPU, OFF_CPU, NETWORK, CONTINUOUS_ON_CPU);
    private static final List<EBPFProfilingTargetType> ALL_TARGETS = Arrays.asList(EBPFProfilingTargetType.values());
    private static final List<EBPFProfilingTargetType> CPU =
        Arrays.asList(EBPFProfilingTargetType.ON_CPU, EBPFProfilingTargetType.OFF_CPU);

    private static final EBPFProfilingScheduleRecord FIRST_SCHEDULE =
        schedule(NETWORK.getLogicalId(), INSTANCE_ID, "checkout", minutes(-15), minutes(-14));
    /** Started before midnight and still running after it. */
    private static final EBPFProfilingScheduleRecord ACROSS_MIDNIGHT_SCHEDULE =
        schedule(NETWORK.getLogicalId(), INSTANCE_ID, "checkout-worker", minutes(-3), minutes(2));
    private static final EBPFProfilingScheduleRecord LAST_SCHEDULE =
        schedule(NETWORK.getLogicalId(), INSTANCE_ID, "checkout", minutes(5), minutes(6));
    private static final EBPFProfilingScheduleRecord OTHER_TASK_SCHEDULE =
        schedule(ON_CPU.getLogicalId(), INSTANCE_ID, "checkout", minutes(-1), minutes(0));

    private static final String SCHEDULE = "schedule-read";
    private static final String OTHER_SCHEDULE = "schedule-also-read";
    private static final String UNASKED_SCHEDULE = "schedule-not-read";
    private static final long WINDOW_BEGIN = MIDNIGHT - 5_000L;
    private static final long WINDOW_END = MIDNIGHT + 5_000L;
    private static final int UPLOADS = 20;
    private static final int STACKS_PER_UPLOAD = 3;

    private final ElasticsearchContainer server;
    private final ElasticSearchQueryITSupport.Mode mode;
    private ElasticSearchQueryITSupport support;
    private Model tasks;
    private EBPFProfilingTaskEsDAO taskDAO;
    private EBPFProfilingScheduleEsDAO scheduleDAO;
    private EBPFProfilingDataEsDAO dataDAO;
    private final List<EBPFProfilingDataRecord> windowOfSchedule = new ArrayList<>();
    private final List<EBPFProfilingDataRecord> windowOfOtherSchedule = new ArrayList<>();
    private final List<EBPFProfilingDataRecord> beforeWindow = new ArrayList<>();
    private final List<EBPFProfilingDataRecord> onWindowEnd = new ArrayList<>();

    EBPFProfilingQueryITBase(final ElasticsearchContainer server, final ElasticSearchQueryITSupport.Mode mode) {
        this.server = server;
        this.mode = mode;
    }

    @BeforeAll
    public void writeProfiling() throws Exception {
        support = ElasticSearchQueryITSupport.connect(server, mode, getClass(), config -> {
            config.setProfileTaskQueryMaxSize(TASKS_PER_QUERY);
            config.setProfileDataQueryBatchSize(STACKS_PER_PAGE);
        });

        // Every model is installed before the first write, as at boot: in OAP's default mode the tasks and the data
        // share one physical table, whose dated indices take their mappings when the first write creates them.
        tasks = support.installNoneStream(EBPFProfilingTaskRecord.class);
        final Model schedules = support.installMetrics(EBPFProfilingScheduleRecord.class);
        final Model dataModel = support.installRecord(EBPFProfilingDataRecord.class);

        for (final EBPFProfilingTaskRecord task : TASKS) {
            support.writeNoneStream(tasks, new EBPFProfilingTaskRecord.Builder(), task);
        }
        support.writeMetrics(schedules, new EBPFProfilingScheduleRecord.Builder(), Arrays.asList(
            ACROSS_MIDNIGHT_SCHEDULE, OTHER_TASK_SCHEDULE, FIRST_SCHEDULE, LAST_SCHEDULE));

        for (int upload = 0; upload < UPLOADS; upload++) {
            for (int stack = 0; stack < STACKS_PER_UPLOAD; stack++) {
                windowOfSchedule.add(data(SCHEDULE, upload + "-" + stack, WINDOW_BEGIN + upload * 500L));
            }
        }
        for (int stack = 0; stack < STACKS_PER_UPLOAD; stack++) {
            beforeWindow.add(data(SCHEDULE, "before-" + stack, WINDOW_BEGIN - 1));
            onWindowEnd.add(data(SCHEDULE, "end-" + stack, WINDOW_END));
            windowOfOtherSchedule.add(data(OTHER_SCHEDULE, "other-" + stack, MIDNIGHT - 1_000L));
            windowOfOtherSchedule.add(data(OTHER_SCHEDULE, "other-later-" + stack, MIDNIGHT + 1_000L));
        }
        final List<EBPFProfilingDataRecord> data = new ArrayList<>(windowOfSchedule);
        data.addAll(windowOfOtherSchedule);
        data.addAll(beforeWindow);
        data.addAll(onWindowEnd);
        data.add(data(UNASKED_SCHEDULE, "unasked", MIDNIGHT));
        support.writeRecords(dataModel, new EBPFProfilingDataRecord.Builder(), data);

        taskDAO = new EBPFProfilingTaskEsDAO(support.getClient(), support.getConfig());
        scheduleDAO = new EBPFProfilingScheduleEsDAO(support.getClient(), support.getConfig());
        dataDAO = new EBPFProfilingDataEsDAO(support.getClient(), support.getConfig());
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    /**
     * The rover asks for the fixed-time tasks of the services of its processes: neither the tasks of another
     * service nor the continuous-profiling tasks, which the rover triggers itself.
     */
    @Test
    public void readsTheFixedTimeTasksOfTheRoversServicesNewestFirst() throws Exception {
        assertEquals(describe(OFF_CPU, NETWORK_EXTENDED, NETWORK, ON_CPU), describe(taskDAO.queryTasksByServices(
            Collections.singletonList(SERVICE_ID), EBPFProfilingTriggerType.FIXED_TIME, 0, 0)));
        assertEquals(describe(OTHER_SERVICE_ON_CPU), describe(taskDAO.queryTasksByServices(
            Collections.singletonList(OTHER_SERVICE_ID), null, 0, 0)));
    }

    /**
     * The rover asks for the tasks updated after its last poll: a task updated at that time is not read again, and
     * the copy extending the network task after midnight is.
     */
    @Test
    public void readsTheTasksUpdatedAfterTheRoversLastPoll() throws Exception {
        assertEquals(describe(OFF_CPU, NETWORK_EXTENDED), describe(taskDAO.queryTasksByServices(
            Collections.singletonList(SERVICE_ID), EBPFProfilingTriggerType.FIXED_TIME, 0,
            NETWORK.getLastUpdateTime())));
        assertEquals(describe(OFF_CPU, NETWORK_EXTENDED), describe(taskDAO.queryTasksByServices(
            Collections.singletonList(SERVICE_ID), EBPFProfilingTriggerType.FIXED_TIME, 0,
            NETWORK_EXTENDED.getLastUpdateTime() - 1)));
        assertEquals(describe(OFF_CPU), describe(taskDAO.queryTasksByServices(
            Collections.singletonList(SERVICE_ID), EBPFProfilingTriggerType.FIXED_TIME, 0,
            NETWORK_EXTENDED.getLastUpdateTime())));
    }

    @Test
    public void readsTheTasksStartingFromATime() throws Exception {
        assertEquals(describe(OFF_CPU, NETWORK_EXTENDED, NETWORK), describe(taskDAO.queryTasksByServices(
            Collections.singletonList(SERVICE_ID), EBPFProfilingTriggerType.FIXED_TIME, NETWORK.getStartTime(), 0)));
        assertEquals(describe(OFF_CPU), describe(taskDAO.queryTasksByServices(
            Collections.singletonList(SERVICE_ID), EBPFProfilingTriggerType.FIXED_TIME, NETWORK.getStartTime() + 1,
            0)));
    }

    /**
     * The task list of a service, as the profiling page asks for it, by target type.
     */
    @Test
    public void readsTheTasksOfTheTargetTypesAsked() throws Exception {
        assertEquals(describe(CONTINUOUS_ON_CPU, ON_CPU), describe(taskDAO.queryTasksByTargets(
            SERVICE_ID, null, Collections.singletonList(EBPFProfilingTargetType.ON_CPU), null, 0, 0)));
        assertEquals(describe(OFF_CPU, CONTINUOUS_OFF_CPU, CONTINUOUS_ON_CPU, ON_CPU),
                     describe(taskDAO.queryTasksByTargets(SERVICE_ID, null, CPU, null, 0, 0)));
        assertEquals(describe(CONTINUOUS_ON_CPU, OTHER_SERVICE_ON_CPU, ON_CPU), describe(taskDAO.queryTasksByTargets(
            null, null, Collections.singletonList(EBPFProfilingTargetType.ON_CPU), null, 0, 0)));
    }

    @Test
    public void readsTheTasksOfAnInstance() throws Exception {
        assertEquals(describe(NETWORK_EXTENDED, CONTINUOUS_ON_CPU, NETWORK),
                     describe(taskDAO.queryTasksByTargets(SERVICE_ID, INSTANCE_ID, ALL_TARGETS, null, 0, 0)));
        assertEquals(describe(CONTINUOUS_OFF_CPU),
                     describe(taskDAO.queryTasksByTargets(null, OTHER_INSTANCE_ID, ALL_TARGETS, null, 0, 0)));
    }

    /**
     * The continuous-profiling page counts the tasks a policy triggered since a time, creating a fixed-time task
     * looks for the tasks of its target starting from its own start, and the task list of a duration asks for the
     * tasks updated after its end.
     */
    @Test
    public void readsTheTasksOfATriggerTypeByTheirStartAndUpdateTimes() throws Exception {
        assertEquals(describe(CONTINUOUS_OFF_CPU, CONTINUOUS_ON_CPU), describe(taskDAO.queryTasksByTargets(
            SERVICE_ID, null, CPU, EBPFProfilingTriggerType.CONTINUOUS_PROFILING, CONTINUOUS_ON_CPU.getStartTime(), 0)));
        assertEquals(describe(CONTINUOUS_OFF_CPU), describe(taskDAO.queryTasksByTargets(
            SERVICE_ID, null, CPU, EBPFProfilingTriggerType.CONTINUOUS_PROFILING,
            CONTINUOUS_ON_CPU.getStartTime() + 1, 0)));
        assertEquals(describe(NETWORK_EXTENDED, NETWORK), describe(taskDAO.queryTasksByTargets(
            SERVICE_ID, null, Collections.singletonList(EBPFProfilingTargetType.NETWORK),
            EBPFProfilingTriggerType.FIXED_TIME, NETWORK.getStartTime(), 0)));
        assertEquals(describe(OFF_CPU), describe(taskDAO.queryTasksByTargets(
            SERVICE_ID, null, CPU, EBPFProfilingTriggerType.FIXED_TIME, 0, NETWORK_EXTENDED.getLastUpdateTime())));
    }

    @Test
    public void keepsTheNewestTasksOfALimit() throws Exception {
        assertEquals(describe(OFF_CPU, CONTINUOUS_OFF_CPU, NETWORK_EXTENDED, CONTINUOUS_ON_CPU, NETWORK),
                     describe(taskDAO.queryTasksByTargets(SERVICE_ID, null, ALL_TARGETS, null, 0, 0)));
        assertEquals(describe(OFF_CPU, CONTINUOUS_OFF_CPU, NETWORK_EXTENDED, CONTINUOUS_ON_CPU, OTHER_SERVICE_ON_CPU),
                     describe(taskDAO.queryTasksByServices(Arrays.asList(SERVICE_ID, OTHER_SERVICE_ID), null, 0, 0)));
    }

    /**
     * Keeping a network task alive reads every row of the task, the one the task was created with and the copies
     * that extend it, whichever daily index each is in.
     */
    @Test
    public void readsEveryRowOfATaskById() throws Exception {
        assertEquals(sorted(describe(NETWORK, NETWORK_EXTENDED)),
                     sorted(describe(taskDAO.getTaskRecord(NETWORK.getLogicalId()))));
        assertEquals(describe(OFF_CPU), describe(taskDAO.getTaskRecord(OFF_CPU.getLogicalId())));
        assertEquals(Collections.emptyList(), taskDAO.getTaskRecord("missing-task"));
    }

    @Test
    public void readsTheSchedulesOfOneTaskLatestStartFirst() throws Exception {
        assertEquals(describeSchedules(LAST_SCHEDULE, ACROSS_MIDNIGHT_SCHEDULE, FIRST_SCHEDULE),
                     describeSchedules(scheduleDAO.querySchedules(NETWORK.getLogicalId())));
        assertEquals(describeSchedules(OTHER_TASK_SCHEDULE),
                     describeSchedules(scheduleDAO.querySchedules(ON_CPU.getLogicalId())));
        assertEquals(Collections.emptyList(), scheduleDAO.querySchedules("missing-task"));
    }

    /**
     * The analysis reads every stack a schedule uploaded in a window, from both daily indices and over several
     * scroll pages, and none uploaded just before the window or on its end.
     */
    @Test
    public void readsEveryStackOfAScheduleInTheWindow() throws Exception {
        assertEquals(describeData(windowOfSchedule),
                     describeData(dataDAO.queryData(Collections.singletonList(SCHEDULE), WINDOW_BEGIN, WINDOW_END)));
    }

    @Test
    public void readsTheStacksOfTheSchedulesAskedOnly() throws Exception {
        final List<EBPFProfilingDataRecord> both = new ArrayList<>(windowOfSchedule);
        both.addAll(windowOfOtherSchedule);
        assertEquals(describeData(both), describeData(
            dataDAO.queryData(Arrays.asList(SCHEDULE, OTHER_SCHEDULE), WINDOW_BEGIN, WINDOW_END)));
    }

    /**
     * The analysis asks for windows that meet at their ends: an upload on a window's end belongs to the next one.
     */
    @Test
    public void readsTheUploadsOnAWindowsBoundsInOneWindowOnly() throws Exception {
        assertEquals(describeData(onWindowEnd), describeData(dataDAO.queryData(
            Collections.singletonList(SCHEDULE), WINDOW_END, WINDOW_END + 10_000L)));
        assertEquals(describeData(beforeWindow), describeData(dataDAO.queryData(
            Collections.singletonList(SCHEDULE), WINDOW_BEGIN - 10_000L, WINDOW_BEGIN)));
    }

    /**
     * Every task lies in the daily index of the day it is created on, as the profiling page and the eBPF agents create
     * them, with the minute of their creation, so the TTL deletes it with its day.
     */
    @Test
    public void storesEveryTaskInTheIndexOfItsDay() throws Exception {
        final List<String> expected = new ArrayList<>();
        for (final EBPFProfilingTaskRecord task : TASKS) {
            expected.add(task.getLogicalId() + "|" + task.getCreateTime() + "|" + dailyIndex(task.getCreateTime()));
        }
        Collections.sort(expected);
        final List<String> stored = new ArrayList<>();
        for (final String logicalId : TASKS.stream().map(EBPFProfilingTaskRecord::getLogicalId).distinct()
                                           .collect(Collectors.toList())) {
            stored.addAll(storedIndices(logicalId));
        }
        Collections.sort(stored);
        assertEquals(expected, stored);
    }

    /**
     * Keeping a network task alive near its end writes a copy that runs ten minutes longer, created at that time: the
     * copy carries the minute it is created in, lies in that day's index, and is read with the task.
     */
    @Test
    public void keepsANetworkTaskAliveWithACopyInTheIndexOfItsDay() throws Exception {
        final String instanceId = IDManager.ServiceInstanceID.buildId(
            IDManager.ServiceID.buildId("keep-alive", true), "keep-alive-1");
        // Created to end in thirty seconds, within the minute before its end in which a keep-alive extends it.
        final EBPFProfilingTaskRecord task = networkTask(
            instanceId, System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(600 - 30));
        support.writeNoneStream(tasks, new EBPFProfilingTaskRecord.Builder(), task);

        final long before = System.currentTimeMillis();
        final EBPFNetworkKeepProfilingResult result =
            new EBPFProfilingMutationService(noneStreamWorkers()).keepEBPFNetworkProfiling(task.getLogicalId());
        final long after = System.currentTimeMillis();

        assertEquals("true|null", result.isStatus() + "|" + result.getErrorReason());
        final List<EBPFProfilingTaskRecord> read = taskDAO.getTaskRecord(task.getLogicalId());
        final EBPFProfilingTaskRecord copy = read.stream()
                                                 .filter(row -> row.getCreateTime() != task.getCreateTime())
                                                 .findFirst()
                                                 .orElseThrow(() -> new AssertionError("no copy of " + read));
        assertTrue(copy.getCreateTime() >= before && copy.getCreateTime() <= after,
                   "created at " + copy.getCreateTime());
        final EBPFProfilingTaskRecord expectedCopy = extended(task, copy.getCreateTime());
        assertEquals(sorted(describe(task, expectedCopy)), sorted(describe(read)));
        assertEquals(sorted(Arrays.asList(
                         task.getLogicalId() + "|" + task.getCreateTime() + "|" + dailyIndex(task.getCreateTime()),
                         task.getLogicalId() + "|" + copy.getCreateTime() + "|" + dailyIndex(copy.getCreateTime()))),
                     sorted(storedIndices(task.getLogicalId())));
    }

    private static long minutes(final int fromMidnight) {
        return MIDNIGHT + TimeUnit.MINUTES.toMillis(fromMidnight);
    }

    /**
     * The task worker of OAP's none-stream processor, writing through the storage's own none-stream DAO, for the
     * services that write tasks.
     */
    private ModuleManager noneStreamWorkers() throws Exception {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleProviderHolder storage = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder storageServices = mock(ModuleServiceHolder.class);
        when(moduleManager.find(StorageModule.NAME)).thenReturn(storage);
        when(storage.provider()).thenReturn(storageServices);
        when(storageServices.getService(IEBPFProfilingTaskDAO.class)).thenReturn(taskDAO);
        when(storageServices.getService(StorageDAO.class)).thenReturn(new StorageEsDAO(support.getClient()));
        when(storageServices.getService(StorageBuilderFactory.class)).thenReturn(new StorageBuilderFactory.Default());
        final ModuleProviderHolder core = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder coreServices = mock(ModuleServiceHolder.class);
        final ModelRegistry models = mock(ModelRegistry.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(core);
        when(core.provider()).thenReturn(coreServices);
        when(coreServices.getService(ModelRegistry.class)).thenReturn(models);
        when(models.add(any(), anyInt(), any(), any())).thenReturn(tasks);
        NoneStreamProcessor.getInstance().create(
            moduleManager, EBPFProfilingTaskRecord.class.getAnnotation(Stream.class), EBPFProfilingTaskRecord.class);
        return moduleManager;
    }

    /**
     * The daily index of each stored row of the task, with the row's creation time.
     */
    private List<String> storedIndices(final String logicalId) {
        final SearchResponse response = support.getClient().search(
            IndexController.LogicIndicesRegister.getPhysicalTableName(EBPFProfilingTaskRecord.INDEX_NAME),
            Search.builder().query(Query.term(EBPFProfilingTaskRecord.LOGICAL_ID, logicalId)).size(100).build());
        return response.getHits().getHits().stream()
                       .map(hit -> logicalId + "|" + hit.getSource().get(EBPFProfilingTaskRecord.CREATE_TIME) + "|"
                           + hit.getIndex())
                       .collect(Collectors.toList());
    }

    private String dailyIndex(final long createTime) {
        return support.getClient().formatIndexName(
            IndexNames.writeIndex(tasks, TimeBucket.getMinuteTimeBucket(createTime)));
    }

    /**
     * Every column of each task, as the task workers stored it, in the order read.
     */
    private static List<String> describe(final List<EBPFProfilingTaskRecord> tasks) {
        final EBPFProfilingTaskRecord.Builder builder = new EBPFProfilingTaskRecord.Builder();
        return tasks.stream().map(task -> {
            final HashMapConverter.ToStorage columns = new HashMapConverter.ToStorage();
            builder.entity2Storage(task, columns);
            return new TreeMap<>(columns.obtain()).toString();
        }).collect(Collectors.toList());
    }

    private static List<String> describe(final EBPFProfilingTaskRecord... tasks) {
        return describe(Arrays.asList(tasks));
    }

    private static List<String> sorted(final List<String> described) {
        return described.stream().sorted().collect(Collectors.toList());
    }

    private static List<String> describeSchedules(final List<EBPFProfilingSchedule> schedules) {
        return schedules.stream()
                        .map(schedule -> String.join("|", schedule.getScheduleId(), schedule.getTaskId(),
                                                     schedule.getProcessId(), String.valueOf(schedule.getStartTime()),
                                                     String.valueOf(schedule.getEndTime())))
                        .collect(Collectors.toList());
    }

    private static List<String> describeSchedules(final EBPFProfilingScheduleRecord... records) {
        return describeSchedules(Arrays.stream(records).map(record -> {
            final EBPFProfilingSchedule schedule = new EBPFProfilingSchedule();
            schedule.setScheduleId(record.getScheduleId());
            schedule.setTaskId(record.getTaskId());
            schedule.setProcessId(record.getProcessId());
            schedule.setStartTime(record.getStartTime());
            schedule.setEndTime(record.getEndTime());
            return schedule;
        }).collect(Collectors.toList()));
    }

    /**
     * Every field of each stack the analysis uses, in no order.
     */
    private static List<String> describeData(final List<EBPFProfilingDataRecord> data) {
        return data.stream()
                   .map(row -> String.join("|", row.getScheduleId(), row.getTaskId(), row.getStackIdList(),
                                           String.valueOf(row.getTargetType()),
                                           Base64.getEncoder().encodeToString(row.getDataBinary()),
                                           String.valueOf(row.getUploadTime())))
                   .sorted()
                   .collect(Collectors.toList());
    }

    /**
     * A fixed-time task as the profiling page creates it ({@code EBPFProfilingMutationService#createTask}).
     */
    private static EBPFProfilingTaskRecord fixedTimeTask(final String serviceId, final List<String> processLabels,
                                                         final EBPFProfilingTargetType targetType,
                                                         final long createTime, final long startTime) {
        final EBPFProfilingTaskRecord task = new EBPFProfilingTaskRecord();
        task.setServiceId(serviceId);
        task.setProcessLabelsJson(processLabels.isEmpty() ? Const.EMPTY_STRING : GSON.toJson(processLabels));
        task.setStartTime(startTime);
        task.setTriggerType(EBPFProfilingTriggerType.FIXED_TIME.value());
        task.setFixedTriggerDuration(60);
        task.setTargetType(targetType.value());
        task.setCreateTime(createTime);
        task.setLastUpdateTime(createTime);
        task.setTimeBucket(TimeBucket.getMinuteTimeBucket(createTime));
        task.generateLogicalId();
        task.setExtensionConfigJson(Const.EMPTY_STRING);
        return task;
    }

    /**
     * A network-profiling task of an instance, created to start at once.
     */
    private static EBPFProfilingTaskRecord networkTask(final String instanceId, final long createTime) {
        final EBPFProfilingTaskRecord task = new EBPFProfilingTaskRecord();
        task.setServiceId(IDManager.ServiceInstanceID.analysisId(instanceId).getServiceId());
        task.setProcessLabelsJson(Const.EMPTY_STRING);
        task.setInstanceId(instanceId);
        task.setStartTime(createTime);
        task.setTriggerType(EBPFProfilingTriggerType.FIXED_TIME.value());
        task.setFixedTriggerDuration(600);
        task.setTargetType(EBPFProfilingTargetType.NETWORK.value());
        task.setCreateTime(createTime);
        task.setLastUpdateTime(createTime);
        task.setTimeBucket(TimeBucket.getMinuteTimeBucket(createTime));
        task.setExtensionConfigJson("{\"networkSamplings\":[{\"uriRegex\":\"/checkout\",\"minDuration\":0}]}");
        task.generateLogicalId();
        return task;
    }

    /**
     * The copy {@code EBPFProfilingMutationService#keepEBPFNetworkProfiling} writes to keep a network task alive:
     * the same logical id, ten more minutes, created and updated at the time it is kept alive.
     */
    private static EBPFProfilingTaskRecord extended(final EBPFProfilingTaskRecord task, final long keptAt) {
        final EBPFProfilingTaskRecord copy = new EBPFProfilingTaskRecord();
        copy.setLogicalId(task.getLogicalId());
        copy.setServiceId(task.getServiceId());
        copy.setProcessLabelsJson(Const.EMPTY_STRING);
        copy.setInstanceId(task.getInstanceId());
        copy.setStartTime(task.getStartTime());
        copy.setTriggerType(task.getTriggerType());
        copy.setFixedTriggerDuration(task.getFixedTriggerDuration() + 600);
        copy.setTargetType(EBPFProfilingTargetType.NETWORK.value());
        copy.setCreateTime(keptAt);
        copy.setLastUpdateTime(keptAt);
        copy.setTimeBucket(TimeBucket.getMinuteTimeBucket(keptAt));
        copy.setExtensionConfigJson(Const.EMPTY_STRING);
        return copy;
    }

    /**
     * A task as the eBPF agent reports one when a continuous-profiling policy triggers on a process of an instance.
     */
    private static EBPFProfilingTaskRecord continuousProfilingTask(final String instanceId,
                                                                   final EBPFProfilingTargetType targetType,
                                                                   final long time) {
        final EBPFProfilingTaskContinuousProfiling continuousProfiling = new EBPFProfilingTaskContinuousProfiling();
        continuousProfiling.setProcessId(IDManager.ProcessID.buildId(instanceId, "checkout"));
        continuousProfiling.setProcessName("checkout");
        final EBPFProfilingTaskRecord task = new EBPFProfilingTaskRecord();
        task.setServiceId(IDManager.ServiceInstanceID.analysisId(instanceId).getServiceId());
        task.setProcessLabelsJson(Const.EMPTY_STRING);
        task.setInstanceId(instanceId);
        task.setStartTime(time);
        task.setTriggerType(EBPFProfilingTriggerType.CONTINUOUS_PROFILING.value());
        task.setFixedTriggerDuration(60);
        task.setTargetType(targetType.value());
        task.setCreateTime(time);
        task.setLastUpdateTime(time);
        task.setTimeBucket(TimeBucket.getMinuteTimeBucket(time));
        task.setContinuousProfilingJson(GSON.toJson(continuousProfiling));
        task.generateLogicalId();
        return task;
    }

    /**
     * A schedule as the eBPF receiver's dispatcher stores it, at the minute of its latest report.
     */
    private static EBPFProfilingScheduleRecord schedule(final String taskId, final String instanceId,
                                                        final String processName, final long startTime,
                                                        final long endTime) {
        final EBPFProfilingScheduleRecord schedule = new EBPFProfilingScheduleRecord();
        schedule.setTaskId(taskId);
        schedule.setProcessId(IDManager.ProcessID.buildId(instanceId, processName));
        schedule.setStartTime(startTime);
        schedule.setEndTime(endTime);
        schedule.setScheduleId(taskId + "-" + processName + "-" + startTime);
        schedule.setTimeBucket(TimeBucket.getMinuteTimeBucket(endTime));
        return schedule;
    }

    /**
     * A stack of an upload, as the eBPF profiling data dispatcher writes it.
     */
    private static EBPFProfilingDataRecord data(final String scheduleId, final String stack, final long uploadTime) {
        final EBPFProfilingDataRecord data = new EBPFProfilingDataRecord();
        data.setScheduleId(scheduleId);
        data.setTaskId(NETWORK.getLogicalId());
        data.setStackIdList("stack-" + stack);
        data.setTargetType(EBPFProfilingTargetType.ON_CPU.value());
        data.setDataBinary(("stack " + stack).getBytes());
        data.setUploadTime(uploadTime);
        data.setTimeBucket(TimeBucket.getRecordTimeBucket(uploadTime));
        return data;
    }
}
