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

import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;

import static org.apache.skywalking.oap.server.core.analysis.manual.log.HTTPAccessLogRecord.INDEX_NAME;

@Stream(name = INDEX_NAME, scopeId = DefaultScopeDefine.HTTP_ACCESS_LOG, builder = HTTPAccessLogRecord.Builder.class, processor = RecordStreamProcessor.class)
public class HTTPAccessLogRecord extends AbstractLogRecord {

    public static final String INDEX_NAME = "http_access_log";

    public static class Builder extends AbstractLogRecord.Builder<HTTPAccessLogRecord> {
        @Override
        public HTTPAccessLogRecord map2Data(Map<String, Object> dbMap) {
            HTTPAccessLogRecord record = new HTTPAccessLogRecord();
            super.map2Data(record, dbMap);
            return record;
        }
    }
}
