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
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rules;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
public class RuleLoaderFailTest {
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {Arrays.asList("not-exist-folder/*")},
            {Arrays.asList("not-exist-folder/not-exist-file.yml")},
            {Arrays.asList("not-exist-folder/not-exist-file.yaml")},
            {Arrays.asList("not-exist-single-file.yaml")},
            {Arrays.asList("test-folder/not-exist-file.yaml")},
            {Arrays.asList("test-folder/case1.yaml", "not-exist-single-file.yaml")},
        });
    }

    @ParameterizedTest
    @MethodSource("data")
    public void test(List<String> enabledRule) throws ModuleStartException, IOException {
        assertThrows(UnexpectedException.class, () -> Rules.loadRules("otel-rules", enabledRule));
    }
}
