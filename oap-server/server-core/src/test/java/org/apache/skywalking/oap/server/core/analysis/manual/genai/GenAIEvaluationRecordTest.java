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

package org.apache.skywalking.oap.server.core.analysis.manual.genai;

import org.apache.skywalking.oap.server.core.query.enumeration.GenAITraceRefType;
import org.apache.skywalking.oap.server.core.query.type.GenAITraceRef;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenAIEvaluationRecordTest {

    @Test
    void keepsAZipkinTraceRefThroughStorage() {
        final GenAIEvaluationRecord record = GenAIEvaluationRecord.create("openai", "gpt-4.1-mini");
        record.setUniqueId("unique");
        record.setTraceId("trace");
        record.setRefType(GenAITraceRefType.ZIPKIN.name());
        record.setSpanId("0123456789abcdef");
        record.setEvaluationTime(1_790_000_000_000L);
        record.setTimeBucket(20261019000000L);

        final GenAIEvaluationRecord.Builder builder = new GenAIEvaluationRecord.Builder();
        final HashMapConverter.ToStorage toStorage = new HashMapConverter.ToStorage();
        builder.entity2Storage(record, toStorage);
        final GenAITraceRef ref = builder.storage2Entity(new HashMapConverter.ToEntity(toStorage.obtain()))
                                         .getTraceRef();

        assertEquals(GenAITraceRefType.ZIPKIN, ref.getType());
        assertEquals("0123456789abcdef", ref.getSpanId());
    }
}
