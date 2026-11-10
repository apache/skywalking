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

import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Tells, from the call stack an event is recorded on, which phase of the segment's analysis made it and which part of
 * the analysis it belongs to, so the two sides' events carry the same annotation when they come from the same place.
 */
interface CheckerFrameClassifier {
    /**
     * The owners of the lines the span loop writes itself, outside any listener callback or rule run.
     */
    Set<String> LOOP_OWNERS = Set.of("TraceAnalyzer", "SwNativeRuleRunner");

    /**
     * @param frames the stack the event is recorded on, innermost first
     * @return where the event comes from, or null when no frame tells
     */
    CheckerAttribution classify(StackTraceElement[] frames);

    /**
     * Where an event comes from: its phase ({@code segment}, {@code first}, {@code span} or {@code build}), its
     * canonical group (the Java listener's, which on the rule side is the rule that ports it), and its owner, the
     * listener class or the rule, which only the guards read.
     */
    @Getter
    @RequiredArgsConstructor
    final class CheckerAttribution {
        static final CheckerAttribution UNKNOWN = new CheckerAttribution("-", "-", "-");

        private final String phase;
        private final String group;
        private final String owner;
    }
}
