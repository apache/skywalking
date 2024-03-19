/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.restapi;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.generator.Generator;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.source.Log;
import lombok.Data;
import lombok.SneakyThrows;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public final class LogRequest implements Generator<Object, Log> {
    @JsonIgnore
    private final ObjectMapper om = new ObjectMapper();

    private Generator<Object, Long> timestamp;
    private Generator<Object, String> serviceName;
    private Generator<Object, String> serviceInstanceName;
    private Generator<Object, String> endpointName;
    private Generator<Object, String> traceId;
    private Generator<Object, String> traceSegmentId;
    private Generator<Object, Long> spanId;
    private Generator<Object, Long> contentType;
    private Generator<Object, String> content;
    private Generator<Object, List<TagGenerator>> tags;
    private Generator<Object, Boolean> error;

    @SneakyThrows
    @Override
    public Log next(Object ignored) {
        final Log log = new Log();
        if (timestamp == null) {
            log.setTimestamp(System.currentTimeMillis());
        } else {
            log.setTimestamp(getTimestamp().next(null));
        }
        log.setServiceId(
            IDManager.ServiceID.buildId(
                getServiceName().next(null),
                true));
        log.setServiceInstanceId(
            IDManager.ServiceInstanceID.buildId(
                log.getServiceId(),
                getServiceInstanceName().next(null)));
        log.setEndpointId(
            IDManager.EndpointID.buildId(
                log.getServiceId(),
                getEndpointName().next(null)));
        log.setTraceId(getTraceId().next(null));
        log.setTraceSegmentId(getTraceSegmentId().next(null));
        log.setSpanId(getSpanId().next(null).intValue());
        log.setContentType(ContentType.instanceOf(getContentType().next(null).intValue()));
        log.setContent(getContent().next(null));
        log.setError(getError().next(null));
        log.setTimeBucket(TimeBucket.getRecordTimeBucket(log.getTimestamp()));
        log.setTags(
            getTags()
                .next(null)
                .stream()
                .map(tg -> tg.next(null))
                .collect(Collectors.<Tag>toList()));
        log.setTagsRawData(
            LogTags
                .newBuilder()
                .addAllData(
                    log
                        .getTags()
                        .stream()
                        .map(it -> KeyStringValuePair
                            .newBuilder()
                            .setKey(it.getKey())
                            .setValue(it.getValue())
                            .build())
                        .collect(Collectors.toList()))
                .build()
                .toByteArray());
        log.setUniqueId(UUID.randomUUID().toString());
        return log;
    }
}
