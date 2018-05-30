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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.global.std;

import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricWorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.AbstractRemoteWorker;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.AbstractRemoteWorkerProvider;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.remote.service.RemoteSenderService;
import org.apache.skywalking.apm.collector.remote.service.Selector;
import org.apache.skywalking.apm.collector.storage.table.global.ResponseTimeDistribution;

/**
 * @author peng-yongsheng
 */
public class ResponseTimeDistributionMinuteRemoteWorker extends AbstractRemoteWorker<ResponseTimeDistribution, ResponseTimeDistribution> {

    private ResponseTimeDistributionMinuteRemoteWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return MetricWorkerIdDefine.RESPONSE_TIME_DISTRIBUTION_MINUTE_REMOTE_WORKER_ID;
    }

    @Override protected void onWork(ResponseTimeDistribution responseTimeDistribution) {
        onNext(responseTimeDistribution);
    }

    @Override public Selector selector() {
        return Selector.HashCode;
    }

    public static class Factory extends AbstractRemoteWorkerProvider<ResponseTimeDistribution, ResponseTimeDistribution, ResponseTimeDistributionMinuteRemoteWorker> {
        public Factory(ModuleManager moduleManager, RemoteSenderService remoteSenderService, int graphId) {
            super(moduleManager, remoteSenderService, graphId);
        }

        @Override public ResponseTimeDistributionMinuteRemoteWorker workerInstance(ModuleManager moduleManager) {
            return new ResponseTimeDistributionMinuteRemoteWorker(moduleManager);
        }
    }
}
