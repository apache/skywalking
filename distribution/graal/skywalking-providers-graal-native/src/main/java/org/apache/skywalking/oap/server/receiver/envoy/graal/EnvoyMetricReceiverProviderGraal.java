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
 * In the old logic, class (@link org.apache.skywalking.oap.server.receiver.envoy.als.mxFieldsHelper) was used, which is not supported by native-image,
 * so we change the execution of `prepare()` to the compilation stage, see (@link org.apache.skywalking.graal.Generator).
 */
public class EnvoyMetricReceiverProviderGraal extends EnvoyMetricReceiverProvider {
    @Override
    public String name() {
        return "default-graalvm";
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {

    }
}
