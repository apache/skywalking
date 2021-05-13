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

package org.apache.skywalking.oap.server.receiver.sharing.server;

import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.JettyHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;

/**
 * Sharing server is an independent gRPC and Jetty servers provided for all receiver modules. In default, this module
 * would not be activated unless the user active explicitly. It only delegates the core gRPC and Jetty servers.
 *
 * Once it is activated, provides separated servers, then all receivers use these to accept outside requests. Typical,
 * this is activated to avoid the ip, port and thread pool sharing between receiver and internal traffics. For security
 * consideration, receiver should open TLS and token check, and internal(remote module) traffic should base on trusted
 * network, no TLS and token check. Even some companies may require TLS internally, it still use different TLS keys. In
 * this specific case, we recommend users to consider use {@link org.apache.skywalking.oap.server.core.CoreModuleConfig.Role}.
 */
public class SharingServerModule extends ModuleDefine {

    public static final String NAME = "receiver-sharing-server";

    public SharingServerModule() {
        super(NAME);
    }

    @Override
    public Class[] services() {
        return new Class[] {
            GRPCHandlerRegister.class,
            JettyHandlerRegister.class
        };
    }
}
