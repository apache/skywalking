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

package org.apache.skywalking.oap.server.core.config.group;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.util.StringFormatGroup;

/**
 * Endpoint group rule hosts all group rules of all services.
 */
public class EndpointGroupingRule {
    private Map<String, StringFormatGroup> rules = new HashMap<>();

    /**
     * Add a new rule to the context.
     *
     * @param serviceName       of the new rule
     * @param endpointGroupName represents the logic endpoint name.
     * @param ruleRegex         match the endpoints which should be in the group name.
     */
    void addRule(String serviceName, String endpointGroupName, String ruleRegex) {
        final StringFormatGroup formatGroup = rules.computeIfAbsent(serviceName, name -> new StringFormatGroup());
        formatGroup.addRule(endpointGroupName, ruleRegex);
    }

    /**
     * @param service      of the given endpoint belonged.
     * @param endpointName to do group checking.
     * @return group result and new endpoint name if rule matched.
     */
    public StringFormatGroup.FormatResult format(String service, String endpointName) {
        final StringFormatGroup stringFormatGroup = rules.get(service);
        if (stringFormatGroup != null) {
            return stringFormatGroup.format(endpointName);
        } else {
            return new StringFormatGroup.FormatResult(false, endpointName, endpointName);
        }
    }
}
