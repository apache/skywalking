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

package org.apache.skywalking.oap.trace.analysis.checker;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.TraceAnalyzer;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.tools.ExecFileLoader;
import org.yaml.snakeyaml.Yaml;

/**
 * The branch gate over the moved Java analysis: the branches of {@code trace-analysis-listener-v1} the comparison's
 * fixtures did not reach, read from the JaCoCo agent the build attaches, apart from the listed ones with their reasons in
 * {@code checker-allowed-misses.yml}, each listed with its exact count, so another miss on the same line still shows. The comparison shows the two engines behave alike only where the fixtures go.
 */
final class CheckerListenerCoverage {
    private CheckerListenerCoverage() {
    }

    /**
     * @return the problems: a missed branch that is not listed, and a listed one that is reached now
     */
    static List<String> problems() throws Exception {
        final ExecFileLoader loader = new ExecFileLoader();
        loader.load(new ByteArrayInputStream(executionData()));
        final CoverageBuilder coverage = new CoverageBuilder();
        final File moved = new File(TraceAnalyzer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        new Analyzer(loader.getExecutionDataStore(), coverage).analyzeAll(moved);

        final Map<String, String> missed = new TreeMap<>();
        for (final IClassCoverage type : coverage.getClasses()) {
            for (final IMethodCoverage method : type.getMethods()) {
                for (int line = method.getFirstLine(); line > 0 && line <= method.getLastLine(); line++) {
                    final ICounter branches = method.getLine(line).getBranchCounter();
                    if (branches.getMissedCount() > 0) {
                        missed.put(type.getName().replace('/', '.') + "#" + method.getName() + ":" + line,
                            branches.getMissedCount() + " of " + branches.getTotalCount() + " branches missed");
                    }
                }
            }
        }
        Files.createDirectories(Paths.get("target"));
        final StringBuilder report = new StringBuilder();
        missed.forEach((where, what) -> report.append(where).append(' ').append(what).append('\n'));
        Files.write(Paths.get("target", "checker-missed-branches.txt"), report.toString().getBytes(StandardCharsets.UTF_8));

        final Map<String, String> allowed = allowedMisses();
        final List<String> problems = new ArrayList<>();
        if (coverage.getClasses().isEmpty()) {
            problems.add("no moved class was analyzed from " + moved);
        }
        missed.forEach((where, what) -> {
            if (!allowed.containsKey(where + " " + what)) {
                problems.add("not reached by any fixture: " + where + " " + what);
            }
        });
        allowed.keySet().forEach(listed -> {
            final int at = listed.indexOf(' ');
            final String where = at < 0 ? listed : listed.substring(0, at);
            if (!(listed.substring(at + 1)).equals(missed.get(where))) {
                problems.add("listed as missed, but its branches are reached now or differently: " + listed);
            }
        });
        return problems;
    }

    /**
     * @return what the JaCoCo agent has recorded so far in this JVM
     */
    private static byte[] executionData() throws Exception {
        final Object agent = Class.forName("org.jacoco.agent.rt.RT").getMethod("getAgent").invoke(null);
        final Method data = agent.getClass().getMethod("getExecutionData", boolean.class);
        data.setAccessible(true);
        return (byte[]) data.invoke(agent, false);
    }

    private static Map<String, String> allowedMisses() throws Exception {
        try (InputStream in = CheckerListenerCoverage.class.getResourceAsStream("/checker-allowed-misses.yml")) {
            final Map<String, String> allowed = in == null ? null : new Yaml().load(in);
            return allowed == null ? new TreeMap<>() : allowed;
        }
    }
}
