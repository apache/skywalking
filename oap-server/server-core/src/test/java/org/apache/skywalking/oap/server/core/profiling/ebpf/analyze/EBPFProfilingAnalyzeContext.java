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

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFOnCPUProfiling;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingStackMetadata;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingStackType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingDataRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzation;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzeAggregateType;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzeTimeRange;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingStackElement;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTree;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingDataDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * eBPF Profiling Analyzer test context.
 */
@Data
public class EBPFProfilingAnalyzeContext {

    private String times;
    private List<String> symbols;
    private List<Except> excepted;

    /**
     * Initial the eBPF Profiling Analyzation and verify the analysis result.
     */
    public void analyzeAssert() throws IOException {
        final Analyzer analyzer = new Analyzer();
        final EBPFProfilingAnalyzation analyze = analyzer.analyze(null, buildTimeRanges(), EBPFProfilingAnalyzeAggregateType.COUNT);
        Assertions.assertNotNull(analyze);
        Assertions.assertNull(analyze.getTip());
        Assertions.assertNotNull(analyze.getTrees());
        for (int i = 0; i < excepted.size(); i++) {
            Assertions.assertTrue(analyze.getTrees().size() > i);
            final Except except = excepted.get(i);
            final EBPFProfilingTree actualTree = analyze.getTrees().get(i);
            validateTree(except, actualTree, 0);
        }
    }

    private void validateTree(Except except, EBPFProfilingTree actual, int parentId) {
        String symbol = except.getData().split(":")[0];
        long count = Long.parseLong(except.getData().split(":")[1]);
        boolean found = false;
        int dataId = 0;
        for (EBPFProfilingStackElement element : actual.getElements()) {
            Assertions.assertNotNull(element);
            if (element.getParentId() == parentId
                    && Objects.equals(element.getSymbol(), symbol)
                    && Objects.equals(element.getDumpCount(), count)) {
                found = true;
                dataId = element.getId();
            }
        }
        Assertions.assertTrue(found, "could not found:" + except.getData());

        if (CollectionUtils.isNotEmpty(except.getChild())) {
            for (Except e : except.getChild()) {
                validateTree(e, actual, dataId);
            }
        }
    }

    private List<EBPFProfilingAnalyzeTimeRange> buildTimeRanges() {
        return Arrays.stream(this.times.split(","))
                .map(m -> {
                    final String[] startEnd = m.split("-");
                    final EBPFProfilingAnalyzeTimeRange range = new EBPFProfilingAnalyzeTimeRange();
                    range.setStart(Long.parseLong(startEnd[0]));
                    range.setEnd(Long.parseLong(startEnd[1]));
                    return range;
                }).collect(Collectors.toList());
    }

    @Data
    public static class Except {
        private String data;
        private List<Except> child;
    }

    private class Analyzer extends EBPFProfilingAnalyzer implements IEBPFProfilingDataDAO {
        public Analyzer() {
            super(null, 100, 5);
        }

        @Override
        protected IEBPFProfilingDataDAO getDataDAO() {
            return this;
        }

        @Override
        public List<EBPFProfilingDataRecord> queryData(List<String> taskIdList, long beginTime, long endTime) throws IOException {
            final ArrayList<EBPFProfilingDataRecord> records = new ArrayList<>();
            for (; beginTime < endTime; beginTime++) {
                if (symbols.size() <= (int) beginTime) {
                    break;
                }
                final String symbolData = symbols.get((int) beginTime);
                final EBPFProfilingDataRecord record = new EBPFProfilingDataRecord();
                record.setTargetType(EBPFProfilingTargetType.ON_CPU.value());
                final int count = Integer.parseInt(StringUtils.substringBefore(symbolData, ":"));
                final List<String> symbols = Arrays.asList(StringUtils.substringAfter(symbolData, ":").split("-"));
                // revert symbol to the real case
                Collections.reverse(symbols);
                final EBPFProfilingStackMetadata metadata = EBPFProfilingStackMetadata.newBuilder()
                        .setStackType(EBPFProfilingStackType.PROCESS_USER_SPACE)
                        .setStackId(1)
                        .addAllStackSymbols(symbols)
                        .build();
                record.setDataBinary(EBPFOnCPUProfiling.newBuilder().setDumpCount(count).addStacks(metadata).build().toByteArray());
                records.add(record);
            }
            return records;
        }
    }
}
