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

import org.apache.skywalking.apm.collector.core.data.Column;
import org.apache.skywalking.apm.collector.core.data.RemoteData;
import org.apache.skywalking.apm.collector.core.data.StreamData;
import org.apache.skywalking.apm.collector.core.data.operator.AddMergeOperation;
import org.apache.skywalking.apm.collector.core.data.operator.CoverMergeOperation;
import org.apache.skywalking.apm.collector.core.data.operator.NonMergeOperation;
import org.apache.skywalking.apm.collector.remote.service.RemoteDataRegisterService;

/**
 * @author peng-yongsheng
 */
public class ResponseTimeDistribution extends StreamData {

    private static final Column[] STRING_COLUMNS = {
        new Column(ResponseTimeDistributionTable.ID, new NonMergeOperation()),
        new Column(ResponseTimeDistributionTable.METRIC_ID, new NonMergeOperation()),
    };

    private static final Column[] LONG_COLUMNS = {
        new Column(ResponseTimeDistributionTable.TIME_BUCKET, new CoverMergeOperation()),
        new Column(ResponseTimeDistributionTable.CALLS, new AddMergeOperation()),
        new Column(ResponseTimeDistributionTable.ERROR_CALLS, new AddMergeOperation()),
        new Column(ResponseTimeDistributionTable.SUCCESS_CALLS, new AddMergeOperation()),
    };

    private static final Column[] DOUBLE_COLUMNS = {};

    private static final Column[] INTEGER_COLUMNS = {
        new Column(ResponseTimeDistributionTable.STEP, new NonMergeOperation()),
    };

    private static final Column[] BYTE_COLUMNS = {};

    public ResponseTimeDistribution() {
        super(STRING_COLUMNS, LONG_COLUMNS, DOUBLE_COLUMNS, INTEGER_COLUMNS, BYTE_COLUMNS);
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
