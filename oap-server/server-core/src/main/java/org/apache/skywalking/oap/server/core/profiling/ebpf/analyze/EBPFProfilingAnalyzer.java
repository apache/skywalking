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

package org.apache.skywalking.oap.server.core.profiling.ebpf.analyze;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingDataRecord;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzation;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzeAggregateType;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzeTimeRange;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTree;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingDataDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * eBPF Profiling Analyzer working on data query and combine them for generate the Flame Graph.
 */
@Slf4j
public class EBPFProfilingAnalyzer {

    private static final EBPFProfilingAnalyzeCollector ANALYZE_COLLECTOR = new EBPFProfilingAnalyzeCollector();
    private static final Long FETCH_DATA_DURATION = TimeUnit.SECONDS.toMillis(10);

    private final ModuleManager moduleManager;
    protected IEBPFProfilingDataDAO dataDAO;
    private long maxQueryTimeoutInSecond;
    private final ExecutorService fetchDataThreadPool;

    public EBPFProfilingAnalyzer(ModuleManager moduleManager, int maxDurationOfQuery, int fetchDataThreadPoolSize) {
        this.moduleManager = moduleManager;
        this.maxQueryTimeoutInSecond = maxDurationOfQuery;
        this.fetchDataThreadPool = Executors.newFixedThreadPool(fetchDataThreadPoolSize);
    }

    /**
     * search data and analyze
     */
    public EBPFProfilingAnalyzation analyze(List<String> scheduleIdList,
                                            List<EBPFProfilingAnalyzeTimeRange> ranges,
                                            EBPFProfilingAnalyzeAggregateType aggregateType) throws IOException {
        EBPFProfilingAnalyzation analyzation = new EBPFProfilingAnalyzation();

        // query data
        long queryDataMaxTimestamp = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(maxQueryTimeoutInSecond);
        final Stream<EBPFProfilingStack> stackStream = buildTimeRanges(ranges).parallelStream().map(r -> {
            try {
                return fetchDataThreadPool.submit(() -> getDataDAO().queryData(scheduleIdList, r.getMinTime(), r.getMaxTime()))
                        .get(queryDataMaxTimestamp - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
                return Collections.<EBPFProfilingDataRecord>emptyList();
            }
        }).flatMap(Collection::stream).map(e -> {
            try {
                return EBPFProfilingStack.deserialize(e, aggregateType);
            } catch (Exception ex) {
                log.warn("could not deserialize the stack", ex);
                return null;
            }
        }).filter(Objects::nonNull).distinct();

        // analyze tree
        generateTrees(analyzation, stackStream);

        return analyzation;
    }

    public void generateTrees(EBPFProfilingAnalyzation analyzation, Stream<EBPFProfilingStack> stackStream) {
        Collection<EBPFProfilingTree> stackTrees = stackStream
                // stack list cannot be empty
                .filter(s -> CollectionUtils.isNotEmpty(s.getSymbols()))
                // analyze the symbol and combine as trees
                .collect(Collectors.groupingBy(s -> s.getSymbols()
                        .get(0), ANALYZE_COLLECTOR)).values();

        analyzation.getTrees().addAll(stackTrees);
    }

    protected List<TimeRange> buildTimeRanges(List<EBPFProfilingAnalyzeTimeRange> timeRanges) {
        return timeRanges.parallelStream()
                .map(r -> buildTimeRanges(r.getStart(), r.getEnd()))
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    /**
     * Split time ranges to insure the start time and end time is small then {@link #FETCH_DATA_DURATION}
     */
    protected List<TimeRange> buildTimeRanges(long start, long end) {
        if (start >= end) {
            return null;
        }

        // include latest millisecond
        end += 1;

        final List<TimeRange> timeRanges = new ArrayList<>();
        do {
            long batchEnd = Math.min(start + FETCH_DATA_DURATION, end);
            timeRanges.add(new TimeRange(start, batchEnd));
            start = batchEnd;
        }
        while (start < end);

        return timeRanges;
    }

    protected IEBPFProfilingDataDAO getDataDAO() {
        if (dataDAO == null) {
            dataDAO = moduleManager.find(StorageModule.NAME)
                    .provider()
                    .getService(IEBPFProfilingDataDAO.class);
        }
        return dataDAO;
    }

    /**
     * Split the query time with {@link #FETCH_DATA_DURATION}
     */
    @Getter
    @RequiredArgsConstructor
    private static class TimeRange {
        private final long minTime;
        private final long maxTime;
    }
}