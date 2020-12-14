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

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

@Slf4j
@RunWith(Parameterized.class)
public class ExpressionParsingTest {

    @Parameterized.Parameter
    public String name;

    @Parameterized.Parameter(1)
    public String expression;

    @Parameterized.Parameter(2)
    public ExpressionParsingContext want;

    @Parameterized.Parameter(3)
    public boolean isThrow;

    @Parameterized.Parameters(name = "{index}: {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
//            {
//                "mini",
//                "foo.instance(['service'], ['host'])",
//                ExpressionParsingContext.builder()
//                                        .downsampling(DownsamplingType.AVG)
//                                        .scopeLabels(Arrays.asList("service", "host"))
//                                        .scopeType(ScopeType.SERVICE_INSTANCE)
//                                        .aggregationLabels(Lists.newArrayList()).build(),
//                false,
//            },
            {
                "all",
                "latest (foo - 1).tagEqual('bar', '1').sum(['tt']).irate().histogram().histogram_percentile([50,99]).service(['rr'])",
                ExpressionParsingContext.builder()
                                        .samples(Collections.singletonList("foo"))
                                        .scopeType(ScopeType.SERVICE)
                                        .scopeLabels(Collections.singletonList("rr"))
                                        .aggregationLabels(Collections.singletonList("tt"))
                                        .downsampling(DownsamplingType.LATEST)
                                        .isHistogram(true)
                                        .percentiles(new int[]{50, 99}).build(),
                false,
            },
        });
    }

    @Test
    public void test() {
        Expression e = DSL.parse(expression);
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
        assertThat(r, is(want));
    }
}