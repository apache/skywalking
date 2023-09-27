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

package org.apache.skywalking.oap.server.receiver.envoy.graal;

import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.receiver.envoy.EnvoyMetricReceiverProvider;

/**
 * change the execution of `prepare()` to the compilation stage due to the Graal limitation on Reflection
 * see (@link org.apache.skywalking.graal.EnvoyGeneratorMojo).
 */
public class EnvoyMetricReceiverProviderGraal extends EnvoyMetricReceiverProvider {
    @Override
    public String name() {
        return "graalvm";
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {

    }
}
