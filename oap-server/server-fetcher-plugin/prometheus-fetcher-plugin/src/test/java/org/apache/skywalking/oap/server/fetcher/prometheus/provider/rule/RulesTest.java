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

package org.apache.skywalking.oap.server.fetcher.prometheus.provider.rule;

import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rule;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rules;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class RulesTest {

    @Test
    public void testFetcherPrometheusRulesLoader() throws ModuleStartException {
        List<Rule> rr = Rules.loadRules("fetcher-prom-rules", Collections.singletonList("localhost"));

        assertThat(rr.size(), is(1));
    }

}