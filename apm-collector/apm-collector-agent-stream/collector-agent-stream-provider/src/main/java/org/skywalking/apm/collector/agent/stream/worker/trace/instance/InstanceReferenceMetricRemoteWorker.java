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

package org.skywalking.apm.collector.agent.stream.worker.trace.instance;

import org.skywalking.apm.collector.agent.stream.service.graph.InstanceGraphNodeIdDefine;
import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.collector.remote.service.RemoteSenderService;
import org.skywalking.apm.collector.remote.service.Selector;
import org.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetric;
import org.skywalking.apm.collector.stream.worker.base.AbstractRemoteWorker;
import org.skywalking.apm.collector.stream.worker.base.AbstractRemoteWorkerProvider;
import org.skywalking.apm.collector.stream.worker.base.WorkerException;

/**
 * @author peng-yongsheng
 */
public class InstanceReferenceMetricRemoteWorker extends AbstractRemoteWorker<InstanceReferenceMetric, InstanceReferenceMetric> {

    public InstanceReferenceMetricRemoteWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return InstanceGraphNodeIdDefine.INSTANCE_REFERENCE_METRIC_REMOTE_NODE_ID;
    }

    @Override public Selector selector() {
        return Selector.HashCode;
    }

    @Override protected void onWork(InstanceReferenceMetric instanceReferenceMetric) throws WorkerException {
        onNext(instanceReferenceMetric);
    }

    public static class Factory extends AbstractRemoteWorkerProvider<InstanceReferenceMetric, InstanceReferenceMetric, InstanceReferenceMetricRemoteWorker> {

        public Factory(ModuleManager moduleManager, RemoteSenderService remoteSenderService, int graphId) {
            super(moduleManager, remoteSenderService, graphId);
        }

        @Override public InstanceReferenceMetricRemoteWorker workerInstance(ModuleManager moduleManager) {
            return new InstanceReferenceMetricRemoteWorker(moduleManager);
        }
    }
}
