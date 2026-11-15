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
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingDataRecord;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBEBPFProfilingDataDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Reads eBPF profiling data the way the profiling analysis does ({@code queryData}), on the real
 * {@code ebpf_profiling_data} stream: every stack a schedule uploaded in the window asked for, however many there
 * are, and none from outside it. The analysis asks for 10-second windows that meet at their ends, each
 * {@code [begin, end)}; each upload sends one row per stack, all with the upload's time.
 */
@Testcontainers
public class BanyanDBEBPFProfilingDataQueryIT {
    private static final String SCHEDULE = "schedule-read";
    private static final String OTHER_SCHEDULE = "schedule-other";
    private static final long WINDOW_MILLIS = 10_000L;
    /** Uploads of the read schedule in the window, half a second apart, each of this many stacks. */
    private static final int UPLOADS = 15;
    private static final int STACKS_PER_UPLOAD = 10;
    private static final int OTHER_SCHEDULE_STACKS = 20;

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBEBPFProfilingDataDAO QUERY_DAO;
    private static long WINDOW_START;
    private static long WINDOW_END;
    private static List<String> READ_STACKS;
    /** Uploaded at the window's end, which is the first millisecond of the next window. */
    private static List<String> BOUNDARY_STACKS;

    @BeforeAll
    public static void writeData() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        final Model model = SUPPORT.install(
            EBPFProfilingDataRecord.class, DefaultScopeDefine.EBPF_PROFILING_DATA, EBPFProfilingDataRecord.INDEX_NAME);
        WINDOW_START = System.currentTimeMillis() - 3 * WINDOW_MILLIS;
        WINDOW_END = WINDOW_START + WINDOW_MILLIS;
        final List<EBPFProfilingDataRecord> data = new ArrayList<>();
        final List<String> readStacks = new ArrayList<>();
        for (int upload = 0; upload < UPLOADS; upload++) {
            for (int stack = 0; stack < STACKS_PER_UPLOAD; stack++) {
                final EBPFProfilingDataRecord row = row(SCHEDULE, WINDOW_START + upload * 500L, upload + "-" + stack);
                data.add(row);
                readStacks.add(row.getStackIdList());
            }
        }
        final List<String> boundaryStacks = new ArrayList<>();
        for (int stack = 0; stack < STACKS_PER_UPLOAD; stack++) {
            data.add(row(SCHEDULE, WINDOW_START - 1_000L, "before-" + stack));
            final EBPFProfilingDataRecord boundary = row(SCHEDULE, WINDOW_END, "boundary-" + stack);
            data.add(boundary);
            boundaryStacks.add(boundary.getStackIdList());
        }
        for (int stack = 0; stack < OTHER_SCHEDULE_STACKS; stack++) {
            data.add(row(OTHER_SCHEDULE, WINDOW_START + 1_000L, "other-" + stack));
        }
        SUPPORT.write(model, new EBPFProfilingDataRecord.Builder(), data);
        Collections.sort(readStacks);
        READ_STACKS = readStacks;
        Collections.sort(boundaryStacks);
        BOUNDARY_STACKS = boundaryStacks;

        QUERY_DAO = new BanyanDBEBPFProfilingDataDAO(SUPPORT.getClient());
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(
            () -> assertEquals(OTHER_SCHEDULE_STACKS,
                               read(Collections.singletonList(OTHER_SCHEDULE), WINDOW_START, WINDOW_END).size()));
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @Test
    public void readsEveryStackOfTheWindow() throws IOException {
        assertEquals(READ_STACKS, read(Collections.singletonList(SCHEDULE), WINDOW_START, WINDOW_END));
    }

    @Test
    public void readsEverySchedule() throws IOException {
        assertEquals(UPLOADS * STACKS_PER_UPLOAD + OTHER_SCHEDULE_STACKS,
                     read(Arrays.asList(SCHEDULE, OTHER_SCHEDULE), WINDOW_START, WINDOW_END).size());
    }

    @Test
    public void readsAnUploadOnTheBoundaryInTheLaterWindowOnly() throws IOException {
        assertEquals(BOUNDARY_STACKS,
                     read(Collections.singletonList(SCHEDULE), WINDOW_END, WINDOW_END + WINDOW_MILLIS));
    }

    /** The sorted stack lists read. */
    private static List<String> read(List<String> schedules, long begin, long end) throws IOException {
        return QUERY_DAO.queryData(schedules, begin, end).stream()
                        .map(EBPFProfilingDataRecord::getStackIdList).sorted().collect(Collectors.toList());
    }

    private static EBPFProfilingDataRecord row(String schedule, long uploadTime, String stack) {
        final EBPFProfilingDataRecord row = new EBPFProfilingDataRecord();
        row.setTaskId("task");
        row.setScheduleId(schedule);
        row.setStackIdList("stack-" + stack);
        row.setTargetType(0);
        row.setDataBinary(new byte[] {1});
        row.setUploadTime(uploadTime);
        row.setTimeBucket(TimeBucket.getRecordTimeBucket(uploadTime));
        return row;
    }
}
