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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.instance.mapping;

import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricGraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerCreateListener;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.core.graph.Node;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.remote.RemoteModule;
import org.apache.skywalking.apm.collector.remote.service.RemoteSenderService;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMapping;

/**
 * @author peng-yongsheng
 */
public class InstanceMappingGraph {

    private final ModuleManager moduleManager;
    private final WorkerCreateListener workerCreateListener;

    public InstanceMappingGraph(ModuleManager moduleManager, WorkerCreateListener workerCreateListener) {
        this.moduleManager = moduleManager;
        this.workerCreateListener = workerCreateListener;
    }

    public void create() {
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        Node<InstanceMapping, InstanceMapping> remoteNode = GraphManager.INSTANCE.createIfAbsent(MetricGraphIdDefine.INSTANCE_MAPPING_GRAPH_ID, InstanceMapping.class)
            .addNode(new InstanceMappingMinuteAggregationWorker.Factory(moduleManager).create(workerCreateListener))
            .addNext(new InstanceMappingMinuteRemoteWorker.Factory(moduleManager, remoteSenderService, MetricGraphIdDefine.INSTANCE_MAPPING_GRAPH_ID).create(workerCreateListener));

        remoteNode.addNext(new InstanceMappingMinutePersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        remoteNode.addNext(new InstanceMappingHourTransformNode())
            .addNext(new InstanceMappingHourPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        remoteNode.addNext(new InstanceMappingDayTransformNode())
            .addNext(new InstanceMappingDayPersistenceWorker.Factory(moduleManager).create(workerCreateListener));

        remoteNode.addNext(new InstanceMappingMonthTransformNode())
            .addNext(new InstanceMappingMonthPersistenceWorker.Factory(moduleManager).create(workerCreateListener));
    }
}
