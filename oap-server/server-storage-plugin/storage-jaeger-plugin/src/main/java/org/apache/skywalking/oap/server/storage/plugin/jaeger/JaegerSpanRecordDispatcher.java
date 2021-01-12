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

package org.apache.skywalking.oap.server.storage.plugin.jaeger;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;

/**
 * Dispatch for Zipkin native mode spans.
 */
public class JaegerSpanRecordDispatcher implements SourceDispatcher<JaegerSpan> {

    @Override
    public void dispatch(JaegerSpan source) {
        JaegerSpanRecord segment = new JaegerSpanRecord();
        segment.setTraceId(source.getTraceId());
        segment.setSpanId(source.getSpanId());
        segment.setServiceId(source.getServiceId());
        segment.setServiceInstanceId(source.getServiceInstanceId());
        segment.setEndpointName(source.getEndpointName());
        segment.setEndpointId(source.getEndpointId());
        segment.setStartTime(source.getStartTime());
        segment.setEndTime(source.getEndTime());
        segment.setLatency(source.getLatency());
        segment.setIsError(source.getIsError());
        segment.setDataBinary(source.getDataBinary());
        segment.setTimeBucket(source.getTimeBucket());
        segment.setEncode(source.getEncode());

        RecordStreamProcessor.getInstance().in(segment);
    }
}
