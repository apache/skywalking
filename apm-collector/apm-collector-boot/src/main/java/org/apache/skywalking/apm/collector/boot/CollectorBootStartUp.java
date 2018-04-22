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
import org.apache.skywalking.apm.collector.core.module.ModuleConfigException;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.module.ModuleNotFoundException;
import org.apache.skywalking.apm.collector.core.module.ModuleStartException;
import org.apache.skywalking.apm.collector.core.module.ProviderNotFoundException;
import org.apache.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class CollectorBootStartUp {

    public static void main(String[] args) {
        final Logger logger = LoggerFactory.getLogger(CollectorBootStartUp.class);

        ApplicationConfigLoader configLoader = new ApplicationConfigLoader();
        ModuleManager manager = new ModuleManager();
        try {
            ApplicationConfiguration applicationConfiguration = configLoader.load();
            manager.init(applicationConfiguration);
        } catch (ConfigFileNotFoundException | ModuleNotFoundException | ProviderNotFoundException | ServiceNotProvidedException | ModuleConfigException | ModuleStartException e) {
            logger.error(e.getMessage(), e);
            System.exit(1);
        }
    }
}
