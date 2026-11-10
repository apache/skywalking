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
 */

package org.apache.skywalking.oap.trace.analyzer.v2.dsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * An element of {@link TestNestedSpanInput}'s lists, and its nullable child.
 */
@Getter
@RequiredArgsConstructor
public class TestSpanChild {
    private final String label;

    public double getWeight() {
        return 1.5;
    }

    public double value(final int scale) {
        return scale * 1.5;
    }

    public String value(final String text) {
        return "text";
    }

    public double amount() {
        return 2.5;
    }

    public List<String> repeat(final int times) {
        return Collections.nCopies(times, label);
    }

    public List<Integer> lengths(final List<String> of) {
        final List<Integer> lengths = new ArrayList<>();
        for (final String each : of) {
            lengths.add(each.length());
        }
        return lengths;
    }
}
