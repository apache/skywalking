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

package org.apache.skywalking.oap.server.core.config.group;

import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

public class EndpointNameGroupingRuleWatcherTest {
    @Test
    public void testWatcher() throws FileNotFoundException {
        EndpointNameGrouping endpointNameGrouping = new EndpointNameGrouping();

        EndpointNameGroupingRuleWatcher watcher = new EndpointNameGroupingRuleWatcher(
            new ModuleProvider() {
                @Override
                public String name() {
                    return "test";
                }

                @Override
                public Class<? extends ModuleDefine> module() {
                    return CoreModule.class;
                }

                @Override
                public ConfigCreator newConfigCreator() {
                    return null;
                }

                @Override
                public void prepare() throws ServiceNotProvidedException, ModuleStartException {

                }

                @Override
                public void start() throws ServiceNotProvidedException, ModuleStartException {

                }

                @Override
                public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {

                }

                @Override
                public String[] requiredModules() {
                    return new String[0];
                }
            }, endpointNameGrouping);
        Assertions.assertEquals("/prod/{var}", endpointNameGrouping.format("serviceA", "/prod/123")._1());

        watcher.notify(new ConfigChangeWatcher.ConfigChangeEvent(
                "grouping:\n" +
                "  # Endpoint of the service would follow the following rules\n" +
                "  - service-name: serviceA\n" +
                "    rules:\n" +
                "      - /prod/{var}\n" +
                "      - /prod/{var}/info\n"
            , ConfigChangeWatcher.EventType.MODIFY
        ));

        Assertions.assertEquals("/prod/{var}/info", endpointNameGrouping.format("serviceA", "/prod/123/info")._1());

        watcher.notify(new ConfigChangeWatcher.ConfigChangeEvent("", ConfigChangeWatcher.EventType.DELETE));
        Assertions.assertEquals("/prod/123", endpointNameGrouping.format("serviceA", "/prod/123")._1());
    }
}
