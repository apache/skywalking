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

package org.apache.skywalking.oap.server.core.profiling.pprof.storage;

import com.google.gson.Gson;
import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.PprofProfilingData;

public class PprofProfilingDataDispatcher implements SourceDispatcher<PprofProfilingData> {
    private static final Gson GSON = new Gson();

    @Override
    public void dispatch(PprofProfilingData source) {
        PprofProfilingDataRecord record = new PprofProfilingDataRecord();
        record.setTaskId(source.getTaskId());
        record.setInstanceId(source.getInstanceId());
        record.setDataBinary(GSON.toJson(source.getFrameTree()).getBytes());
        record.setUploadTime(source.getUploadTime());
        record.setTimeBucket(TimeBucket.getRecordTimeBucket(source.getUploadTime()));
        RecordStreamProcessor.getInstance().in(record);
    }
}
