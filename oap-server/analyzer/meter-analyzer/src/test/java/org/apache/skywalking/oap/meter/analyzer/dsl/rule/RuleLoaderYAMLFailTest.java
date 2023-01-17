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
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Slf4j
@RunWith(Parameterized.class)
public class RuleLoaderYAMLFailTest {
    @Parameterized.Parameter
    public List<String> enabledRule;

    @Parameterized.Parameters(name = "{index}: {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {Arrays.asList("illegal-yaml/test.yml")},
                {Arrays.asList("illegal-yaml/test")},
                {Arrays.asList("illegal-yaml/*.yml")},
                {Arrays.asList("/illegal-yaml/*")},
        });
    }

    @Test(expected = YAMLException.class)
    public void test() throws ModuleStartException, IOException {
        Rules.loadRules("otel-rules", enabledRule);
    }
}
