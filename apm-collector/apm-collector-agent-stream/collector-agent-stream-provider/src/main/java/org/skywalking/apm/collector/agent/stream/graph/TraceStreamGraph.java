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

import org.skywalking.apm.collector.agent.stream.parser.standardization.SegmentStandardization;
import org.skywalking.apm.collector.agent.stream.parser.standardization.SegmentStandardizationWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.application.ApplicationReferenceMetricAggregationWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.application.ApplicationReferenceMetricPersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.application.ApplicationReferenceMetricRemoteWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.global.GlobalTracePersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.instance.InstanceMetricPersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.application.ApplicationComponentAggregationWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.application.ApplicationComponentPersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.application.ApplicationComponentRemoteWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.application.ApplicationMappingAggregationWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.application.ApplicationMappingPersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.application.ApplicationMappingRemoteWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.segment.SegmentCostPersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.segment.SegmentPersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.service.ServiceEntryAggregationWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.service.ServiceEntryPersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.service.ServiceEntryRemoteWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.service.ServiceReferenceMetricAggregationWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.service.ServiceReferenceMetricPersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.service.ServiceReferenceMetricRemoteWorker;
import org.skywalking.apm.collector.core.graph.Graph;
import org.skywalking.apm.collector.core.graph.GraphManager;
import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.collector.queue.QueueModule;
import org.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.skywalking.apm.collector.remote.RemoteModule;
import org.skywalking.apm.collector.remote.service.RemoteSenderService;
import org.skywalking.apm.collector.storage.table.global.GlobalTrace;
import org.skywalking.apm.collector.storage.table.instance.InstanceMetric;
import org.skywalking.apm.collector.storage.table.node.ApplicationComponent;
import org.skywalking.apm.collector.storage.table.node.ApplicationMapping;
import org.skywalking.apm.collector.storage.table.noderef.ApplicationReferenceMetric;
import org.skywalking.apm.collector.storage.table.segment.Segment;
import org.skywalking.apm.collector.storage.table.segment.SegmentCost;
import org.skywalking.apm.collector.storage.table.service.ServiceEntry;
import org.skywalking.apm.collector.storage.table.serviceref.ServiceReferenceMetric;
import org.skywalking.apm.collector.stream.worker.base.WorkerCreateListener;

/**
 * @author peng-yongsheng
 */
public class TraceStreamGraph {

    public static final int GLOBAL_TRACE_GRAPH_ID = 300;
    public static final int INSTANCE_METRIC_GRAPH_ID = 301;
    public static final int NODE_COMPONENT_GRAPH_ID = 302;
    public static final int NODE_MAPPING_GRAPH_ID = 303;
    public static final int APPLICATION_REFERENCE_METRIC_GRAPH_ID = 304;
    public static final int SERVICE_ENTRY_GRAPH_ID = 305;
    public static final int SERVICE_REFERENCE_GRAPH_ID = 306;
    public static final int SEGMENT_GRAPH_ID = 307;
    public static final int SEGMENT_COST_GRAPH_ID = 308;
    public static final int SEGMENT_STANDARDIZATION_GRAPH_ID = 309;

    private final ModuleManager moduleManager;
    private final WorkerCreateListener workerCreateListener;

    public TraceStreamGraph(ModuleManager moduleManager, WorkerCreateListener workerCreateListener) {
        this.moduleManager = moduleManager;
        this.workerCreateListener = workerCreateListener;
    }

    @SuppressWarnings("unchecked")
    public void createSegmentStandardizationGraph() {
        QueueCreatorService<SegmentStandardization> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<SegmentStandardization> graph = GraphManager.INSTANCE.createIfAbsent(SEGMENT_STANDARDIZATION_GRAPH_ID, SegmentStandardization.class);
        graph.addNode(new SegmentStandardizationWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
    }

    @SuppressWarnings("unchecked")
    public void createGlobalTraceGraph() {
        QueueCreatorService<GlobalTrace> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<GlobalTrace> graph = GraphManager.INSTANCE.createIfAbsent(GLOBAL_TRACE_GRAPH_ID, GlobalTrace.class);
        graph.addNode(new GlobalTracePersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
    }

    @SuppressWarnings("unchecked")
    public void createInstanceMetricGraph() {
        QueueCreatorService<InstanceMetric> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<InstanceMetric> graph = GraphManager.INSTANCE.createIfAbsent(INSTANCE_METRIC_GRAPH_ID, InstanceMetric.class);
        graph.addNode(new InstanceMetricPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
    }

    @SuppressWarnings("unchecked")
    public void createNodeComponentGraph() {
        QueueCreatorService<ApplicationComponent> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        Graph<ApplicationComponent> graph = GraphManager.INSTANCE.createIfAbsent(NODE_COMPONENT_GRAPH_ID, ApplicationComponent.class);
        graph.addNode(new ApplicationComponentAggregationWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener))
            .addNext(new ApplicationComponentRemoteWorker.Factory(moduleManager, remoteSenderService, NODE_COMPONENT_GRAPH_ID).create(workerCreateListener))
            .addNext(new ApplicationComponentPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
    }

    @SuppressWarnings("unchecked")
    public void createNodeMappingGraph() {
        QueueCreatorService<ApplicationMapping> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        Graph<ApplicationMapping> graph = GraphManager.INSTANCE.createIfAbsent(NODE_MAPPING_GRAPH_ID, ApplicationMapping.class);
        graph.addNode(new ApplicationMappingAggregationWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener))
            .addNext(new ApplicationMappingRemoteWorker.Factory(moduleManager, remoteSenderService, NODE_MAPPING_GRAPH_ID).create(workerCreateListener))
            .addNext(new ApplicationMappingPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
    }

    @SuppressWarnings("unchecked")
    public void createApplicationReferenceMetricGraph() {
        QueueCreatorService<ApplicationReferenceMetric> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        Graph<ApplicationReferenceMetric> graph = GraphManager.INSTANCE.createIfAbsent(APPLICATION_REFERENCE_METRIC_GRAPH_ID, ApplicationReferenceMetric.class);
        graph.addNode(new ApplicationReferenceMetricAggregationWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener))
            .addNext(new ApplicationReferenceMetricRemoteWorker.Factory(moduleManager, remoteSenderService, APPLICATION_REFERENCE_METRIC_GRAPH_ID).create(workerCreateListener))
            .addNext(new ApplicationReferenceMetricPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
    }

    @SuppressWarnings("unchecked")
    public void createServiceEntryGraph() {
        QueueCreatorService<ServiceEntry> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        Graph<ServiceEntry> graph = GraphManager.INSTANCE.createIfAbsent(SERVICE_ENTRY_GRAPH_ID, ServiceEntry.class);
        graph.addNode(new ServiceEntryAggregationWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener))
            .addNext(new ServiceEntryRemoteWorker.Factory(moduleManager, remoteSenderService, SERVICE_ENTRY_GRAPH_ID).create(workerCreateListener))
            .addNext(new ServiceEntryPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
    }

    @SuppressWarnings("unchecked")
    public void createServiceReferenceGraph() {
        QueueCreatorService<ServiceReferenceMetric> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        Graph<ServiceReferenceMetric> graph = GraphManager.INSTANCE.createIfAbsent(SERVICE_REFERENCE_GRAPH_ID, ServiceReferenceMetric.class);
        graph.addNode(new ServiceReferenceMetricAggregationWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener))
            .addNext(new ServiceReferenceMetricRemoteWorker.Factory(moduleManager, remoteSenderService, SERVICE_REFERENCE_GRAPH_ID).create(workerCreateListener))
            .addNext(new ServiceReferenceMetricPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
    }

    @SuppressWarnings("unchecked")
    public void createSegmentGraph() {
        QueueCreatorService<Segment> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<Segment> graph = GraphManager.INSTANCE.createIfAbsent(SEGMENT_GRAPH_ID, Segment.class);
        graph.addNode(new SegmentPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
    }

    @SuppressWarnings("unchecked")
    public void createSegmentCostGraph() {
        QueueCreatorService<SegmentCost> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<SegmentCost> graph = GraphManager.INSTANCE.createIfAbsent(SEGMENT_COST_GRAPH_ID, SegmentCost.class);
        graph.addNode(new SegmentCostPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
    }
}
