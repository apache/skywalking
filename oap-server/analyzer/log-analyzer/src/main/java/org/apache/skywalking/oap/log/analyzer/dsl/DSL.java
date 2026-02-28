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

package org.apache.skywalking.oap.log.analyzer.dsl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.log.analyzer.dsl.spec.filter.FilterSpec;
import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;

/**
 * Same-FQCN replacement for upstream LAL DSL.
 * Loads pre-compiled {@link LalExpression} classes from lal-expressions.txt manifest
 * (keyed by SHA-256 hash) instead of Groovy {@code GroovyShell} runtime compilation.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class DSL {
    private static final String MANIFEST_PATH = "META-INF/lal-expressions.txt";
    private static volatile Map<String, String> EXPRESSION_MAP;
    private static final AtomicInteger LOADED_COUNT = new AtomicInteger();

    private final LalExpression expression;
    private final FilterSpec filterSpec;
    private Binding binding;

    public static DSL of(final ModuleManager moduleManager,
                         final LogAnalyzerModuleConfig config,
                         final String dsl) throws ModuleStartException {
        final Map<String, String> exprMap = loadManifest();
        final String dslHash = sha256(dsl);
        final String className = exprMap.get(dslHash);
        if (className == null) {
            throw new ModuleStartException(
                "Pre-compiled LAL expression not found for DSL hash: " + dslHash
                    + ". Available: " + exprMap.size() + " expressions.");
        }

        try {
            final Class<?> exprClass = Class.forName(className);
            final LalExpression expression = (LalExpression) exprClass.getDeclaredConstructor().newInstance();
            final FilterSpec filterSpec = new FilterSpec(moduleManager, config);
            final int count = LOADED_COUNT.incrementAndGet();
            log.debug("Loaded pre-compiled LAL expression [{}/{}]: {}", count, exprMap.size(), className);
            return new DSL(expression, filterSpec);
        } catch (ClassNotFoundException e) {
            throw new ModuleStartException(
                "Pre-compiled LAL expression class not found: " + className, e);
        } catch (ReflectiveOperationException e) {
            throw new ModuleStartException(
                "Pre-compiled LAL expression instantiation failed: " + className, e);
        }
    }

    public void bind(final Binding binding) {
        this.binding = binding;
        this.filterSpec.bind(binding);
    }

    public void evaluate() {
        expression.execute(filterSpec, binding);
    }

    private static Map<String, String> loadManifest() {
        if (EXPRESSION_MAP != null) {
            return EXPRESSION_MAP;
        }
        synchronized (DSL.class) {
            if (EXPRESSION_MAP != null) {
                return EXPRESSION_MAP;
            }
            final Map<String, String> map = new HashMap<>();
            try (InputStream is = DSL.class.getClassLoader().getResourceAsStream(MANIFEST_PATH)) {
                if (is == null) {
                    log.warn("LAL expression manifest not found: {}", MANIFEST_PATH);
                    EXPRESSION_MAP = map;
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
                        final String[] parts = line.split("=", 2);
                        if (parts.length == 2) {
                            map.put(parts[0], parts[1]);
                        }
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load LAL expression manifest", e);
            }
            log.info("Loaded {} pre-compiled LAL expressions from manifest", map.size());
            EXPRESSION_MAP = map;
            return map;
        }
    }

    static String sha256(final String input) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder();
            for (final byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
