/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agent.stream.worker;

import org.skywalking.apm.collector.remote.service.RemoteDataRegisterService;
import org.skywalking.apm.collector.storage.table.node.NodeComponent;
import org.skywalking.apm.collector.storage.table.node.NodeMapping;
import org.skywalking.apm.collector.storage.table.noderef.NodeReference;
import org.skywalking.apm.collector.storage.table.register.Application;
import org.skywalking.apm.collector.storage.table.register.Instance;
import org.skywalking.apm.collector.storage.table.register.ServiceName;
import org.skywalking.apm.collector.storage.table.service.ServiceEntry;
import org.skywalking.apm.collector.storage.table.serviceref.ServiceReference;

/**
 * @author peng-yongsheng
 */
public class AgentStreamRemoteDataRegister {

    private final RemoteDataRegisterService remoteDataRegisterService;

    public AgentStreamRemoteDataRegister(RemoteDataRegisterService remoteDataRegisterService) {
        this.remoteDataRegisterService = remoteDataRegisterService;
    }

    public void register() {
        remoteDataRegisterService.register(Application.class, Application::new);
        remoteDataRegisterService.register(Instance.class, Instance::new);
        remoteDataRegisterService.register(ServiceName.class, ServiceName::new);

        remoteDataRegisterService.register(NodeComponent.class, NodeComponent::new);
        remoteDataRegisterService.register(NodeMapping.class, NodeMapping::new);
        remoteDataRegisterService.register(NodeReference.class, NodeReference::new);
        remoteDataRegisterService.register(ServiceEntry.class, ServiceEntry::new);
        remoteDataRegisterService.register(ServiceReference.class, ServiceReference::new);
    }
}
