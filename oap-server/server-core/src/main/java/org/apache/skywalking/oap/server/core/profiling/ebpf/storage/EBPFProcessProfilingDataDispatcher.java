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

package org.apache.skywalking.oap.server.core.profiling.ebpf.storage;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.EBPFProfilingData;

public class EBPFProcessProfilingDataDispatcher implements SourceDispatcher<EBPFProfilingData> {
    @Override
    public void dispatch(EBPFProfilingData source) {
        final EBPFProfilingDataRecord record = new EBPFProfilingDataRecord();
        record.setScheduleId(source.getScheduleId());
        record.setTaskId(source.getTaskId());
        record.setStackIdList(source.getStackIdList());
        record.setTargetType(source.getTargetType().value());
        record.setDataBinary(source.getDataBinary());
        record.setUploadTime(source.getUploadTime());
        record.setTimeBucket(TimeBucket.getRecordTimeBucket(source.getUploadTime()));
        RecordStreamProcessor.getInstance().in(record);
    }
}