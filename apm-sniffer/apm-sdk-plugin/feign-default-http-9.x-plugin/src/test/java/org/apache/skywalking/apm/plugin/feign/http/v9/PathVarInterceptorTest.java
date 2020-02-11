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

package org.apache.skywalking.apm.plugin.feign.http.v9;

import feign.RequestTemplate;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(PowerMockRunner.class)
public class PathVarInterceptorTest {

    private PathVarInterceptor pathVarInterceptor;

    @Mock
    private EnhancedInstance enhancedInstance;

    @Mock
    private MethodInterceptResult result;

    private Object[] allArguments;
    private Class[] argumentTypes;

    private RequestTemplate resolvedTemplate;

    @Before
    public void setUp() {

        RequestTemplate template = new RequestTemplate();
        template.append("http://skywalking.org/{pathVar}");

        resolvedTemplate = new RequestTemplate();
        resolvedTemplate.append("http://skywalking.org/value");

        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("pathVar", "value");
        allArguments = new Object[] {
            new Object[] {},
            template,
            variables
        };
        argumentTypes = new Class[] {
            Object[].class,
            RequestTemplate.class,
            Map.class
        };
        pathVarInterceptor = new PathVarInterceptor();

    }

    @Test
    public void testMethodsAround() throws Throwable {
        pathVarInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, result);
        pathVarInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, resolvedTemplate);
        assertThat(PathVarInterceptor.URL_CONTEXT.get().getOriginUrl(), is("http://skywalking.org/{pathVar}"));
        assertThat(PathVarInterceptor.URL_CONTEXT.get().getUrl(), is("http://skywalking.org/value"));
    }

    @After
    public void clean() {
        PathVarInterceptor.URL_CONTEXT.remove();
    }
}
