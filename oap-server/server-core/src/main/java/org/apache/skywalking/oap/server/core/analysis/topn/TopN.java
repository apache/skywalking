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

package org.apache.skywalking.oap.server.core.analysis.topn;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.ComparableStorageData;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * TopN data.
 */
public abstract class TopN extends Record implements ComparableStorageData {
    public static final String STATEMENT = "statement";
    public static final String LATENCY = "latency";
    public static final String TRACE_ID = "trace_id";
    public static final String SERVICE_ID = "service_id";
    
    @Getter
    @Setter
    @Column(columnName = LATENCY, dataType = Column.ValueDataType.SAMPLED_RECORD)
    private long latency;
    @Getter
    @Setter
    @Column(columnName = TRACE_ID)
    private String traceId;
    @Getter
    @Setter
    @Column(columnName = SERVICE_ID)
    private String serviceId;

    @Override
    public int compareTo(Object o) {
        TopN target = (TopN) o;
        return (int) (latency - target.latency);
    }
}
