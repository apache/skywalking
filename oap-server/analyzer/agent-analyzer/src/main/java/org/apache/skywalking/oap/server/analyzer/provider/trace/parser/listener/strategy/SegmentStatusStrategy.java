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

package org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.strategy;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Define the available strategies for analysis segment status analysis.
 */
@AllArgsConstructor
public enum SegmentStatusStrategy {
    /**
     * `FROM_SPAN_STATUS` represents the segment status would be error if any span is in error status.
     */
    FROM_SPAN_STATUS(new FromSpanStatus()),
    /**
     * `FROM_ENTRY_SPAN` means the segment status would be determined by the status of entry spans only.
     *
     * @see FromEntrySpan
     */
    FROM_ENTRY_SPAN(new FromEntrySpan()),
    /**
     * `FROM_FIRST_SPAN` means the segment status would be determined by the status of the first span only.
     */
    FROM_FIRST_SPAN(new FromFirstSpan());

    @Getter
    private final SegmentStatusAnalyzer exceptionAnalyzer;

    public static SegmentStatusStrategy findByName(String name) {
        for (final SegmentStatusStrategy strategy : SegmentStatusStrategy.values()) {
            if (strategy.name().equalsIgnoreCase(name)) {
                return strategy;
            }
        }
        return FROM_SPAN_STATUS;
    }
}
