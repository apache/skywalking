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

package org.apache.skywalking.apm.agent.core.plugin.witness;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.skywalking.apm.agent.core.plugin.WitnessFinder;
import org.apache.skywalking.apm.agent.core.plugin.WitnessMethod;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

/**
 * unit test for WitnessFinder
 */
public class WitnessTest {

    private final String className = "org.apache.skywalking.apm.agent.core.plugin.witness.WitnessTest";

    private final WitnessFinder finder = WitnessFinder.INSTANCE;

    @Test
    public void testWitnessClass() {
        Assert.assertTrue(finder.exist(className, this.getClass().getClassLoader()));
    }

    @Test
    public void testWitnessMethod() {
        ElementMatcher.Junction<MethodDescription> junction = ElementMatchers.named("foo")
                .and(ElementMatchers.returnsGeneric(target -> "java.util.List<java.util.Map<java.lang.String, java.lang.Object>>".equals(target.getTypeName())))
                .and(ElementMatchers.takesGenericArgument(0, target -> "java.util.List<java.util.Map<java.lang.String, java.lang.Object>>".equals(target.getTypeName())))
                .and(ElementMatchers.takesArgument(1, target -> "java.lang.String".equals(target.getName())));
        WitnessMethod witnessMethod = new WitnessMethod(className, junction);
        Assert.assertTrue(finder.exist(witnessMethod, this.getClass().getClassLoader()));
    }

    @Test
    public void testWitnessMethodOnlyUsingName() {
        ElementMatcher.Junction<MethodDescription> junction = ElementMatchers.named("foo");
        WitnessMethod witnessMethod = new WitnessMethod(className, junction);
        Assert.assertTrue(finder.exist(witnessMethod, this.getClass().getClassLoader()));
    }

    public List<Map<String, Object>> foo(List<Map<String, Object>> param, String s) {
        return null;
    }

}
