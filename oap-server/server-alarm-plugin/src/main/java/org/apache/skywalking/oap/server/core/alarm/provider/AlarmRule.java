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

package org.apache.skywalking.oap.server.core.alarm.provider;

import java.util.ArrayList;
import java.util.Objects;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author wusheng
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter(AccessLevel.PUBLIC)
@Getter(AccessLevel.PUBLIC)
public class AlarmRule {
    private String alarmRuleName;

    private String metricsName;
    private ArrayList<String> includeNames;
    private ArrayList<String> excludeNames;
    private String threshold;
    private String op;
    private int period;
    private int count;
    private int silencePeriod;
    private String message;

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final AlarmRule alarmRule = (AlarmRule) o;

        return period == alarmRule.period
            && count == alarmRule.count
            && silencePeriod == alarmRule.silencePeriod
            && Objects.equals(alarmRuleName, alarmRule.alarmRuleName)
            && Objects.equals(metricsName, alarmRule.metricsName)
            && Objects.equals(includeNames, alarmRule.includeNames)
            && Objects.equals(excludeNames, alarmRule.excludeNames)
            && Objects.equals(threshold, alarmRule.threshold)
            && Objects.equals(op, alarmRule.op)
            && Objects.equals(message, alarmRule.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alarmRuleName, metricsName, includeNames, excludeNames, threshold, op, period, count, silencePeriod, message);
    }
}
