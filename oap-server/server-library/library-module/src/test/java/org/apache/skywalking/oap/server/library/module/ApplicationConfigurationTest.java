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

package org.apache.skywalking.oap.server.library.module;

import java.util.Properties;
import org.junit.Assert;
import org.junit.Test;

public class ApplicationConfigurationTest {
    @Test
    public void testBuildConfig() {
        ApplicationConfiguration configuration = new ApplicationConfiguration();
        Properties p1 = new Properties();
        p1.setProperty("p1", "value1");
        p1.setProperty("p2", "value2");
        Properties p2 = new Properties();
        p2.setProperty("prop1", "value1-prop");
        p2.setProperty("prop2", "value2-prop");
        configuration.addModule("MO-1").addProviderConfiguration("MO-1-P1", p1).addProviderConfiguration("MO-1-P2", p2);

        Assert.assertArrayEquals(new String[] {"MO-1"}, configuration.moduleList());
        Assert.assertEquals("value2-prop", configuration.getModuleConfiguration("MO-1")
                                                        .getProviderConfiguration("MO-1-P2")
                                                        .getProperty("prop2"));
        Assert.assertEquals(p1, configuration.getModuleConfiguration("MO-1").getProviderConfiguration("MO-1-P1"));
    }
}
