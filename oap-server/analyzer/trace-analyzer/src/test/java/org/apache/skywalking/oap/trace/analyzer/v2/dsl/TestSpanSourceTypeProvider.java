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

import java.util.Collections;
import java.util.Map;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.TALSourceTypeProvider;

/**
 * Serves {@link TestSpanInput} to the runtime so an execution test can control exactly which attributes a rule
 * sees.
 */
public class TestSpanSourceTypeProvider implements TALSourceTypeProvider {
    @Override
    public String alias() {
        return "test";
    }

    @Override
    public Class<?> inputType() {
        return TestSpanInput.class;
    }

    @Override
    public Map<String, String> spanAttributes(final Object input) {
        return input instanceof TestSpanInput
            ? ((TestSpanInput) input).getAttributes() : Collections.emptyMap();
    }
}
