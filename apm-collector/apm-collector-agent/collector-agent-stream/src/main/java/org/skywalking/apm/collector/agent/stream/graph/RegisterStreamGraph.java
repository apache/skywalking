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

package org.skywalking.apm.collector.agent.stream.graph;

import org.skywalking.apm.collector.agent.stream.worker.register.ApplicationRegisterRemoteWorker;
import org.skywalking.apm.collector.agent.stream.worker.register.ApplicationRegisterSerialWorker;
import org.skywalking.apm.collector.agent.stream.worker.register.InstanceRegisterRemoteWorker;
import org.skywalking.apm.collector.agent.stream.worker.register.InstanceRegisterSerialWorker;
import org.skywalking.apm.collector.agent.stream.worker.register.ServiceNameRegisterRemoteWorker;
import org.skywalking.apm.collector.agent.stream.worker.register.ServiceNameRegisterSerialWorker;
import org.skywalking.apm.collector.core.graph.Graph;
import org.skywalking.apm.collector.core.graph.GraphManager;
import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.collector.queue.QueueModule;
import org.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.skywalking.apm.collector.remote.RemoteModule;
import org.skywalking.apm.collector.remote.service.RemoteSenderService;
import org.skywalking.apm.collector.storage.StorageModule;
import org.skywalking.apm.collector.storage.service.DAOService;
import org.skywalking.apm.collector.storage.table.register.Application;
import org.skywalking.apm.collector.storage.table.register.Instance;
import org.skywalking.apm.collector.storage.table.register.ServiceName;
import org.skywalking.apm.collector.stream.worker.base.WorkerCreateListener;

/**
 * @author peng-yongsheng
 */
public class RegisterStreamGraph {

    public static final int APPLICATION_REGISTER_GRAPH_ID = 200;
    public static final int INSTANCE_REGISTER_GRAPH_ID = 201;
    public static final int SERVICE_NAME_REGISTER_GRAPH_ID = 202;

    private final ModuleManager moduleManager;
    private final WorkerCreateListener workerCreateListener;

    public RegisterStreamGraph(ModuleManager moduleManager, WorkerCreateListener workerCreateListener) {
        this.moduleManager = moduleManager;
        this.workerCreateListener = workerCreateListener;
    }

    @SuppressWarnings("unchecked")
    public Graph<Application> createApplicationRegisterGraph() {
        DAOService daoService = moduleManager.find(StorageModule.NAME).getService(DAOService.class);
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        QueueCreatorService<Application> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<Application> graph = GraphManager.INSTANCE.createIfAbsent(APPLICATION_REGISTER_GRAPH_ID, Application.class);
        graph.addNode(new ApplicationRegisterRemoteWorker.Factory(moduleManager, remoteSenderService, APPLICATION_REGISTER_GRAPH_ID).create(workerCreateListener))
            .addNext(new ApplicationRegisterSerialWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
        return graph;
    }

    @SuppressWarnings("unchecked")
    public Graph<Instance> createInstanceRegisterGraph() {
        DAOService daoService = moduleManager.find(StorageModule.NAME).getService(DAOService.class);
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        QueueCreatorService<Instance> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<Instance> graph = GraphManager.INSTANCE.createIfAbsent(INSTANCE_REGISTER_GRAPH_ID, Instance.class);
        graph.addNode(new InstanceRegisterRemoteWorker.Factory(moduleManager, remoteSenderService, INSTANCE_REGISTER_GRAPH_ID).create(workerCreateListener))
            .addNext(new InstanceRegisterSerialWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
        return graph;
    }

    @SuppressWarnings("unchecked")
    public Graph<ServiceName> createServiceNameRegisterGraph() {
        DAOService daoService = moduleManager.find(StorageModule.NAME).getService(DAOService.class);
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        QueueCreatorService<ServiceName> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<ServiceName> graph = GraphManager.INSTANCE.createIfAbsent(SERVICE_NAME_REGISTER_GRAPH_ID, ServiceName.class);
        graph.addNode(new ServiceNameRegisterRemoteWorker.Factory(moduleManager, remoteSenderService, SERVICE_NAME_REGISTER_GRAPH_ID).create(workerCreateListener))
            .addNext(new ServiceNameRegisterSerialWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
        return graph;
    }
}
