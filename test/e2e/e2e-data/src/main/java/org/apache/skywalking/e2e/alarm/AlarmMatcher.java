/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.e2e.alarm;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.e2e.common.KeyValue;
import org.apache.skywalking.e2e.common.KeyValueMatcher;
import org.apache.skywalking.e2e.event.Event;
import org.apache.skywalking.e2e.event.EventMatcher;
import org.apache.skywalking.e2e.verification.AbstractMatcher;
import org.springframework.util.CollectionUtils;

import java.util.List;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.fail;

@Slf4j
@Data
public class AlarmMatcher extends AbstractMatcher<Alarm> {
    private String startTime;
    private String scope;
    private String id;
    private String message;
    private List<KeyValueMatcher> tags;
    private List<EventMatcher> events;

    @Override
    public void verify(Alarm alarm) {
        doVerify(this.scope, alarm.getScope());
        doVerify(this.id, alarm.getId());
        doVerify(this.message, alarm.getMessage());
        if (nonNull(getTags())) {
            for (final KeyValueMatcher matcher : getTags()) {
                boolean matched = false;
                for (final KeyValue keyValue : alarm.getTags()) {
                    try {
                        matcher.verify(keyValue);
                        matched = true;
                    } catch (Throwable ignore) {

                    }
                }
                if (!matched) {
                    fail("\nExpected: %s\n Actual: %s", getTags(), alarm.getTags());
                }
            }
        }

        if (!CollectionUtils.isEmpty(getEvents())) {
            for (final EventMatcher matcher : getEvents()) {
                boolean matched = false;
                for (final Event event : alarm.getEvents()) {
                    try {
                        matcher.verify(event);
                        matched = true;
                    } catch (Throwable ignore) {
                        //ignore.
                    }
                }
                if (!matched) {
                    fail("\nExpected: %s\n Actual: %s", getEvents(), alarm.getEvents());
                }
            }
        }
    }
}
