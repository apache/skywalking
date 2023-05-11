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

package org.apache.skywalking.oap.meter.analyzer.dsl.rule;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rule;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rules;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Slf4j
public class RuleLoaderTest {
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {Arrays.asList("test-folder/*.yml"), 1},
                {Arrays.asList("test-folder/*.yaml"), 2},
                {Arrays.asList("test-folder/*"), 3},
                {Arrays.asList("/test-folder/*"), 3},

                {Arrays.asList("test-folder/case1"), 1},
                {Arrays.asList("/test-folder/case1.yaml"), 1},
                {Arrays.asList("/test-folder/case2.yml"), 1},

                {Arrays.asList("single-file-case.yaml"), 1},
                {Arrays.asList("single-file-case"), 1},
                {Arrays.asList("/single-file-case"), 1},

                {Arrays.asList("/single-file-case.yaml", "test-folder/*"), 4},
                {Arrays.asList("/single-file-case.yaml", "test-folder/*.yml"), 2},
                {Arrays.asList("/single-file-case.yaml", "test-folder/case1", "/test-folder/case2"), 3},
                // test leading and trailing whitespace
                {Arrays.asList("   /single-file-case.yaml    "), 1},
        });
    }

    @ParameterizedTest
    @MethodSource("data")
    public void test(List<String> enabledRule,
                     int rulesNumber) throws ModuleStartException, IOException {
        List<Rule> rules = Rules.loadRules("otel-rules", enabledRule);
        assertThat(rules.size()).isEqualTo(rulesNumber);
    }

}
