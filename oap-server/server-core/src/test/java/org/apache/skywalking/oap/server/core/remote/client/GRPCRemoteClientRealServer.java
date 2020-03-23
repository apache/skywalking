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

package org.apache.skywalking.oap.server.core.remote.client;

import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.remote.RemoteServiceHandler;
import org.apache.skywalking.oap.server.library.server.ServerException;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCServer;
import org.apache.skywalking.oap.server.testing.module.ModuleDefineTesting;
import org.apache.skywalking.oap.server.testing.module.ModuleManagerTesting;

public class GRPCRemoteClientRealServer {

    public static void main(String[] args) throws ServerException, InterruptedException {
        ModuleManagerTesting moduleManager = new ModuleManagerTesting();
        ModuleDefineTesting moduleDefine = new ModuleDefineTesting();
        moduleManager.put(CoreModule.NAME, moduleDefine);

        GRPCServer server = new GRPCServer("localhost", 10000);
        server.initialize();

        server.addHandler(new RemoteServiceHandler(moduleManager));

        server.start();

        TimeUnit.MINUTES.sleep(10);
    }
}
