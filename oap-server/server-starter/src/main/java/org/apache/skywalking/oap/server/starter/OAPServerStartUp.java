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

package org.apache.skywalking.oap.server.starter;

import org.apache.skywalking.oap.server.library.module.*;
import org.apache.skywalking.oap.server.starter.config.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class OAPServerStartUp {

    private static final Logger logger = LoggerFactory.getLogger(OAPServerStartUp.class);

    public static void main(String[] args) {
        ApplicationConfigLoader configLoader = new ApplicationConfigLoader();
        ModuleManager manager = new ModuleManager();
        try {
            ApplicationConfiguration applicationConfiguration = configLoader.load();
            manager.init(applicationConfiguration);

            String mode = System.getProperty("mode");
            if ("init".equals(mode)) {
                logger.info("OAP starts up in init mode successfully, exit now...");
                System.exit(0);
            }
        } catch (ConfigFileNotFoundException | ModuleNotFoundException | ProviderNotFoundException | ServiceNotProvidedException | ModuleConfigException | ModuleStartException e) {
            logger.error(e.getMessage(), e);
            System.exit(1);
        }
    }
}
