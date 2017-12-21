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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application;

import org.apache.skywalking.apm.collector.analysis.metric.define.graph.WorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.AbstractLocalAsyncWorkerProvider;
import org.apache.skywalking.apm.collector.analysis.worker.model.impl.AggregationWorker;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetric;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetric;

/**
 * @author peng-yongsheng
 */
public class ApplicationMetricAggregationWorker extends AggregationWorker<ApplicationReferenceMetric, ApplicationMetric> {

    public ApplicationMetricAggregationWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return WorkerIdDefine.APPLICATION_METRIC_AGGREGATION_WORKER_ID;
    }

    @Override protected ApplicationMetric transform(ApplicationReferenceMetric applicationReferenceMetric) {
        Integer applicationId = applicationReferenceMetric.getBehindApplicationId();
        Long timeBucket = applicationReferenceMetric.getTimeBucket();
        ApplicationMetric applicationMetric = new ApplicationMetric(String.valueOf(timeBucket) + Const.ID_SPLIT + String.valueOf(applicationId));
        applicationMetric.setApplicationId(applicationId);

        applicationMetric.setTransactionCalls(applicationReferenceMetric.getTransactionCalls());
        applicationMetric.setTransactionDurationSum(applicationReferenceMetric.getTransactionDurationSum());
        applicationMetric.setTransactionErrorCalls(applicationReferenceMetric.getTransactionErrorCalls());
        applicationMetric.setTransactionErrorDurationSum(applicationReferenceMetric.getTransactionErrorDurationSum());

        applicationMetric.setBusinessTransactionCalls(applicationReferenceMetric.getBusinessTransactionCalls());
        applicationMetric.setBusinessTransactionDurationSum(applicationReferenceMetric.getBusinessTransactionDurationSum());
        applicationMetric.setBusinessTransactionErrorCalls(applicationReferenceMetric.getBusinessTransactionErrorCalls());
        applicationMetric.setBusinessTransactionErrorDurationSum(applicationReferenceMetric.getBusinessTransactionErrorDurationSum());

        applicationMetric.setMqTransactionCalls(applicationReferenceMetric.getMqTransactionCalls());
        applicationMetric.setMqTransactionDurationSum(applicationReferenceMetric.getMqTransactionDurationSum());
        applicationMetric.setMqTransactionErrorCalls(applicationReferenceMetric.getMqTransactionErrorCalls());
        applicationMetric.setMqTransactionErrorDurationSum(applicationReferenceMetric.getMqTransactionErrorDurationSum());

        applicationMetric.setSatisfiedCount(applicationReferenceMetric.getSatisfiedCount());
        applicationMetric.setToleratingCount(applicationReferenceMetric.getToleratingCount());
        applicationMetric.setFrustratedCount(applicationReferenceMetric.getFrustratedCount());

        applicationMetric.setTimeBucket(timeBucket);

        return applicationMetric;
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ApplicationReferenceMetric, ApplicationMetric, ApplicationMetricAggregationWorker> {

        public Factory(ModuleManager moduleManager) {
            super(moduleManager);
        }

        @Override public ApplicationMetricAggregationWorker workerInstance(ModuleManager moduleManager) {
            return new ApplicationMetricAggregationWorker(moduleManager);
        }

        @Override public int queueSize() {
            return 256;
        }
    }
}
