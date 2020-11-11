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

package org.apache.skywalking.oap.server.core.remote;

import java.util.List;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClient;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClientManager;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.remote.selector.ForeverFirstSelector;
import org.apache.skywalking.oap.server.core.remote.selector.HashCodeSelector;
import org.apache.skywalking.oap.server.core.remote.selector.RollingSelector;
import org.apache.skywalking.oap.server.core.remote.selector.Selector;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RemoteSenderService represents a gRPC client to send metrics from one OAP node to another through network. It
 * provides several routing mode to select target OAP node.
 */
public class RemoteSenderService implements Service {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteSenderService.class);

    private final ModuleManager moduleManager;
    private final HashCodeSelector hashCodeSelector;
    private final ForeverFirstSelector foreverFirstSelector;
    private final RollingSelector rollingSelector;

    public RemoteSenderService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.hashCodeSelector = new HashCodeSelector();
        this.foreverFirstSelector = new ForeverFirstSelector();
        this.rollingSelector = new RollingSelector();
    }

    /**
     * Send data to the target based on the given selector
     *
     * @param nextWorkName points to the worker to process the data when {@link RemoteServiceHandler} received.
     * @param streamData   data to be sent
     * @param selector     strategy implementation to choose suitable OAP node.
     */
    public void send(String nextWorkName, StreamData streamData, Selector selector) {
        RemoteClientManager clientManager = moduleManager.find(CoreModule.NAME)
                                                         .provider()
                                                         .getService(RemoteClientManager.class);
        RemoteClient remoteClient = null;

        List<RemoteClient> clientList = clientManager.getRemoteClient();
        if (clientList.size() == 0) {
            LOGGER.warn(
                "There is no available remote server for now, ignore the streaming data until the cluster metadata initialized.");
            return;
        }
        switch (selector) {
            case HashCode:
                remoteClient = hashCodeSelector.select(clientList, streamData);
                break;
            case Rolling:
                remoteClient = rollingSelector.select(clientList, streamData);
                break;
            case ForeverFirst:
                remoteClient = foreverFirstSelector.select(clientList, streamData);
                break;
        }
        remoteClient.push(nextWorkName, streamData);
    }
}
