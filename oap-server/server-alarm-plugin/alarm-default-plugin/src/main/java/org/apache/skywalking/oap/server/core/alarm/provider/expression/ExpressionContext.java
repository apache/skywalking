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

import lombok.Getter;
import org.mvel2.ParserContext;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/***
 * Expression context can support custom function in expression,
 * for example `md5(a) == '111111'`, the md5 function add register in the context
 */
public class ExpressionContext {

    @Getter
    private ParserContext context;

    public ExpressionContext() {
        context = new ParserContext();
    }

    /**
     * Register a single method in the context
     */
    public void registerFunc(String func, Method method) {
        context.addImport(func, method);
    }

    /**
     * Register hole class public static methods in the context
     */
    public void registerFunc(Class<?> clz) {
        Method[] methods = clz.getDeclaredMethods();
        for (Method method : methods) {
            int mod = method.getModifiers();
            if (Modifier.isStatic(mod) && Modifier.isPublic(mod)) {
                registerFunc(method.getName(), method);
            }
        }
    }
}
