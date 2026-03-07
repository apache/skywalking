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

package org.apache.skywalking.oap.meter.analyzer.v2.dsl;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALClassGenerator;

/**
 * Compiles a MAL filter closure expression into a {@link MalFilter}
 * using ANTLR4 parsing and Javassist bytecode generation.
 */
@Slf4j
@ToString(of = {"literal"})
public class FilterExpression {
    private static final MALClassGenerator GENERATOR = new MALClassGenerator();

    private final String literal;
    private final MalFilter malFilter;

    public FilterExpression(final String literal) {
        this(literal, null, null);
    }

    public FilterExpression(final String literal, final String filterNameHint) {
        this(literal, filterNameHint, null);
    }

    public FilterExpression(final String literal,
                            final String filterNameHint,
                            final String yamlSource) {
        this.literal = literal;
        try {
            if (filterNameHint != null) {
                GENERATOR.setClassNameHint(filterNameHint);
            }
            GENERATOR.setYamlSource(yamlSource);
            try {
                this.malFilter = GENERATOR.compileFilter(literal);
            } finally {
                GENERATOR.setClassNameHint(null);
                GENERATOR.setYamlSource(null);
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to compile MAL filter expression: " + literal, e);
        }
    }

    public Map<String, SampleFamily> filter(final Map<String, SampleFamily> sampleFamilies) {
        try {
            final Map<String, SampleFamily> result = new HashMap<>();
            for (final Map.Entry<String, SampleFamily> entry : sampleFamilies.entrySet()) {
                final SampleFamily afterFilter = entry.getValue().filter(malFilter::test);
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
