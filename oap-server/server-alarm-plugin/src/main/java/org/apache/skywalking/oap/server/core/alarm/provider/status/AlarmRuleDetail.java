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

package org.apache.skywalking.oap.server.core.alarm.provider.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Data;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;

@Data
public class AlarmRuleDetail {
    private String ruleId;
    private String expression;
    private int period;
    private int silencePeriod;
    private int additionalPeriod;
    private List<String> includeEntityNames = new ArrayList<>();
    private List<String> excludeEntityNames = new ArrayList<>();
    private String includeEntityNamesRegex;
    private String excludeEntityNamesRegex;
    private List<RunningEntity> runningEntities = new ArrayList<>();
    private List<Tag> tags = new ArrayList<>();
    private Set<String> hooks = new HashSet<>();
    private Set<String> includeMetrics = new HashSet<>();

    @Data
    public static class RunningEntity {
        private String scope;
        private String name;
        private String formattedMessage;
    }
}
