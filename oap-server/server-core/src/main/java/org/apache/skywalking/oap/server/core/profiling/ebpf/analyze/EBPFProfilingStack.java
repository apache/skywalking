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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingStackMetadata;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingDataRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingStackType;

import java.io.ByteArrayInputStream;
import java.util.LinkedList;
import java.util.List;

/**
 * Transform the {@link EBPFProfilingDataRecord} as runtime data
 */
@Data
public class EBPFProfilingStack {

    private long uploadTime;
    private long dumpCount;
    private List<Symbol> symbols;

    public static EBPFProfilingStack deserialize(EBPFProfilingDataRecord record) throws Exception {
        final LinkedList<Symbol> symbols = new LinkedList<>();
        final ByteArrayInputStream stackInput = new ByteArrayInputStream(record.getStacksBinary());

        for (EBPFProfilingStackMetadata stack = EBPFProfilingStackMetadata.parseDelimitedFrom(stackInput);
             stack != null; stack = EBPFProfilingStackMetadata.parseDelimitedFrom(stackInput)) {
            EBPFProfilingStackMetadata finalStack = stack;
            stack.getStackSymbolsList()
                    .forEach(s -> symbols.addFirst(new Symbol(
                            s,
                            EBPFProfilingStackType.valueOf(finalStack.getStackType())
                    )));
        }

        final EBPFProfilingStack stack = new EBPFProfilingStack();
        stack.setUploadTime(record.getUploadTime());
        stack.setDumpCount(record.getStackDumpCount());
        stack.setSymbols(symbols);
        return stack;
    }

    @Data
    @AllArgsConstructor
    @EqualsAndHashCode
    public static final class Symbol {
        private String name;
        private EBPFProfilingStackType stackType;
    }
}