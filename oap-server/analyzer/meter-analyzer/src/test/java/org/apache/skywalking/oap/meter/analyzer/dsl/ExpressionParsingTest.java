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

package org.apache.skywalking.oap.meter.analyzer.dsl;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
public class ExpressionParsingTest {
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {
                "all",
                "(foo - 1).tag({ tags -> tags.new_name = \"${tags['tag1']}|${tags['tag2']}\".toString() }).tagEqual('bar', '1').sum(['tt']).irate().histogram().histogram_percentile([50,99]).service(['rr'], Layer.GENERAL).downsampling(LATEST)",
                ExpressionParsingContext.builder()
                                        .samples(Collections.singletonList("foo"))
                                        .scopeType(ScopeType.SERVICE)
                                        .scopeLabels(Sets.newHashSet("rr"))
                                        .aggregationLabels(Sets.newHashSet("tt"))
                                        .downsampling(DownsamplingType.LATEST)
                                        .isHistogram(true)
                                        .percentiles(new int[]{50, 99}).build(),
                false,
            },
            {
                "sumThenAvg",
                "(foo - 1).tagEqual('bar', '1').sum(['tt']).irate().histogram().histogram_percentile([50,99]).service(['rr'], Layer.GENERAL).avg(['tt'])",
                ExpressionParsingContext.builder()
                                        .samples(Collections.singletonList("foo"))
                                        .scopeType(ScopeType.SERVICE)
                                        .scopeLabels(Sets.newHashSet("rr"))
                                        .aggregationLabels(Sets.newHashSet("tt"))
                                        .downsampling(DownsamplingType.AVG)
                                        .isHistogram(true)
                                        .percentiles(new int[]{50, 99}).build(),
                false,
            },
            {
                "avgThenOthersThenSum",
                "(foo - 1).tagEqual('bar', '1').avg(['tt']).irate().histogram().histogram_percentile([50,99]).service(['rr'], Layer.GENERAL).sum(['tt']).downsampling(SUM)",
                ExpressionParsingContext.builder()
                                        .samples(Collections.singletonList("foo"))
                                        .scopeType(ScopeType.SERVICE)
                                        .scopeLabels(Sets.newHashSet("rr"))
                                        .aggregationLabels(Sets.newHashSet("tt"))
                                        .downsampling(DownsamplingType.SUM)
                                        .isHistogram(true)
                                        .percentiles(new int[]{50, 99}).build(),
                false,
            },
            {
                "sameSamples",
                "(node_cpu_seconds_total.sum(['node_identifier_host_name']) - node_cpu_seconds_total.tagEqual('mode', 'idle').sum(['node_identifier_host_name'])).service(['node_identifier_host_name'], Layer.GENERAL) ",
                ExpressionParsingContext.builder()
                                        .samples(Collections.singletonList("node_cpu_seconds_total"))
                                        .scopeType(ScopeType.SERVICE)
                                        .scopeLabels(Sets.newHashSet("node_identifier_host_name"))
                                        .aggregationLabels(Sets.newHashSet("node_identifier_host_name"))
                                        .downsampling(DownsamplingType.AVG)
                                        .isHistogram(false).build(),
                false,
                },
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void test(String name,
                     String expression,
                     ExpressionParsingContext want,
                     boolean isThrow) {
        Expression e = DSL.parse(name, expression);
        ExpressionParsingContext r = null;
        try {
            r = e.parse();
        } catch (Throwable t) {
            if (isThrow) {
                return;
            }
            log.error("Test failed", t);
            fail("Should not throw anything");
        }
        if (isThrow) {
            fail("Should throw something");
        }
        assertThat(r).isEqualTo(want);
    }
}
