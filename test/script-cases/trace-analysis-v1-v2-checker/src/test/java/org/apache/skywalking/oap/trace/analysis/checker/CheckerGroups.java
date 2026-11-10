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

package org.apache.skywalking.oap.trace.analysis.checker;

import java.util.HashMap;
import java.util.Map;

/**
 * The canonical groups the two sides' events are annotated with: the Java listener or processor an event belongs to,
 * which on the rule side is the rule that ports it.
 */
final class CheckerGroups {
    static final String LISTENER = "org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.";
    static final String TRACE_ANALYZER = "org.apache.skywalking.oap.server.analyzer.provider.trace.parser.TraceAnalyzer";
    static final String GEN_AI_METER_ANALYZER = "org.apache.skywalking.oap.analyzer.genai.service.GenAIMeterAnalyzer";
    static final String RUNNER = "org.apache.skywalking.oap.trace.analyzer.v2.provider.nativetrace.SwNativeRuleRunner";

    private static final Map<String, String> LISTENERS = listeners();
    private static final Map<String, String> RULES = rules();

    private CheckerGroups() {
    }

    /**
     * @return the group of a v1 class, or null for a class that is not a listener, a processor or the GenAI analyzer
     */
    static String ofClass(final String className) {
        return LISTENERS.get(className);
    }

    /**
     * @return the group of a shipped native rule
     * @throws IllegalArgumentException for a rule this map does not know, so a new shipped rule is mapped here
     */
    static String ofRule(final String rule) {
        final String group = RULES.get(rule);
        if (group == null) {
            throw new IllegalArgumentException("no canonical group for the native rule " + rule);
        }
        return group;
    }

    private static Map<String, String> listeners() {
        final Map<String, String> groups = new HashMap<>();
        groups.put(LISTENER + "RPCAnalysisListener", "rpc");
        groups.put(LISTENER + "EndpointDepFromCrossThreadAnalysisListener", "cross-thread");
        groups.put(LISTENER + "NetworkAddressAliasMappingListener", "alias");
        groups.put(LISTENER + "SegmentAnalysisListener", "segment");
        groups.put(LISTENER + "VirtualServiceAnalysisListener", "virtual-service");
        groups.put(LISTENER + "vservice.VirtualCacheProcessor", "virtual-service");
        groups.put(LISTENER + "vservice.VirtualDatabaseProcessor", "virtual-service");
        groups.put(LISTENER + "vservice.VirtualMQProcessor", "virtual-service");
        groups.put(LISTENER + "vservice.VirtualGenAIProcessor", "genai");
        groups.put(GEN_AI_METER_ANALYZER, "genai");
        groups.put(LISTENER + "GenAIEvaluationAnalysisListener", "evaluation");
        return groups;
    }

    private static Map<String, String> rules() {
        final Map<String, String> groups = new HashMap<>();
        groups.put("native-rpc-entry", "rpc");
        groups.put("native-rpc-exit", "rpc");
        groups.put("native-rpc-logic-endpoint", "rpc");
        groups.put("native-endpoint-cross-thread", "cross-thread");
        groups.put("native-network-alias", "alias");
        groups.put("native-segment", "segment");
        groups.put("native-virtual-cache", "virtual-service");
        groups.put("native-virtual-database", "virtual-service");
        groups.put("native-virtual-mq", "virtual-service");
        groups.put("native-virtual-genai", "genai");
        groups.put("native-ai-evaluation", "evaluation");
        return groups;
    }

    /**
     * @return a frame's method name, the enclosing method for a lambda body
     */
    static String methodOf(final StackTraceElement frame) {
        final String method = frame.getMethodName();
        if (method.startsWith("lambda$")) {
            final int end = method.indexOf('$', "lambda$".length());
            return end > 0 ? method.substring("lambda$".length(), end) : method;
        }
        return method;
    }
}
