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

package org.apache.skywalking.oap.trace.analyzer.v2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TALConfig;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TALConfigs;

/**
 * The shipped {@code tal/otlp-default.yaml}, read from {@code server-starter}'s resources.
 *
 * <p>Every test that needs a shipped rule comes through here rather than through a copy in test resources. A
 * copy would let the tests pass against text an OAP no longer boots with, which is the failure a test of the
 * shipped rules cannot afford. The file is not on this module's test classpath — {@code server-starter} depends
 * on this module, not the other way round — so it is located by path.
 */
public final class ShippedRules {
    private static final String RELATIVE_PATH =
        "oap-server/server-starter/src/main/resources/tal/otlp-default.yaml";

    private ShippedRules() {
    }

    /**
     * @return the shipped rule file
     */
    public static Path file() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int up = 0; up < 6 && dir != null; up++, dir = dir.getParent()) {
            final Path candidate = dir.resolve(RELATIVE_PATH);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate " + RELATIVE_PATH + " from " + Paths.get("").toAbsolutePath());
    }

    /**
     * Parse it the way the module boots it, so each rule carries the coordinates a generated class reports.
     *
     * @return the parsed file
     * @throws Exception when it cannot be read or parsed
     */
    public static TALConfigs configs() throws Exception {
        return TALConfigs.parse("otlp-default", Files.readAllBytes(file()), "otlp-default.yaml");
    }

    /**
     * @param name the rule as the shipped file names it
     * @return that rule
     * @throws Exception when the file cannot be read
     */
    public static TALConfig rule(final String name) throws Exception {
        return configs().getRules().stream()
                        .filter(rule -> name.equals(rule.getName()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("the shipped file has no rule " + name));
    }

    /**
     * @param name the rule as the shipped file names it
     * @return its DSL body
     * @throws Exception when the file cannot be read
     */
    public static String dsl(final String name) throws Exception {
        return rule(name).getDsl();
    }

    /**
     * @param name the rule as the shipped file names it
     * @return its source path and line, which is what a generated class reports in a stack frame
     * @throws Exception when the file cannot be read
     */
    public static DslSourceRef ref(final String name) throws Exception {
        final TALConfig rule = rule(name);
        return DslSourceRef.ofRule(rule.getSourcePath(), rule.getLineNo());
    }
}
