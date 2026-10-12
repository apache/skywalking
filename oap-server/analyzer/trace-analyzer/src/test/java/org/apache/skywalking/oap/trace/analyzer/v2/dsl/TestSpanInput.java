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

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * A stand-in for a wire span: the attributes a rule reads through {@code attr()}, plus a couple of typed fields
 * so {@code span.*} chains have something to resolve against.
 */
@Getter
@Setter
public class TestSpanInput {
    private final Map<String, String> attributes = new HashMap<>();
    private String name = "";
    private int payloadSize;

    public TestSpanInput attribute(final String key, final String value) {
        attributes.put(key, value);
        return this;
    }
}
