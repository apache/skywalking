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
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@ToString
@EqualsAndHashCode
public class AlarmRule {
    private String alarmRuleName;
    private String metricsName;
    private ArrayList<String> includeNames;
    private String includeNamesRegex;
    private ArrayList<String> excludeNames;
    private String excludeNamesRegex;
    private ArrayList<String> includeLabels;
    private String includeLabelsRegex;
    private ArrayList<String> excludeLabels;
    private String excludeLabelsRegex;
    private String threshold;
    private String op;
    private int period;
    private int count;
    private int silencePeriod;
    private String message;
    private boolean onlyAsCondition;
    private Map<String, String> tags;
    private Set<String> hooks;
}
