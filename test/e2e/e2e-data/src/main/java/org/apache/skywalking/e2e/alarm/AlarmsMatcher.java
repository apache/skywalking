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
import org.junit.Assert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@Data
@Slf4j
public class AlarmsMatcher {
    private GetAlarm alarm;

    public void verify(final GetAlarm alarm) {
        LOGGER.info("alarms:{} matchers:{}", alarm, this.alarm);
        Assert.assertEquals(this.alarm.getTotal(), alarm.getTotal());

        assertThat(this.alarm.getMsgs()).hasSameSizeAs(alarm.getMsgs());

        for (int i = 0; i < this.alarm.getMsgs().size(); i++) {
            boolean matched = false;
            for (AlarmMatcher alarmMatcher : alarm.getMsgs()) {
                try {
                    this.alarm.getMsgs().get(i).verify(alarmMatcher);
                    matched = true;
                } catch (Throwable ignored) {
                }
            }
            if (!matched) {
                fail("\nExpected: %s\nActual: %s", this.alarm, alarm);
            }
        }
    }

}
