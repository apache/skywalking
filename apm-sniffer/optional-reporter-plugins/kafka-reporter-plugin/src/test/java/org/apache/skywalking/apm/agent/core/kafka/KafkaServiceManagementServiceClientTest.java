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

package org.apache.skywalking.apm.agent.core.kafka;

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.util.StringUtil;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class KafkaServiceManagementServiceClientTest {

    @Test
    public void testInstanceName() throws Exception {
        ServiceManager instance = ServiceManager.INSTANCE;
        Method loadAllServicesMethod = instance.getClass().getDeclaredMethod("loadAllServices");
        loadAllServicesMethod.setAccessible(true);
        Object loadAllServices = loadAllServicesMethod.invoke(instance);

        Field bootedServicesField = instance.getClass().getDeclaredField("bootedServices");
        bootedServicesField.setAccessible(true);
        bootedServicesField.set(instance, loadAllServices);

        Method prepare = instance.getClass().getDeclaredMethod("prepare");
        prepare.setAccessible(true);
        prepare.invoke(instance);

        assertThat(StringUtil.isNotBlank(Config.Agent.INSTANCE_NAME), is(true));
    }
}