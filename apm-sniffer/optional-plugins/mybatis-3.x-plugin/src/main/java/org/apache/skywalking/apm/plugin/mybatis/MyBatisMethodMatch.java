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

package org.apache.skywalking.apm.plugin.mybatis;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public enum MyBatisMethodMatch {
    INSTANCE;

    public ElementMatcher<MethodDescription> getMyBatisMethodMatcher() {
        return named("select").and(takesArguments(4))
                              .or(named("selectList").and(takesArguments(3)))
                              .or(named("update").and(takesArguments(2)));
    }

    public ElementMatcher<MethodDescription> getMyBatisShellMethodMatcher() {
        return named("selectOne").or(named("selectMap"))
                                 .or(named("insert"))
                                 .or(named("delete"))
                                 .or(named("select").and(takesArguments(2)))
                                 .or(named("select").and(takesArguments(3)))
                                 .or(named("selectList").and(takesArguments(1)))
                                 .or(named("selectList").and(takesArguments(2)))
                                 .or(named("update").and(takesArguments(1)));
    }
}
