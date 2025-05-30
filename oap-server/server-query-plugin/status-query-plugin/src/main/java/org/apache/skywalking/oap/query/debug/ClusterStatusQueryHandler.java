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

package org.apache.skywalking.oap.query.debug;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.server.annotation.ExceptionHandler;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.ProducesJson;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClient;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClientManager;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

@Slf4j
@ExceptionHandler(StatusQueryExceptionHandler.class)
public class ClusterStatusQueryHandler {
    private final ModuleManager moduleManager;
    private RemoteClientManager remoteClientManager;

    public ClusterStatusQueryHandler(final ModuleManager manager) {
        this.moduleManager = manager;
    }

    private RemoteClientManager getRemoteClientManager() {
        if (remoteClientManager == null) {
            remoteClientManager = moduleManager.find(CoreModule.NAME)
                                               .provider()
                                               .getService(RemoteClientManager.class);
        }
        return remoteClientManager;
    }

    @ProducesJson
    @Get("/status/cluster/nodes")
    public Map<String, ?> buildClusterNodeList(HttpRequest request) {
        return Map.of(
            "nodes", 
            getRemoteClientManager().getRemoteClient()
                                    .stream()
                                    .map(RemoteClient::getAddress)
                                    .collect(Collectors.toList())
        );
    }
}
