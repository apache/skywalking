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

package org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.plugin.elasticsearch.v6.TransportClientEnhanceInfo;
import org.elasticsearch.common.settings.Settings;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
@PrepareForTest(value = {
        Settings.class
})
public class TransportServiceConInterceptorTest {

    @Mock
    private Settings settings;

    private Object[] allArguments;

    private TransportServiceConInterceptor transportServiceConInterceptor;

    @Before
    public void setUp() {
        when(settings.get("cluster.name")).thenReturn("my.es.cluster");

        allArguments = new Object[]{settings};
    }

    @Test
    public void testConstruct() {

        final EnhancedInstance objInst = new EnhancedInstance() {
            private Object object = null;

            @Override
            public Object getSkyWalkingDynamicField() {
                return object;
            }

            @Override
            public void setSkyWalkingDynamicField(Object value) {
                this.object = value;
            }
        };

        transportServiceConInterceptor = new TransportServiceConInterceptor();
        transportServiceConInterceptor.onConstruct(objInst, allArguments);

        assertThat(objInst.getSkyWalkingDynamicField() instanceof TransportClientEnhanceInfo, is(true));
        assertThat(((TransportClientEnhanceInfo) (objInst.getSkyWalkingDynamicField())).getClusterName(), is("my.es.cluster"));
    }

}
