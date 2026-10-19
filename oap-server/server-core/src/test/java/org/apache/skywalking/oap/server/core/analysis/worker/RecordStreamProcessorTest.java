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

package org.apache.skywalking.oap.server.core.analysis.worker;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RecordStreamProcessorTest {
    private static final int TTL_DAYS = 3;

    private RecordStreamProcessor processor;
    private RecordPersistentWorker worker;
    private Record record;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        processor = new RecordStreamProcessor();
        processor.setRecordDataTTL(TTL_DAYS);
        worker = mock(RecordPersistentWorker.class);
        record = mock(Record.class);

        final Field workersField = RecordStreamProcessor.class.getDeclaredField("workers");
        workersField.setAccessible(true);
        ((Map<Class<? extends Record>, RecordPersistentWorker>) workersField.get(processor))
            .put(record.getClass(), worker);
    }

    @Test
    void acceptsRecordWithinTTL() {
        givenRecordAged(TimeUnit.DAYS.toMillis(TTL_DAYS) - TimeUnit.MINUTES.toMillis(2));

        processor.in(record);

        verify(worker).in(record);
    }

    @Test
    void dropsRecordBeyondTTL() {
        givenRecordAged(TimeUnit.DAYS.toMillis(TTL_DAYS) + TimeUnit.MINUTES.toMillis(2));

        processor.in(record);

        verifyNoInteractions(worker);
    }

    private void givenRecordAged(final long ageMillis) {
        when(record.getTimeBucket()).thenReturn(
            TimeBucket.getRecordTimeBucket(System.currentTimeMillis() - ageMillis));
    }
}
