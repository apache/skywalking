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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.component;

import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricWorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.AbstractLocalAsyncWorkerProvider;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.WorkerException;
import org.apache.skywalking.apm.collector.analysis.worker.model.impl.AggregationWorker;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponent;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponentTable;

/**
 * @author peng-yongsheng
 */
public class ApplicationComponentMinuteAggregationWorker extends AggregationWorker<ApplicationComponent, ApplicationComponent> {

    private ApplicationComponentMinuteAggregationWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return MetricWorkerIdDefine.APPLICATION_COMPONENT_MINUTE_AGGREGATION_WORKER_ID;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ApplicationComponent, ApplicationComponent, ApplicationComponentMinuteAggregationWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public ApplicationComponentMinuteAggregationWorker workerInstance(ModuleManager moduleManager) {
            return new ApplicationComponentMinuteAggregationWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }

    @GraphComputingMetric(name = "/aggregation/onWork/" + ApplicationComponentTable.TABLE)
    @Override protected void onWork(ApplicationComponent message) throws WorkerException {
        super.onWork(message);
    }
}
