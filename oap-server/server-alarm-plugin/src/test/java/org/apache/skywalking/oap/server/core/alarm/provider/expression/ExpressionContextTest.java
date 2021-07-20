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

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class ExpressionContextTest {

    @Test
    public void testRegisterFuncWithMethod() throws NoSuchMethodException {
        ExpressionContext expressionContext = new ExpressionContext();
        Method[] methods = Math.class.getMethods();
        Arrays.stream(methods).forEach(method -> {
            if (method.getName().equalsIgnoreCase("sqrt")) {
                expressionContext.registerFunc("sqrt", method);
            }
        });
        Expression expression = new Expression(expressionContext);
        Number number = (Number) expression.eval("sqrt(16)");
        assertThat(number, is(4.0));
    }

    @Test
    public void testRegisterFuncWithClazz() throws NoSuchMethodException {
        ExpressionContext expressionContext = new ExpressionContext();
        expressionContext.registerFunc(Math.class);
        Expression expression = new Expression(expressionContext);
        Number number = (Number) expression.eval("sqrt(16)");
        assertThat(number, is(4.0));
        number = (Number) expression.eval("abs(-12)");
        assertThat(number, is(12));
    }
}
