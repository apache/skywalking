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
package org.apache.skywalking.apm.collector.ui.mutation;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * this class may not be implemented ,so just test if it's null
 * if update the class ,please update the testcase
 * @author lican
 */
public class ConfigMutationTest {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void setDataTTLConfigs() {
        ConfigMutation configMutation = new ConfigMutation();
        Boolean aBoolean = configMutation.setDataTTLConfigs(null);
        Assert.assertNull(aBoolean);
    }

    @Test
    public void setAlarmThreshold() {
        ConfigMutation configMutation = new ConfigMutation();
        Boolean aBoolean = configMutation.setAlarmThreshold(null);
        Assert.assertNull(aBoolean);
    }
}