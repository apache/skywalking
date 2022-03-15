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

package org.apache.skywalking.oap.server.receiver.register.provider.handler.v8.rest;

import com.linecorp.armeria.server.annotation.Post;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.management.v3.InstancePingPkg;
import org.apache.skywalking.apm.network.management.v3.InstanceProperties;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.register.provider.handler.v8.ManagementServiceHandler;

public class ManagementServiceHTTPHandler {
    private final ManagementServiceHandler handler;

    public ManagementServiceHTTPHandler(ModuleManager moduleManager) {
        handler = new ManagementServiceHandler(moduleManager);
    }

    @Post("/v3/management/keepAlive")
    public Commands keepAlive(final InstancePingPkg request) {
        return handler.keepAlive(request);
    }

    @Post("/v3/management/reportProperties")
    public Commands reportProperties(final InstanceProperties request) {
        return handler.reportInstanceProperties(request);
    }
}
