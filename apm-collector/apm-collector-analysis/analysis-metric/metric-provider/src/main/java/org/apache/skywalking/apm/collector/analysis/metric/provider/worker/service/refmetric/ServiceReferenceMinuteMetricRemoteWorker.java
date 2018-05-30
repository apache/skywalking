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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.service.refmetric;

import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricWorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.AbstractRemoteWorker;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.AbstractRemoteWorkerProvider;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.remote.service.RemoteSenderService;
import org.apache.skywalking.apm.collector.remote.service.Selector;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetric;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceMinuteMetricRemoteWorker extends AbstractRemoteWorker<ServiceReferenceMetric, ServiceReferenceMetric> {

    private ServiceReferenceMinuteMetricRemoteWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return MetricWorkerIdDefine.SERVICE_REFERENCE_MINUTE_METRIC_REMOTE_WORKER_ID;
    }

    @Override protected void onWork(ServiceReferenceMetric serviceReferenceMetric) {
        onNext(serviceReferenceMetric);
    }

    @Override public Selector selector() {
        return Selector.HashCode;
    }

    public static class Factory extends AbstractRemoteWorkerProvider<ServiceReferenceMetric, ServiceReferenceMetric, ServiceReferenceMinuteMetricRemoteWorker> {

        public Factory(ModuleManager moduleManager, RemoteSenderService remoteSenderService, int graphId) {
            super(moduleManager, remoteSenderService, graphId);
        }

        @Override public ServiceReferenceMinuteMetricRemoteWorker workerInstance(ModuleManager moduleManager) {
            return new ServiceReferenceMinuteMetricRemoteWorker(moduleManager);
        }
    }
}
