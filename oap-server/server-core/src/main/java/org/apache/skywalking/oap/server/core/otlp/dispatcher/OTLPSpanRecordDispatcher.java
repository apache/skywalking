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

package org.apache.skywalking.oap.server.core.otlp.dispatcher;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.otlp.OTLPSpanRecord;
import org.apache.skywalking.oap.server.core.otlp.source.OTLPSpan;

public class OTLPSpanRecordDispatcher implements SourceDispatcher<OTLPSpan> {
    @Override
    public void dispatch(final OTLPSpan source) {
        final OTLPSpanRecord record = new OTLPSpanRecord();
        record.setTraceId(source.getTraceId());
        record.setSpanId(source.getSpanId());
        record.setParentSpanId(source.getParentSpanId());
        record.setServiceName(source.getServiceName());
        record.setServiceInstance(source.getServiceInstance());
        record.setScopeName(source.getScopeName());
        record.setName(source.getName());
        record.setKind(source.getKind());
        record.setStatusCode(source.getStatusCode());
        record.setPeerService(source.getPeerService());
        record.setStartTime(source.getStartTime());
        record.setDuration(source.getDuration());
        record.setTags(source.getTags());
        record.setDataBinary(source.getDataBinary());
        record.setTimeBucket(source.getTimeBucket());
        RecordStreamProcessor.getInstance().in(record);
    }
}
