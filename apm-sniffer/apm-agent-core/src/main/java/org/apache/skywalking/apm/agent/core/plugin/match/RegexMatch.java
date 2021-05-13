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

package org.apache.skywalking.apm.agent.core.plugin.match;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.nameMatches;

/**
 * Match the class by given class name regex expression.
 */
public class RegexMatch implements IndirectMatch {
    private String[] regexExpressions;

    private RegexMatch(String... regexExpressions) {
        if (regexExpressions == null || regexExpressions.length == 0) {
            throw new IllegalArgumentException("annotations is null");
        }
        this.regexExpressions = regexExpressions;
    }

    @Override
    public ElementMatcher.Junction buildJunction() {
        ElementMatcher.Junction regexJunction = null;
        for (String regexExpression : regexExpressions) {
            if (regexJunction == null) {
                regexJunction = nameMatches(regexExpression);
            } else {
                regexJunction = regexJunction.or(nameMatches(regexExpression));
            }
        }
        return regexJunction;
    }

    @Override
    public boolean isMatch(TypeDescription typeDescription) {
        boolean isMatch = false;
        for (String matchExpression : regexExpressions) {
            isMatch = typeDescription.getTypeName().matches(matchExpression);
            if (isMatch) {
                break;
            }
        }
        return isMatch;
    }

    public static RegexMatch byRegexMatch(String... regexExpressions) {
        return new RegexMatch(regexExpressions);
    }
}