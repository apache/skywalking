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

package org.apache.skywalking.oap.server.core.config.group.openapi;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.apache.skywalking.apm.util.StringFormatGroup;

public class EndpointGroupingRule4Openapi {
    private final Map<String/*serviceName*/, Map<String/*endpointName*/, String/*endpointGroupName*/>> directLookup = new HashMap<>();
    @Getter
    private final Map<String, Map<String, StringFormatGroup>> groupedRules = new HashMap<>();

    void addDirectLookup(String serviceName, String endpointName, String endpointGroupName) {
        Map<String, String> endpointNameLookup = directLookup.computeIfAbsent(serviceName, name -> new HashMap<>());
        endpointNameLookup.put(endpointName, endpointGroupName);
    }

    void addGroupedRule(String serviceName, String endpointGroupName, String ruleRegex) {
        String rulesGroupkey = getGroupedRulesKey(ruleRegex);
        Map<String, StringFormatGroup> rules = groupedRules.computeIfAbsent(serviceName, name -> new HashMap<>());
        StringFormatGroup formatGroup = rules.computeIfAbsent(rulesGroupkey, name -> new StringFormatGroup());
        formatGroup.addRule(endpointGroupName, ruleRegex);
    }

    public StringFormatGroup.FormatResult format(String service, String endpointName) {
        Map<String, String> endpointNameLookup = directLookup.get(service);
        if (endpointNameLookup != null && endpointNameLookup.get(endpointName) != null) {
            return new StringFormatGroup.FormatResult(true, endpointNameLookup.get(endpointName), endpointName);
        }

        Map<String, StringFormatGroup> rules = groupedRules.get(service);
        if (rules != null) {
            final StringFormatGroup stringFormatGroup = rules.get(getGroupedRulesKey(endpointName));
            if (stringFormatGroup != null) {
                return stringFormatGroup.format(endpointName);
            }
        }

        return new StringFormatGroup.FormatResult(false, endpointName, endpointName);
    }

    void sortRulesAll() {
        groupedRules.entrySet().forEach(rules -> {
            sortRulesByService(rules.getKey());
        });
    }

    void sortRulesByService(String serviceName) {
        Map<String, StringFormatGroup> rules = groupedRules.get(serviceName);
        if (rules != null) {
            rules.entrySet().forEach(stringFormatGroup -> {
                stringFormatGroup.getValue()
                                 .sortRules(new EndpointGroupingRule4Openapi.EndpointGroupingRulesComparator());
            });
        }
    }

    String getGroupedRulesKey(String string) {
        String[] ss = string.split("/");
        if (ss.length == 1) {   //eg. POST:/
            return ss[0] + "/";
        }
        if (ss.length > 1) {
            return ss[0] + "/" + ss[1];
        }
        return "/";
    }

    static class EndpointGroupingRulesComparator implements Comparator<StringFormatGroup.PatternRule> {
        private static final String VAR_PATTERN = "\\(\\[\\^\\/\\]\\+\\)";

        @Override
        public int compare(final StringFormatGroup.PatternRule rule1, final StringFormatGroup.PatternRule rule2) {

            String pattern1 = rule1.getPattern().pattern();
            String pattern2 = rule2.getPattern().pattern();

            if (getPatternVarsCount(pattern1) < getPatternVarsCount(pattern2)) {
                return -1;
            } else if (getPatternVarsCount(pattern1) > getPatternVarsCount(pattern2)) {
                return 1;
            }

            int length1 = getPatternLength(pattern1);
            int length2 = getPatternLength(pattern2);
            return length2 - length1;
        }

        private int getPatternVarsCount(String pattern) {
            return ",".concat(pattern).concat(",").split(VAR_PATTERN).length - 1;
        }

        private int getPatternLength(String pattern) {
            return pattern.replaceAll(VAR_PATTERN, "#").length();
        }

    }
}
