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

package org.apache.skywalking.apm.collector.boot;

import org.apache.skywalking.apm.collector.boot.config.ApplicationConfigLoader;
import org.apache.skywalking.apm.collector.boot.config.ConfigFileNotFoundException;
import org.apache.skywalking.apm.collector.core.module.ApplicationConfiguration;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author wu-sheng
 */
public class ApplicationConfigLoaderTest {
    @Test
    public void loadTest() throws ConfigFileNotFoundException {
        System.setProperty("naming.jetty.host", "5000");
        ApplicationConfigLoader loader = new ApplicationConfigLoader();
        ApplicationConfiguration applicationConfiguration = loader.load();

        Assert.assertEquals(applicationConfiguration.getModuleConfiguration("naming").getProviderConfiguration("jetty").getProperty("host"), "5000");
        Assert.assertEquals(applicationConfiguration.getModuleConfiguration("naming").getProviderConfiguration("jetty").getProperty("port"), "10800");
    }
}
