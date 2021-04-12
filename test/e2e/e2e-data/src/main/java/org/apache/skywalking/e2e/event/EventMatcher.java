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

package org.apache.skywalking.e2e.event;

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
public class EventMatcher extends AbstractMatcher<Event> {
    private String uuid;

    private Source source;

    private String name;

    private String type;

    private String message;

    private List<KeyValueMatcher> parameters;

    private String startTime;

    private String endTime;

    @Override
    public void verify(final Event event) {
        if (nonNull(getUuid())) {
            doVerify(getUuid(), event.getUuid());
        }
        if (nonNull(getSource())) {
            if (nonNull(getSource().getService())) {
                doVerify(getSource().getService(), event.getSource().getService());
            }
            if (nonNull(getSource().getServiceInstance())) {
                doVerify(getSource().getServiceInstance(), event.getSource().getServiceInstance());
            }
            if (nonNull(getSource().getEndpoint())) {
                doVerify(getSource().getEndpoint(), event.getSource().getEndpoint());
            }
        }
        if (nonNull(getName())) {
            doVerify(getName(), event.getName());
        }
        if (nonNull(getType())) {
            doVerify(getType(), event.getType());
        }
        if (nonNull(getMessage())) {
            doVerify(getMessage(), event.getMessage());
        }
        if (nonNull(getStartTime())) {
            doVerify(getStartTime(), event.getStartTime());
        }
        if (nonNull(getEndTime())) {
            doVerify(getEndTime(), event.getEndTime());
        }
        if (nonNull(getParameters())) {
            for (final KeyValueMatcher matcher : getParameters()) {
                boolean matched = false;
                for (final KeyValue keyValue : event.getParameters()) {
                    try {
                        matcher.verify(keyValue);
                        matched = true;
                    } catch (Throwable ignore) {

                    }
                }
                if (!matched) {
                    fail("\nExpected: %s\n Actual: %s", getParameters(), event.getParameters());
                }
            }
        }
    }

    @Getter
    @Setter
    @ToString
    public static class Source {
        private String service;

        private String serviceInstance;

        private String endpoint;
    }
}
