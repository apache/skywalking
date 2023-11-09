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

package org.apache.skywalking.oap.meter.analyzer.dsl;

import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;

/**
 * ExpressionRunningContext contains states in running phase of an expression.
 */
@Getter
@Builder
public class ExpressionRunningContext {

    static ExpressionRunningContext create(String metricName, Map<String, SampleFamily> sampleFamilies) {
        if (CACHE.get() == null) {
            CACHE.set(ExpressionRunningContext.builder()
                                              .metricName(metricName)
                                              .sampleFamilies(sampleFamilies).build());
        }
        return CACHE.get();
    }

    static Optional<ExpressionRunningContext> get() {
        return Optional.ofNullable(CACHE.get());
    }

    private final static ThreadLocal<ExpressionRunningContext> CACHE = new ThreadLocal<>();

    private String metricName;

    private Map<String, SampleFamily> sampleFamilies;

    public void close() {
        CACHE.remove();
    }
}
