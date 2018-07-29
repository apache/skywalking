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

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.analysis.worker.define.WorkerMapper;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * @author peng-yongsheng
 */
public class SelfRemoteClient implements RemoteClient {

    private final ModuleManager moduleManager;
    private final String host;
    private final int port;

    public SelfRemoteClient(ModuleManager moduleManager, String host, int port) {
        this.moduleManager = moduleManager;
        this.host = host;
        this.port = port;
    }

    @Override public String getHost() {
        return host;
    }

    @Override public int getPort() {
        return port;
    }

    @Override public void push(int nextWorkerId, Indicator indicator) {
        WorkerMapper workerMapper = moduleManager.find(CoreModule.NAME).getService(WorkerMapper.class);
        workerMapper.findInstanceById(nextWorkerId).in(indicator);
    }
}
