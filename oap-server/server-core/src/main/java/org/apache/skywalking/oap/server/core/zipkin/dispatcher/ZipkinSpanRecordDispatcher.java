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

package org.apache.skywalking.oap.server.core.zipkin.dispatcher;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.apache.skywalking.oap.server.core.zipkin.source.ZipkinSpan;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;

public class ZipkinSpanRecordDispatcher implements SourceDispatcher<ZipkinSpan> {

    @Override
    public void dispatch(ZipkinSpan source) {
        ZipkinSpanRecord record = new ZipkinSpanRecord();
        record.setTraceId(source.getTraceId());
        record.setSpanId(source.getSpanId());
        record.setParentId(source.getParentId());
        record.setKind(source.getKind());
        record.setDuration(source.getDuration());
        record.setName(source.getName());
        record.setLocalEndpointServiceName(source.getLocalEndpointServiceName());
        record.setLocalEndpointIPV4(source.getLocalEndpointIPV4());
        record.setLocalEndpointIPV6(source.getLocalEndpointIPV6());
        record.setLocalEndpointPort(source.getLocalEndpointPort());
        record.setRemoteEndpointServiceName(source.getRemoteEndpointServiceName());
        record.setRemoteEndpointIPV4(source.getRemoteEndpointIPV4());
        record.setRemoteEndpointIPV6(source.getRemoteEndpointIPV6());
        record.setRemoteEndpointPort(source.getRemoteEndpointPort());
        record.setTimestamp(source.getTimestamp());
        record.setTimestampMillis(source.getTimestampMillis());
        record.setQuery(source.getQuery());
        record.setTags(source.getTags());
        record.setAnnotations(source.getAnnotations());
        record.setTimeBucket(source.getTimeBucket());
        record.setDebug(BooleanUtils.booleanToValue(Boolean.TRUE.equals(source.getDebug())));
        record.setShared(BooleanUtils.booleanToValue(Boolean.TRUE.equals(source.getShared())));
        RecordStreamProcessor.getInstance().in(record);
    }
}
