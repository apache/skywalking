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

package org.apache.skywalking.apm.plugin.play.v2x;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.plugin.play.v2x.define.Play2xInstrumentation;
import org.junit.Assert;
import org.junit.Test;
import play.api.http.EnabledFilters;

public class Play2xInstrumentationTest {

    @Test
    public void testConstructorMatch() throws Exception {
        final ElementMatcher<MethodDescription> matcher = Play2xInstrumentation.getInjectConstructorMatcher();
        final MethodDescription method = new MethodDescription.ForLoadedConstructor(EnabledFilters.class.getConstructor(play.api.Environment.class, play.api.Configuration.class, play.api.inject.Injector.class));
        Assert.assertTrue(matcher.matches(method));
    }

    @Test
    public void testMethodMatch() throws Exception {
        final ElementMatcher<MethodDescription> matcher = Play2xInstrumentation.getFiltersMethodMatcher();
        final MethodDescription method = new MethodDescription.ForLoadedMethod(EnabledFilters.class.getMethod("filters"));
        Assert.assertTrue(matcher.matches(method));
    }
}
