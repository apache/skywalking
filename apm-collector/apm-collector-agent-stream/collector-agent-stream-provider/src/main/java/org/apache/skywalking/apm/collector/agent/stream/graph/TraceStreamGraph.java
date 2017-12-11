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

import org.apache.skywalking.apm.collector.agent.stream.parser.standardization.SegmentStandardizationWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.trace.application.ApplicationComponentPersistenceWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.trace.application.ApplicationComponentRemoteWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.trace.application.ApplicationMappingAggregationWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.trace.segment.SegmentPersistenceWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.trace.service.ServiceEntryRemoteWorker;
import org.apache.skywalking.apm.collector.core.graph.Graph;
import org.apache.skywalking.apm.collector.core.graph.GraphManager;
import org.apache.skywalking.apm.collector.queue.QueueModule;
import org.apache.skywalking.apm.collector.remote.service.RemoteSenderService;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMapping;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetric;
import org.apache.skywalking.apm.collector.stream.worker.base.WorkerCreateListener;
import org.apache.skywalking.apm.collector.agent.stream.parser.standardization.SegmentStandardization;
import org.apache.skywalking.apm.collector.agent.stream.worker.trace.application.ApplicationComponentAggregationWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.trace.application.ApplicationMappingPersistenceWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.trace.application.ApplicationMappingRemoteWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.trace.global.GlobalTracePersistenceWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.trace.instance.InstanceMetricPersistenceWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.trace.segment.SegmentCostPersistenceWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.trace.service.ServiceEntryAggregationWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.trace.service.ServiceEntryPersistenceWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.trace.service.ServiceReferenceMetricAggregationWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.trace.service.ServiceReferenceMetricPersistenceWorker;
import org.apache.skywalking.apm.collector.agent.stream.worker.trace.service.ServiceReferenceMetricRemoteWorker;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.apache.skywalking.apm.collector.remote.RemoteModule;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponent;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.global.GlobalTrace;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetric;
import org.apache.skywalking.apm.collector.storage.table.segment.Segment;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentCost;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceEntry;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetric;

/**
 * @author peng-yongsheng
 */
public class TraceStreamGraph {
    public static final int GLOBAL_TRACE_GRAPH_ID = 300;
    public static final int INSTANCE_METRIC_GRAPH_ID = 301;
    public static final int APPLICATION_COMPONENT_GRAPH_ID = 302;
    public static final int APPLICATION_MAPPING_GRAPH_ID = 303;
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
    public void createApplicationComponentGraph() {
        QueueCreatorService<ApplicationComponent> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        Graph<ApplicationComponent> graph = GraphManager.INSTANCE.createIfAbsent(APPLICATION_COMPONENT_GRAPH_ID, ApplicationComponent.class);
        graph.addNode(new ApplicationComponentAggregationWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener))
            .addNext(new ApplicationComponentRemoteWorker.Factory(moduleManager, remoteSenderService, APPLICATION_COMPONENT_GRAPH_ID).create(workerCreateListener))
            .addNext(new ApplicationComponentPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
    }

    @SuppressWarnings("unchecked")
    public void createApplicationMappingGraph() {
        QueueCreatorService<ApplicationMapping> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

        Graph<ApplicationMapping> graph = GraphManager.INSTANCE.createIfAbsent(APPLICATION_MAPPING_GRAPH_ID, ApplicationMapping.class);
        graph.addNode(new ApplicationMappingAggregationWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener))
            .addNext(new ApplicationMappingRemoteWorker.Factory(moduleManager, remoteSenderService, APPLICATION_MAPPING_GRAPH_ID).create(workerCreateListener))
            .addNext(new ApplicationMappingPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
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

        createInstanceReferenceGraph(graph);
    }

    @SuppressWarnings("unchecked")
    private void createInstanceReferenceGraph(Graph<ServiceReferenceMetric> graph) {
        QueueCreatorService<ServiceReferenceMetric> aggregationQueueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);
        QueueCreatorService<InstanceReferenceMetric> persistenceQueueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

//        Node<?, ServiceReferenceMetric> serviceReferenceMetricNode = graph.toFinder().findNode(ServiceGraphNodeIdDefine.SERVICE_REFERENCE_METRIC_AGGREGATION_NODE_ID, ServiceReferenceMetric.class);
//        serviceReferenceMetricNode.addNext(new InstanceReferenceMetricAggregationWorker.Factory(moduleManager, aggregationQueueCreatorService).create(workerCreateListener))
//            .addNext(new InstanceReferenceMetricRemoteWorker.Factory(moduleManager, remoteSenderService, SERVICE_REFERENCE_GRAPH_ID).create(workerCreateListener))
//            .addNext(new InstanceReferencePersistenceWorker.Factory(moduleManager, persistenceQueueCreatorService).create(workerCreateListener));

        createApplicationReferenceMetricGraph(graph);
    }

    @SuppressWarnings("unchecked")
    private void createApplicationReferenceMetricGraph(Graph<ServiceReferenceMetric> graph) {
        QueueCreatorService<ApplicationReferenceMetric> queueCreatorService = moduleManager.find(QueueModule.NAME).getService(QueueCreatorService.class);
        RemoteSenderService remoteSenderService = moduleManager.find(RemoteModule.NAME).getService(RemoteSenderService.class);

//        Node<?, ServiceReferenceMetric> serviceReferenceMetricNode = graph.toFinder().findNode(ServiceGraphNodeIdDefine.SERVICE_REFERENCE_METRIC_AGGREGATION_NODE_ID, ServiceReferenceMetric.class);
//        graph.addNode(new ApplicationReferenceMetricAggregationWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener))
//            .addNext(new ApplicationReferenceMetricRemoteWorker.Factory(moduleManager, remoteSenderService, APPLICATION_REFERENCE_METRIC_GRAPH_ID).create(workerCreateListener))
//            .addNext(new ApplicationReferenceMetricPersistenceWorker.Factory(moduleManager, queueCreatorService).create(workerCreateListener));
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
