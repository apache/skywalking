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

package org.apache.skywalking.oap.server.core.alarm.provider.expression;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mvel2.CompileException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

public class ExpressionTest {
    private Expression expression;

    @Before
    public void init() {
        expression = new Expression(new ExpressionContext());
    }

    @Test
    public void testEval() {
        String expr = " a && b ";
        Map<String, Object> dataMap = new HashMap();
        dataMap.put("a", Boolean.TRUE);
        Object flag = expression.eval(expr, dataMap);
        assertNull(flag);
        dataMap.put("b", Boolean.TRUE);
        flag = expression.eval(expr, dataMap);
        assertThat(flag, is(true));
    }

    @Test
    public void testAnalysisInputs() {
        String expr = " a && b ";
        Set<String> inputs = expression.analysisInputs(expr);
        assertThat(inputs.size(), is(2));
        assertThat(inputs, is(Sets.newHashSet("a", "b")));
    }

    @Test
    public void testEvalWithEmptyContext() {
        String expr = " a && b ";
        Object flag = expression.eval(expr);
        assertNull(flag);
        flag = expression.eval(" 1 > 0");
        assertThat(flag, is(true));
    }

    @Test
    public void testCompile() {
        String expr = " a && b ";
        ExpressionContext context = new ExpressionContext();
        Object compiledExpression = expression.compile(expr, context);
        assertNotNull(compiledExpression);
        Object sameExpression = expression.compile(expr, context);
        assertThat(compiledExpression, is(sameExpression));
    }

    @Test(expected = CompileException.class)
    public void testCompileWithException() {
        String expr = " a && * b ";
        ExpressionContext context = new ExpressionContext();
        expression.compile(expr, context);
    }
}
