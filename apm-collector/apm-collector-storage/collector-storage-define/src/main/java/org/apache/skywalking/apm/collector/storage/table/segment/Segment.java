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

package org.apache.skywalking.apm.collector.storage.table.segment;

import org.apache.skywalking.apm.collector.core.data.StreamData;
import org.apache.skywalking.apm.collector.core.data.column.*;
import org.apache.skywalking.apm.collector.core.data.operator.*;

/**
 * @author peng-yongsheng
 */
public class Segment extends StreamData {

    private static final StringColumn[] STRING_COLUMNS = {
        new StringColumn(SegmentTable.ID, new NonMergeOperation()),
    };

    private static final LongColumn[] LONG_COLUMNS = {
        new LongColumn(SegmentTable.TIME_BUCKET, new NonMergeOperation()),
    };

    private static final ByteColumn[] BYTE_COLUMNS = {
        new ByteColumn(SegmentTable.DATA_BINARY, new CoverMergeOperation()),
    };

    private static final IntegerColumn[] INTEGER_COLUMNS = {
    };

    private static final DoubleColumn[] DOUBLE_COLUMNS = {
    };

    public Segment() {
        super(STRING_COLUMNS, LONG_COLUMNS, INTEGER_COLUMNS, DOUBLE_COLUMNS, BYTE_COLUMNS);
    }

    @Override public String getId() {
        return getDataString(0);
    }

    @Override public void setId(String id) {
        setDataString(0, id);
    }

    @Override public String getMetricId() {
        return getId();
    }

    @Override public void setMetricId(String metricId) {
        setId(metricId);
    }

    public byte[] getDataBinary() {
        return getDataBytes(0);
    }

    public void setDataBinary(byte[] dataBinary) {
        setDataBytes(0, dataBinary);
    }

    public long getTimeBucket() {
        return getDataLong(0);
    }

    public void setTimeBucket(long timeBucket) {
        setDataLong(0, timeBucket);
    }
}
