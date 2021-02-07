/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.e2e.log;

import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.skywalking.e2e.common.KeyValue;
import org.apache.skywalking.e2e.common.KeyValueMatcher;
import org.apache.skywalking.e2e.verification.AbstractMatcher;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.fail;

@Setter
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class LogMatcher extends AbstractMatcher<Log> {
    private String serviceName;
    private String serviceId;
    private String serviceInstanceName;
    private String serviceInstanceId;
    private String endpointName;
    private String endpointId;
    private String traceId;
    private String timestamp;
    private String contentType;
    private String content;
    private List<KeyValueMatcher> tags;

    @Override
    public void verify(final Log log) {
        if (nonNull(getServiceName())) {
            doVerify(getServiceName(), log.getServiceName());
        }
        if (nonNull(getServiceId())) {
            doVerify(getServiceId(), log.getServiceId());
        }
        if (nonNull(getServiceInstanceName())) {
            doVerify(getServiceInstanceName(), log.getServiceInstanceName());
        }
        if (nonNull(getServiceInstanceId())) {
            doVerify(getServiceInstanceId(), log.getServiceInstanceId());
        }
        if (nonNull(getEndpointName())) {
            doVerify(getEndpointName(), log.getEndpointName());
        }
        if (nonNull(getEndpointId())) {
            doVerify(getEndpointId(), log.getEndpointId());
        }
        if (nonNull(getTraceId())) {
            doVerify(getTraceId(), log.getTraceId());
        }
        if (nonNull(getTimestamp())) {
            doVerify(getTimestamp(), log.getTimestamp());
        }
        if (nonNull(getContentType())) {
            doVerify(getContentType(), log.getContentType());
        }
        if (nonNull(getContent())) {
            doVerify(getContent(), log.getContent());
        }
        if (nonNull(getTags())) {
            for (final KeyValueMatcher matcher : getTags()) {
                boolean matched = false;
                for (final KeyValue keyValue : log.getTags()) {
                    try {
                        matcher.verify(keyValue);
                        matched = true;
                    } catch (Throwable ignore) {

                    }
                }
                if (!matched) {
                    fail("\nExpected: %s\n Actual: %s", getTags(), log.getTags());
                }
            }
        }
    }
}
