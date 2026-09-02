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

package org.apache.skywalking.oap.meter.analyzer.v2.dsl.counter;

import com.google.common.collect.ImmutableMap;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@EqualsAndHashCode
@ToString
class ID {

    /**
     * The rule that is evaluating, i.e. the output metric name of the MAL rule
     * ({@code RunningContext.metricName}). Without it, two rules that reduce the SAME wire family
     * to the SAME label set share one window and difference against each other's values; with it
     * but without {@link #name}, several families inside ONE rule collide instead. The window is
     * per (rule, family, labels) because those two collisions are independent.
     */
    private final String owner;

    private final String name;

    private final ImmutableMap<String, String> labels;
}
