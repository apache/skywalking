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

package org.apache.skywalking.oal.rt.parser;

import java.util.*;
import lombok.*;

@Getter(AccessLevel.PUBLIC)
@Setter(AccessLevel.PUBLIC)
public class EntryMethod {
    private static final int LITERAL_TYPE = 1;
    private static final int EXPRESSION_TYPE = 2;

    private String methodName;
    private List<Integer> argTypes = new ArrayList<>();
    private List<Object> argsExpressions = new ArrayList<>();

    public void addArg(Class<?> parameterType, String expression) {
        if (parameterType.equals(int.class)) {
            expression = "(int)(" + expression + ")";
        } else if (parameterType.equals(long.class)) {
            expression = "(long)(" + expression + ")";
        } else if (parameterType.equals(double.class)) {
            expression = "(double)(" + expression + ")";
        } else if (parameterType.equals(float.class)) {
            expression = "(float)(" + expression + ")";
        }
        argTypes.add(LITERAL_TYPE);
        argsExpressions.add(expression);
    }

    public void addArg(Expression expression) {
        argTypes.add(EXPRESSION_TYPE);
        argsExpressions.add(expression);
    }
}
