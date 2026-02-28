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

package org.apache.skywalking.oap.meter.analyzer.dsl;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Same-FQCN replacement for upstream FilterExpression.
 * Loads transpiled {@link MalFilter} classes from mal-filter-expressions.properties
 * manifest instead of Groovy filter closures -- no Groovy runtime needed.
 */
@Slf4j
@ToString(of = {"literal"})
public class FilterExpression {
    private static final String MANIFEST_PATH = "META-INF/mal-filter-expressions.properties";
    private static volatile Map<String, String> FILTER_MAP;
    private static final AtomicInteger LOADED_COUNT = new AtomicInteger();

    private final String literal;
    private final MalFilter malFilter;

    @SuppressWarnings("unchecked")
    public FilterExpression(final String literal) {
        this.literal = literal;

        final Map<String, String> filterMap = loadManifest();
        final String className = filterMap.get(literal);
        if (className == null) {
            throw new IllegalStateException(
                "Transpiled MAL filter not found for: " + literal
                    + ". Available filters: " + filterMap.size());
        }

        try {
            final Class<?> filterClass = Class.forName(className);
            malFilter = (MalFilter) filterClass.getDeclaredConstructor().newInstance();
            final int count = LOADED_COUNT.incrementAndGet();
            log.debug("Loaded transpiled MAL filter [{}/{}]: {}", count, filterMap.size(), literal);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                "Transpiled MAL filter class not found: " + className, e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Failed to instantiate transpiled MAL filter: " + className, e);
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

    private static Map<String, String> loadManifest() {
        if (FILTER_MAP != null) {
            return FILTER_MAP;
        }
        synchronized (FilterExpression.class) {
            if (FILTER_MAP != null) {
                return FILTER_MAP;
            }
            final Map<String, String> map = new HashMap<>();
            try (InputStream is = FilterExpression.class.getClassLoader().getResourceAsStream(MANIFEST_PATH)) {
                if (is == null) {
                    log.warn("MAL filter manifest not found: {}", MANIFEST_PATH);
                    FILTER_MAP = map;
                    return map;
                }
                final Properties props = new Properties();
                props.load(is);
                props.forEach((k, v) -> map.put((String) k, (String) v));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load MAL filter manifest", e);
            }
            log.info("Loaded {} transpiled MAL filters from manifest", map.size());
            FILTER_MAP = map;
            return map;
        }
    }
}
