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

package org.apache.skywalking.oap.server.receiver.pprof.provider.handler;

import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.type.PprofTask;
import org.apache.skywalking.oap.server.core.query.type.PprofTaskLogOperationType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

public class PprofServiceHandlerTest {

    @Test
    public void recordPprofTaskLogUsesMinutesForDuration() {
        final long createTime = 1_700_000_000_000L;
        final int durationMinutes = 1;
        final long expectedEnd = createTime + TimeUnit.MINUTES.toMillis(durationMinutes);

        final PprofTask task = new PprofTask();
        task.setId("task-1");
        task.setCreateTime(createTime);
        task.setDuration(durationMinutes);

        final RecordStreamProcessor processor = mock(RecordStreamProcessor.class);
        try (MockedStatic<RecordStreamProcessor> mocked = mockStatic(RecordStreamProcessor.class)) {
            mocked.when(RecordStreamProcessor::getInstance).thenReturn(processor);

            PprofServiceHandler.recordPprofTaskLog(task, "instance-1", PprofTaskLogOperationType.NOTIFIED);

            final ArgumentCaptor<PprofTaskLogRecord> captor = ArgumentCaptor.forClass(PprofTaskLogRecord.class);
            verify(processor).in(captor.capture());
            final PprofTaskLogRecord record = captor.getValue();

            assertEquals(expectedEnd, record.getTimestamp());
            assertEquals(TimeBucket.getRecordTimeBucket(expectedEnd), record.getTimeBucket());
        }
    }
}
