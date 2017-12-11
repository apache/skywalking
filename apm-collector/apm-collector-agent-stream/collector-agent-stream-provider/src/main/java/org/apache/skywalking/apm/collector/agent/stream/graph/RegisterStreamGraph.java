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


package org.apache.skywalking.apm.collector.agent.stream.graph;

import org.apache.skywalking.apm.collector.agent.stream.service.graph.RegisterStreamGraphDefine;
import org.apache.skywalking.apm.collector.agent.stream.worker.register.ApplicationRegisterRemoteWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.register.ApplicationRegisterSerialWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.register.InstanceRegisterRemoteWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.register.InstanceRegisterSerialWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.register.ServiceNameRegisterRemoteWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.register.ServiceNameRegisterSerialWorker;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.queue.QueueModule;
import org.apache.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.apache.skywalking.apm.collector.remote.RemoteModule;
import org.apache.skywalking.apm.collector.remote.service.RemoteSenderService;
import org.apache.skywalking.apm.collector.storage.table.register.Application;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.apache.skywalking.apm.collector.stream.worker.base.WorkerCreateListener;

/**
 * @author peng-yongsheng
 */
public class RegisterStreamGraph {

    private final ModuleManager moduleManager;
    private final WorkerCreateListener workerCreateListener;

    public RegisterStreamGraph(ModuleManager moduleManager, WorkerCreateListener workerCreateListener) {
        this.moduleManager = moduleManager;
        this.workerCreateListener = workerCreateListener;
    }

    @SuppressWarnings("unchecked")
    public void createApplicationRegisterGraph() {
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        QueueCreatorService<Application> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<Application> graph = GraphManager.INSTANCE.createIfAbsent(RegisterStreamGraphDefine.APPLICATION_REGISTER_GRAPH_ID, Application.class);
        graph.addNode(new ApplicationRegisterRemoteWorker.Factory(moduleManager, remoteSenderService, RegisterStreamGraphDefine.APPLICATION_REGISTER_GRAPH_ID).create(workerCreateListener))
            .addNext(new ApplicationRegisterSerialWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
    }

    @SuppressWarnings("unchecked")
    public void createInstanceRegisterGraph() {
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        QueueCreatorService<Instance> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<Instance> graph = GraphManager.INSTANCE.createIfAbsent(RegisterStreamGraphDefine.INSTANCE_REGISTER_GRAPH_ID, Instance.class);
        graph.addNode(new InstanceRegisterRemoteWorker.Factory(moduleManager, remoteSenderService, RegisterStreamGraphDefine.INSTANCE_REGISTER_GRAPH_ID).create(workerCreateListener))
            .addNext(new InstanceRegisterSerialWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
    }

    @SuppressWarnings("unchecked")
    public void createServiceNameRegisterGraph() {
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        QueueCreatorService<ServiceName> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<ServiceName> graph = GraphManager.INSTANCE.createIfAbsent(RegisterStreamGraphDefine.SERVICE_NAME_REGISTER_GRAPH_ID, ServiceName.class);
        graph.addNode(new ServiceNameRegisterRemoteWorker.Factory(moduleManager, remoteSenderService, RegisterStreamGraphDefine.SERVICE_NAME_REGISTER_GRAPH_ID).create(workerCreateListener))
            .addNext(new ServiceNameRegisterSerialWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
    }
}
