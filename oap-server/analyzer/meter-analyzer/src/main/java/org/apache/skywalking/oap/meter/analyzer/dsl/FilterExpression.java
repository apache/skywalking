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

import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToString(of = {"literal"})
public class FilterExpression {
    private final String literal;
    private final Closure<Boolean> filterClosure;

    @SuppressWarnings("unchecked")
    public FilterExpression(final String literal) {
        this.literal = literal;

        GroovyShell sh = new GroovyShell();
        filterClosure = (Closure<Boolean>) sh.evaluate(literal);
    }

    public Map<String, SampleFamily> filter(final Map<String, SampleFamily> sampleFamilies) {
        try {
            Map<String, SampleFamily> result = new HashMap<>();
            for (Map.Entry<String, SampleFamily> entry : sampleFamilies.entrySet()) {
                SampleFamily afterFilter = entry.getValue().filter(filterClosure);
                if (!Objects.equals(afterFilter, SampleFamily.EMPTY)) {
                    result.put(entry.getKey(), afterFilter);
                }
            }
            return result;
        } catch (Throwable t) {
            log.error("failed to run \"{}\"", literal, t);
        }
        return sampleFamilies;
    }
}
