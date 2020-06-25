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

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

public abstract class AbstractLogRecord extends Record {

    public static final String SERVICE_ID = "service_id";
    public static final String SERVICE_INSTANCE_ID = "service_instance_id";
    public static final String ENDPOINT_NAME = "endpoint_name";
    public static final String ENDPOINT_ID = "endpoint_id";
    public static final String TRACE_ID = "trace_id";
    public static final String IS_ERROR = "is_error";
    public static final String STATUS_CODE = "status_code";
    public static final String CONTENT_TYPE = "content_type";
    public static final String CONTENT = "content";
    public static final String TIMESTAMP = "timestamp";

    @Setter
    @Getter
    @Column(columnName = SERVICE_ID)
    private int serviceId;
    @Setter
    @Getter
    @Column(columnName = SERVICE_INSTANCE_ID)
    private int serviceInstanceId;
    @Setter
    @Getter
    @Column(columnName = ENDPOINT_ID)
    private String endpointId;
    @Setter
    @Getter
    @Column(columnName = ENDPOINT_NAME)
    private String endpointName;
    @Setter
    @Getter
    @Column(columnName = TRACE_ID)
    private String traceId;
    @Setter
    @Getter
    @Column(columnName = IS_ERROR)
    private int isError;
    @Setter
    @Getter
    @Column(columnName = STATUS_CODE)
    private String statusCode;
    @Setter
    @Getter
    @Column(columnName = CONTENT_TYPE)
    private int contentType = ContentType.NONE.value();
    @Setter
    @Getter
    @Column(columnName = CONTENT)
    private String content;
    @Setter
    @Getter
    @Column(columnName = TIMESTAMP)
    private long timestamp;

    @Override
    public String id() {
        throw new UnexpectedException("AbstractLogRecord doesn't provide id()");
    }

    public static abstract class Builder<T extends AbstractLogRecord> implements StorageBuilder<T> {
        protected void map2Data(T record, Map<String, Object> dbMap) {
            record.setServiceId(((Number) dbMap.get(SERVICE_ID)).intValue());
            record.setServiceInstanceId(((Number) dbMap.get(SERVICE_INSTANCE_ID)).intValue());
            record.setEndpointId((String) dbMap.get(ENDPOINT_ID));
            record.setEndpointName((String) dbMap.get(ENDPOINT_NAME));
            record.setIsError(((Number) dbMap.get(IS_ERROR)).intValue());
            record.setTraceId((String) dbMap.get(TRACE_ID));
            record.setStatusCode((String) dbMap.get(STATUS_CODE));
            record.setContentType(((Number) dbMap.get(CONTENT_TYPE)).intValue());
            record.setContent((String) dbMap.get(CONTENT));
            record.setTimestamp(((Number) dbMap.get(TIMESTAMP)).longValue());
            record.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
        }

        @Override
        public Map<String, Object> data2Map(AbstractLogRecord record) {
            Map<String, Object> map = new HashMap<>();
            map.put(SERVICE_ID, record.getServiceId());
            map.put(SERVICE_INSTANCE_ID, record.getServiceInstanceId());
            map.put(ENDPOINT_ID, record.getEndpointId());
            map.put(ENDPOINT_NAME, record.getEndpointName());
            map.put(TRACE_ID, record.getTraceId());
            map.put(IS_ERROR, record.getIsError());
            map.put(STATUS_CODE, record.getStatusCode());
            map.put(TIME_BUCKET, record.getTimeBucket());
            map.put(CONTENT_TYPE, record.getContentType());
            map.put(CONTENT, record.getContent());
            map.put(TIMESTAMP, record.getTimestamp());
            return map;
        }
    }
}
