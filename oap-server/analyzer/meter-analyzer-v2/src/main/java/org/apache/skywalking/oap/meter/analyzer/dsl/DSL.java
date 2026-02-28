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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * Same-FQCN replacement for upstream MAL DSL.
 * Loads transpiled {@link MalExpression} classes from mal-expressions.txt manifest
 * instead of Groovy {@code DelegatingScript} classes -- no Groovy runtime needed.
 */
@Slf4j
public final class DSL {
    private static final String MANIFEST_PATH = "META-INF/mal-expressions.txt";
    private static volatile Map<String, String> SCRIPT_MAP;
    private static final AtomicInteger LOADED_COUNT = new AtomicInteger();

    /**
     * Parse string literal to Expression object, which can be reused.
     *
     * @param metricName the name of metric defined in mal rule
     * @param expression string literal represents the DSL expression.
     * @return Expression object could be executed.
     */
    public static Expression parse(final String metricName, final String expression) {
        if (metricName == null) {
            throw new UnsupportedOperationException(
                "Init expressions (metricName=null) are not supported in v2 mode. "
                    + "All init expressions must be pre-compiled at build time.");
        }

        final Map<String, String> scriptMap = loadManifest();
        final String className = scriptMap.get(metricName);
        if (className == null) {
            throw new IllegalStateException(
                "Transpiled MAL expression not found for metric: " + metricName
                    + ". Available: " + scriptMap.size() + " expressions");
        }

        try {
            final Class<?> exprClass = Class.forName(className);
            final MalExpression malExpr = (MalExpression) exprClass.getDeclaredConstructor().newInstance();
            final int count = LOADED_COUNT.incrementAndGet();
            log.debug("Loaded transpiled MAL expression [{}/{}]: {}", count, scriptMap.size(), metricName);
            return new Expression(metricName, expression, malExpr);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                "Transpiled MAL expression class not found: " + className, e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Failed to instantiate transpiled MAL expression: " + className, e);
        }
    }

    private static Map<String, String> loadManifest() {
        if (SCRIPT_MAP != null) {
            return SCRIPT_MAP;
        }
        synchronized (DSL.class) {
            if (SCRIPT_MAP != null) {
                return SCRIPT_MAP;
            }
            final Map<String, String> map = new HashMap<>();
            try (InputStream is = DSL.class.getClassLoader().getResourceAsStream(MANIFEST_PATH)) {
                if (is == null) {
                    log.warn("MAL expression manifest not found: {}", MANIFEST_PATH);
                    SCRIPT_MAP = map;
                    return map;
                }
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) {
                            continue;
                        }
                        final String simpleName = line.substring(line.lastIndexOf('.') + 1);
                        if (simpleName.startsWith("MalExpr_")) {
                            final String metric = simpleName.substring("MalExpr_".length());
                            map.put(metric, line);
                        }
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load MAL expression manifest", e);
            }
            log.info("Loaded {} transpiled MAL expressions from manifest", map.size());
            SCRIPT_MAP = map;
            return map;
        }
    }
}
