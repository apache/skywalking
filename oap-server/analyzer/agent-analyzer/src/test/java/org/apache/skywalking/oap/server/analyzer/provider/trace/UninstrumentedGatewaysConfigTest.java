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

package org.apache.skywalking.oap.server.analyzer.provider.trace;

import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.junit.Assert;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

public class UninstrumentedGatewaysConfigTest {
    @Test
    public void testParseGatewayYAML() throws Exception {
        final UninstrumentedGatewaysConfig uninstrumentedGatewaysConfig
            = new UninstrumentedGatewaysConfig(new MockProvider());
        UninstrumentedGatewaysConfig.GatewayInfos gatewayInfos
            = Whitebox.invokeMethod(uninstrumentedGatewaysConfig, "parseGatewaysFromFile", "gateways.yml");
        Assert.assertEquals(1, gatewayInfos.getGateways().size());
    }

    private static class MockProvider extends ModuleProvider {

        @Override
        public String name() {
            return null;
        }

        @Override
        public Class<? extends ModuleDefine> module() {
            return null;
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
    }
}
