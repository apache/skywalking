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

import org.skywalking.apm.collector.agent.stream.parser.standardization.SegmentStandardizationWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.global.GlobalTracePersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.instance.InstPerformancePersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.node.NodeComponentAggregationWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.node.NodeComponentPersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.node.NodeComponentRemoteWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.node.NodeMappingAggregationWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.node.NodeMappingPersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.node.NodeMappingRemoteWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.noderef.NodeReferenceAggregationWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.noderef.NodeReferencePersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.noderef.NodeReferenceRemoteWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.segment.SegmentCostPersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.segment.SegmentPersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.service.ServiceEntryAggregationWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.service.ServiceEntryPersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.service.ServiceEntryRemoteWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.serviceref.ServiceReferenceAggregationWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.serviceref.ServiceReferencePersistenceWorker;
import org.skywalking.apm.collector.agent.stream.worker.trace.serviceref.ServiceReferenceRemoteWorker;
import org.skywalking.apm.collector.core.graph.Graph;
import org.skywalking.apm.collector.core.graph.GraphManager;
import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.collector.queue.QueueModule;
import org.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.skywalking.apm.collector.remote.RemoteModule;
import org.skywalking.apm.collector.remote.service.RemoteSenderService;
import org.skywalking.apm.collector.storage.table.global.GlobalTrace;
import org.skywalking.apm.collector.storage.table.instance.InstPerformance;
import org.skywalking.apm.collector.storage.table.node.NodeComponent;
import org.skywalking.apm.collector.storage.table.node.NodeMapping;
import org.skywalking.apm.collector.storage.table.noderef.NodeReference;
import org.skywalking.apm.collector.storage.table.segment.Segment;
import org.skywalking.apm.collector.storage.table.segment.SegmentCost;
import org.skywalking.apm.collector.storage.table.service.ServiceEntry;
import org.skywalking.apm.collector.storage.table.serviceref.ServiceReference;
import org.skywalking.apm.collector.stream.worker.base.WorkerCreateListener;
import org.skywalking.apm.network.proto.UpstreamSegment;

/**
 * @author peng-yongsheng
 */
public class TraceStreamGraph {

    public static final int GLOBAL_TRACE_GRAPH_ID = 300;
    public static final int INST_PERFORMANCE_GRAPH_ID = 301;
    public static final int NODE_COMPONENT_GRAPH_ID = 302;
    public static final int NODE_MAPPING_GRAPH_ID = 303;
    public static final int NODE_REFERENCE_GRAPH_ID = 304;
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
    public Graph<UpstreamSegment> createSegmentStandardizationGraph() {
        QueueCreatorService<UpstreamSegment> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<UpstreamSegment> graph = GraphManager.INSTANCE.createIfAbsent(SEGMENT_STANDARDIZATION_GRAPH_ID, UpstreamSegment.class);
        graph.addNode(new SegmentStandardizationWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
        return graph;
    }

    @SuppressWarnings("unchecked")
    public Graph<GlobalTrace> createGlobalTraceGraph() {
        QueueCreatorService<GlobalTrace> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<GlobalTrace> graph = GraphManager.INSTANCE.createIfAbsent(GLOBAL_TRACE_GRAPH_ID, GlobalTrace.class);
        graph.addNode(new GlobalTracePersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
        return graph;
    }

    @SuppressWarnings("unchecked")
    public Graph<InstPerformance> createInstPerformanceGraph() {
        QueueCreatorService<InstPerformance> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<InstPerformance> graph = GraphManager.INSTANCE.createIfAbsent(INST_PERFORMANCE_GRAPH_ID, InstPerformance.class);
        graph.addNode(new InstPerformancePersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
        return graph;
    }

    @SuppressWarnings("unchecked")
    public Graph<NodeComponent> createNodeComponentGraph() {
        QueueCreatorService<NodeComponent> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        Graph<NodeComponent> graph = GraphManager.INSTANCE.createIfAbsent(NODE_COMPONENT_GRAPH_ID, NodeComponent.class);
        graph.addNode(new NodeComponentAggregationWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener))
            .addNext(new NodeComponentRemoteWorker.Factory(moduleManager, remoteSenderService, NODE_COMPONENT_GRAPH_ID).create(workerCreateListener))
            .addNext(new NodeComponentPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
        return graph;
    }

    @SuppressWarnings("unchecked")
    public Graph<NodeMapping> createNodeMappingGraph() {
        QueueCreatorService<NodeMapping> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        Graph<NodeMapping> graph = GraphManager.INSTANCE.createIfAbsent(NODE_MAPPING_GRAPH_ID, NodeMapping.class);
        graph.addNode(new NodeMappingAggregationWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener))
            .addNext(new NodeMappingRemoteWorker.Factory(moduleManager, remoteSenderService, NODE_MAPPING_GRAPH_ID).create(workerCreateListener))
            .addNext(new NodeMappingPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
        return graph;
    }

    @SuppressWarnings("unchecked")
    public Graph<NodeReference> createNodeReferenceGraph() {
        QueueCreatorService<NodeReference> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        Graph<NodeReference> graph = GraphManager.INSTANCE.createIfAbsent(NODE_REFERENCE_GRAPH_ID, NodeReference.class);
        graph.addNode(new NodeReferenceAggregationWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener))
            .addNext(new NodeReferenceRemoteWorker.Factory(moduleManager, remoteSenderService, NODE_REFERENCE_GRAPH_ID).create(workerCreateListener))
            .addNext(new NodeReferencePersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
        return graph;
    }

    @SuppressWarnings("unchecked")
    public Graph<ServiceEntry> createServiceEntryGraph() {
        QueueCreatorService<ServiceEntry> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        Graph<ServiceEntry> graph = GraphManager.INSTANCE.createIfAbsent(SERVICE_ENTRY_GRAPH_ID, ServiceEntry.class);
        graph.addNode(new ServiceEntryAggregationWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener))
            .addNext(new ServiceEntryRemoteWorker.Factory(moduleManager, remoteSenderService, SERVICE_ENTRY_GRAPH_ID).create(workerCreateListener))
            .addNext(new ServiceEntryPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
        return graph;
    }

    @SuppressWarnings("unchecked")
    public Graph<ServiceReference> createServiceReferenceGraph() {
        QueueCreatorService<ServiceReference> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        Graph<ServiceReference> graph = GraphManager.INSTANCE.createIfAbsent(SERVICE_REFERENCE_GRAPH_ID, ServiceReference.class);
        graph.addNode(new ServiceReferenceAggregationWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener))
            .addNext(new ServiceReferenceRemoteWorker.Factory(moduleManager, remoteSenderService, SERVICE_REFERENCE_GRAPH_ID).create(workerCreateListener))
            .addNext(new ServiceReferencePersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
        return graph;
    }

    @SuppressWarnings("unchecked")
    public Graph<Segment> createSegmentGraph() {
        QueueCreatorService<Segment> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<Segment> graph = GraphManager.INSTANCE.createIfAbsent(SEGMENT_GRAPH_ID, Segment.class);
        graph.addNode(new SegmentPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
        return graph;
    }

    @SuppressWarnings("unchecked")
    public Graph<SegmentCost> createSegmentCostGraph() {
        QueueCreatorService<SegmentCost> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);

        Graph<SegmentCost> graph = GraphManager.INSTANCE.createIfAbsent(SEGMENT_COST_GRAPH_ID, SegmentCost.class);
        graph.addNode(new SegmentCostPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
        return graph;
    }
}
