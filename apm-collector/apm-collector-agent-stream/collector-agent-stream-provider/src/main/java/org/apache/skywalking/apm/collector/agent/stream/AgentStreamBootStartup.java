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


package org.apache.skywalking.apm.collector.agent.stream;

import org.apache.skywalking.apm.collector.agent.stream.graph.JvmMetricStreamGraph;
import org.apache.skywalking.apm.collector.agent.stream.graph.RegisterStreamGraph;
import org.apache.skywalking.apm.collector.agent.stream.graph.TraceStreamGraph;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.stream.worker.base.WorkerCreateListener;
import org.apache.skywalking.apm.collector.stream.timer.PersistenceTimer;

/**
 * @author peng-yongsheng
 */
public class AgentStreamBootStartup {

    private final ModuleManager moduleManager;
    private final WorkerCreateListener workerCreateListener;

    public AgentStreamBootStartup(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.workerCreateListener = new WorkerCreateListener();
    }

    public void start() {
        createJVMGraph();
        createRegisterGraph();
        createTraceGraph();

        PersistenceTimer timer = new PersistenceTimer();
        timer.start(moduleManager, workerCreateListener.getPersistenceWorkers());
    }

    private void createJVMGraph() {
        JvmMetricStreamGraph jvmMetricStreamGraph = new JvmMetricStreamGraph(moduleManager, workerCreateListener);
        jvmMetricStreamGraph.createCpuMetricGraph();
        jvmMetricStreamGraph.createGcMetricGraph();
        jvmMetricStreamGraph.createMemoryMetricGraph();
        jvmMetricStreamGraph.createMemoryPoolMetricGraph();
        jvmMetricStreamGraph.createHeartBeatGraph();
    }

    private void createRegisterGraph() {
        RegisterStreamGraph registerStreamGraph = new RegisterStreamGraph(moduleManager, workerCreateListener);
        registerStreamGraph.createApplicationRegisterGraph();
        registerStreamGraph.createInstanceRegisterGraph();
        registerStreamGraph.createServiceNameRegisterGraph();
    }

    private void createTraceGraph() {
        TraceStreamGraph traceStreamGraph = new TraceStreamGraph(moduleManager, workerCreateListener);
        traceStreamGraph.createSegmentStandardizationGraph();
        traceStreamGraph.createGlobalTraceGraph();
        traceStreamGraph.createInstanceMetricGraph();
        traceStreamGraph.createApplicationComponentGraph();
        traceStreamGraph.createApplicationMappingGraph();
//        traceStreamGraph.createApplicationReferenceMetricGraph();
        traceStreamGraph.createServiceEntryGraph();
        traceStreamGraph.createSegmentGraph();
        traceStreamGraph.createSegmentCostGraph();

        traceStreamGraph.createServiceReferenceGraph();
    }
}
