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

package org.apache.skywalking.oap.server.core.analysis.manual.log;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.HTTPAccessLog;

public class HTTPAccessLogDispatcher implements SourceDispatcher<HTTPAccessLog> {

    @Override
    public void dispatch(HTTPAccessLog source) {
        HTTPAccessLogRecord record = new HTTPAccessLogRecord();
        record.setTimestamp(source.getTimestamp());
        record.setTimeBucket(source.getTimeBucket());
        record.setServiceId(source.getServiceId());
        record.setServiceInstanceId(source.getServiceInstanceId());
        record.setEndpointId(source.getEndpointId());
        record.setEndpointName(source.getEndpointName());
        record.setTraceId(source.getTraceId());
        record.setIsError(source.getIsError());
        record.setStatusCode(source.getStatusCode());
        record.setContentType(source.getContentType().value());
        record.setContent(source.getContent());

        RecordStreamProcessor.getInstance().in(record);
    }
}
