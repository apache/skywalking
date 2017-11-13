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

package org.skywalking.apm.collector.agent.stream;

import org.skywalking.apm.collector.agent.stream.graph.RegisterStreamGraph;
import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.skywalking.apm.collector.stream.worker.base.WorkerCreateListener;

/**
 * @author peng-yongsheng
 */
public class AgentStreamSingleton {

    private static AgentStreamSingleton INSTANCE;

    private final ModuleManager moduleManager;
    private final WorkerCreateListener workerCreateListener;

    public AgentStreamSingleton(ModuleManager moduleManager, WorkerCreateListener workerCreateListener) {
        this.moduleManager = moduleManager;
        this.workerCreateListener = workerCreateListener;
        createJVMGraph();
        createRegisterGraph();
        createTraceGraph();
    }

    public static synchronized AgentStreamSingleton getInstance(ModuleManager moduleManager,
        WorkerCreateListener workerCreateListener) {
        if (ObjectUtils.isEmpty(INSTANCE)) {
            INSTANCE = new AgentStreamSingleton(moduleManager, workerCreateListener);
        }
        return INSTANCE;
    }

    private void createJVMGraph() {

    }

    private void createRegisterGraph() {
        RegisterStreamGraph registerStreamGraph = new RegisterStreamGraph(moduleManager, workerCreateListener);
        registerStreamGraph.createApplicationRegisterGraph();
        registerStreamGraph.createInstanceRegisterGraph();
        registerStreamGraph.createServiceNameRegisterGraph();
    }

    private void createTraceGraph() {

    }
}
