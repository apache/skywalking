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

package org.apache.skywalking.oap.server.core.config.group.uri.quickmatch;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.oap.server.library.util.StringFormatGroup;

public class QuickUriGroupingRule {
    private Map<String, PatternTree> rules = new HashMap<>();

    public void addRule(String serviceName, String pattern) {
        final PatternTree patternTree = rules.computeIfAbsent(serviceName, name -> new PatternTree());
        patternTree.addPattern(pattern);
    }

    public StringFormatGroup.FormatResult format(String service, String endpointName) {
        final PatternTree patternTree = rules.get(service);
        if (patternTree != null) {
            return patternTree.match(endpointName);
        } else {
            return new StringFormatGroup.FormatResult(false, endpointName, endpointName);
        }
    }
}
