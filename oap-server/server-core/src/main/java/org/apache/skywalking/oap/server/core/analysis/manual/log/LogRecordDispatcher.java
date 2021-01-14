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
 */

package org.apache.skywalking.oap.server.core.analysis.manual.log;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.Log;

public class LogRecordDispatcher implements SourceDispatcher<Log> {

    @Override
    public void dispatch(final Log source) {
        LogRecord record = new LogRecord();
        record.setUniqueId(source.getUniqueId());
        record.setTimestamp(source.getTimestamp());
        record.setTimeBucket(source.getTimeBucket());
        record.setServiceId(source.getServiceId());
        record.setServiceInstanceId(source.getServiceInstanceId());
        record.setEndpointId(source.getEndpointId());
        record.setEndpointName(source.getEndpointName());
        record.setTraceId(source.getTraceId());
        record.setTraceSegmentId(source.getTraceSegmentId());
        record.setSpanId(source.getSpanId());
        record.setContentType(source.getContentType().value());
        record.setContent(source.getContent());
        record.setTagsRawData(source.getTagsRawData());
        record.setTagsInString(Tag.Util.toStringList(source.getTags()));
        record.setTags(source.getTags());

        RecordStreamProcessor.getInstance().in(record);
    }
}
