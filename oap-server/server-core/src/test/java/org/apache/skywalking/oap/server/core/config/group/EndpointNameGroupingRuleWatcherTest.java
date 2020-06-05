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

import java.io.FileNotFoundException;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.junit.Assert;
import org.junit.Test;

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
                public ModuleConfig createConfigBeanIfAbsent() {
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
        Assert.assertEquals("/prod/{id}", endpointNameGrouping.format("serviceA", "/prod/123"));

        watcher.notify(new ConfigChangeWatcher.ConfigChangeEvent(
            "# Licensed to the Apache Software Foundation (ASF) under one or more\n" +
                "# contributor license agreements.  See the NOTICE file distributed with\n" +
                "# this work for additional information regarding copyright ownership.\n" +
                "# The ASF licenses this file to You under the Apache License, Version 2.0\n" +
                "# (the \"License\"); you may not use this file except in compliance with\n" +
                "# the License.  You may obtain a copy of the License at\n" +
                "#\n" +
                "#     http://www.apache.org/licenses/LICENSE-2.0\n" +
                "#\n" +
                "# Unless required by applicable law or agreed to in writing, software\n" +
                "# distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                "# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                "# See the License for the specific language governing permissions and\n" +
                "# limitations under the License.\n" +
                "\n" +
                "# Endpoint name grouping rules.\n" +
                "# In most cased, endpoint name should be detected by agents or service mesh automatically, and aggregate the metrics based\n" +
                "# on the name.\n" +
                "# But, in some cases, application put the parameter in the endpoint name, such as putting order id in the URI, like\n" +
                "# /prod/ORDER123, /prod/ORDER123.\n" +
                "# This grouping file provides the regex based definition capability to merge those endpoints into a group by better and\n" +
                "# more meaningful aggregation metrics.\n" +
                "\n" +
                "grouping:\n" +
                "  # Endpoint of the service would follow the following rules\n" +
                "  - service-name: serviceA\n" +
                "    rules:\n" +
                "      - endpoint-name: /prod/order-id\n" +
                "        regex: \\/prod\\/.+"
            , ConfigChangeWatcher.EventType.MODIFY
        ));

        Assert.assertEquals("/prod/order-id", endpointNameGrouping.format("serviceA", "/prod/123"));

        watcher.notify(new ConfigChangeWatcher.ConfigChangeEvent("", ConfigChangeWatcher.EventType.DELETE));
        Assert.assertEquals("/prod/123", endpointNameGrouping.format("serviceA", "/prod/123"));
    }
}
