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

package org.apache.skywalking.apm.agent.core.plugin.bytebuddy;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Return Type match. Similar with {@link net.bytebuddy.matcher.ElementMatchers#returns}, the only different between
 * them is this match use {@link String} to declare the type, instead of {@link Class}. This can avoid the classloader
 * risk.
 * <p>
 * 2019-08-15
 */
public class ReturnTypeNameMatch implements ElementMatcher<MethodDescription> {

    /**
     * the target return type
     */
    private String returnTypeName;

    /**
     * declare the match target method with the certain type.
     *
     * @param returnTypeName target return type
     */
    private ReturnTypeNameMatch(String returnTypeName) {
        this.returnTypeName = returnTypeName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(MethodDescription target) {
        return target.getReturnType().asErasure().getName().equals(returnTypeName);
    }

    /**
     * The static method to create {@link ReturnTypeNameMatch} This is a delegate method to follow byte-buddy {@link
     * ElementMatcher}'s code style.
     *
     * @param returnTypeName target return type
     * @return new {@link ReturnTypeNameMatch} instance.
     */
    public static ElementMatcher<MethodDescription> returnsWithType(String returnTypeName) {
        return new ReturnTypeNameMatch(returnTypeName);
    }
}
