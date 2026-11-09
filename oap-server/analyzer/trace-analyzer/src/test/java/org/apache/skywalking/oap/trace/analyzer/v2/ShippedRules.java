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
import org.apache.skywalking.oap.server.core.source.DatabaseSlowStatement;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TALConfig;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TALConfigs;

/**
 * The shipped {@code tal/otlp-general.yaml}, and the other shipped OTLP rule files, read from {@code server-starter}'s
 * resources.
 *
 * <p>Every test that needs a shipped rule comes through here rather than through a copy in test resources. A
 * copy would let the tests pass against text an OAP no longer boots with, which is the failure a test of the
 * shipped rules cannot afford. The file is not on this module's test classpath — {@code server-starter} depends
 * on this module, not the other way round — so it is located by path.
 */
public final class ShippedRules {
    private static final String RULE_DIR = "oap-server/server-starter/src/main/resources/tal/";

    private ShippedRules() {
    }

    /**
     * @return the shipped rule file
     */
    public static Path file() {
        return file("otlp-general");
    }

    /**
     * @param fileName the shipped rule file's name without its extension, such as {@code otlp-ai-evaluation}
     * @return that file
     */
    public static Path file(final String fileName) {
        final String relativePath = RULE_DIR + fileName + ".yaml";
        Path dir = Paths.get("").toAbsolutePath();
        for (int up = 0; up < 6 && dir != null; up++, dir = dir.getParent()) {
            final Path candidate = dir.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate " + relativePath + " from " + Paths.get("").toAbsolutePath());
    }

    /**
     * Parse it the way the module boots it, so each rule carries the coordinates a generated class reports.
     *
     * @return the parsed file
     * @throws Exception when it cannot be read or parsed
     */
    public static TALConfigs configs() throws Exception {
        return configs("otlp-general");
    }

    /**
     * @param fileName the shipped rule file's name without its extension
     * @return that file, parsed the way the module boots it
     * @throws Exception when it cannot be read or parsed
     */
    public static TALConfigs configs(final String fileName) throws Exception {
        return TALConfigs.parse(fileName, Files.readAllBytes(file(fileName)), fileName + ".yaml");
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

    /**
     * @param fileName the shipped rule file's name without its extension
     * @param name     the rule as that file names it
     * @return its source path and line
     * @throws Exception when the file cannot be read
     */
    public static DslSourceRef ref(final String fileName, final String name) throws Exception {
        final TALConfig rule = configs(fileName).getRules().stream()
                                                .filter(r -> name.equals(r.getName()))
                                                .findFirst()
                                                .orElseThrow(() -> new AssertionError(fileName + " has no rule " + name));
        return DslSourceRef.ofRule(rule.getSourcePath(), rule.getLineNo());
    }

    /**
     * Declare the scope of the source the shipped slow-statement rule sends, which a boot declares by scanning the
     * class path; the rule's {@code source DatabaseSlowStatement {}} compiles against it.
     */
    public static void declareSlowStatementScope() {
        if (DefaultScopeDefine.classOf("DatabaseSlowStatement") == null) {
            new DefaultScopeDefine.Listener().notify(DatabaseSlowStatement.class);
        }
    }
}
