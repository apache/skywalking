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

package org.apache.skywalking.apm.collector.storage.table.global;

import org.apache.skywalking.apm.collector.core.data.*;
import org.apache.skywalking.apm.collector.core.data.column.*;
import org.apache.skywalking.apm.collector.core.data.operator.*;
import org.apache.skywalking.apm.collector.remote.service.RemoteDataRegisterService;

/**
 * @author peng-yongsheng
 */
public class ResponseTimeDistribution extends StreamData {

    private static final StringColumn[] STRING_COLUMNS = {
        new StringColumn(ResponseTimeDistributionTable.ID, new NonMergeOperation()),
        new StringColumn(ResponseTimeDistributionTable.METRIC_ID, new NonMergeOperation()),
    };

    private static final LongColumn[] LONG_COLUMNS = {
        new LongColumn(ResponseTimeDistributionTable.TIME_BUCKET, new CoverMergeOperation()),
        new LongColumn(ResponseTimeDistributionTable.CALLS, new AddMergeOperation()),
        new LongColumn(ResponseTimeDistributionTable.ERROR_CALLS, new AddMergeOperation()),
        new LongColumn(ResponseTimeDistributionTable.SUCCESS_CALLS, new AddMergeOperation()),
    };

    private static final IntegerColumn[] INTEGER_COLUMNS = {
        new IntegerColumn(ResponseTimeDistributionTable.STEP, new NonMergeOperation()),
    };

    private static final DoubleColumn[] DOUBLE_COLUMNS = {
    };

    public ResponseTimeDistribution() {
        super(STRING_COLUMNS, LONG_COLUMNS, INTEGER_COLUMNS, DOUBLE_COLUMNS);
    }

    @Override public String getId() {
        return getDataString(0);
    }

    @Override public void setId(String id) {
        setDataString(0, id);
    }

    @Override public String getMetricId() {
        return getDataString(1);
    }

    @Override public void setMetricId(String metricId) {
        setDataString(1, metricId);
    }

    public int getStep() {
        return getDataInteger(0);
    }

    public void setStep(int step) {
        setDataInteger(0, step);
    }

    public long getTimeBucket() {
        return getDataLong(0);
    }

    public void setTimeBucket(long timeBucket) {
        setDataLong(0, timeBucket);
    }

    public long getCalls() {
        return getDataLong(1);
    }

    public void setCalls(long calls) {
        setDataLong(1, calls);
    }

    public long getErrorCalls() {
        return getDataLong(2);
    }

    public void setErrorCalls(long errorCalls) {
        setDataLong(2, errorCalls);
    }

    public long getSuccessCalls() {
        return getDataLong(3);
    }

    public void setSuccessCalls(long successCalls) {
        setDataLong(3, successCalls);
    }

    public static class InstanceCreator implements RemoteDataRegisterService.RemoteDataInstanceCreator {
        @Override public RemoteData createInstance() {
            return new ResponseTimeDistribution();
        }
    }
}
