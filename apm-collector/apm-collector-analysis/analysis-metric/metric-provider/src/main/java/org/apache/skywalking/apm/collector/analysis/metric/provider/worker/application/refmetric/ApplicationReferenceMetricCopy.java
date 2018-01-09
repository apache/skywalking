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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.refmetric;

import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetric;

/**
 * @author peng-yongsheng
 */
public class ApplicationReferenceMetricCopy {

    public static ApplicationReferenceMetric copy(ApplicationReferenceMetric applicationReferenceMetric) {
        ApplicationReferenceMetric newApplicationReferenceMetric = new ApplicationReferenceMetric();
        newApplicationReferenceMetric.setId(applicationReferenceMetric.getId());
        newApplicationReferenceMetric.setMetricId(applicationReferenceMetric.getMetricId());
        newApplicationReferenceMetric.setSourceValue(applicationReferenceMetric.getSourceValue());

        newApplicationReferenceMetric.setFrontApplicationId(applicationReferenceMetric.getFrontApplicationId());
        newApplicationReferenceMetric.setBehindApplicationId(applicationReferenceMetric.getBehindApplicationId());

        newApplicationReferenceMetric.setTransactionCalls(applicationReferenceMetric.getTransactionCalls());
        newApplicationReferenceMetric.setTransactionDurationSum(applicationReferenceMetric.getTransactionDurationSum());
        newApplicationReferenceMetric.setTransactionErrorCalls(applicationReferenceMetric.getTransactionErrorCalls());
        newApplicationReferenceMetric.setTransactionErrorDurationSum(applicationReferenceMetric.getTransactionErrorDurationSum());

        newApplicationReferenceMetric.setBusinessTransactionCalls(applicationReferenceMetric.getBusinessTransactionCalls());
        newApplicationReferenceMetric.setBusinessTransactionDurationSum(applicationReferenceMetric.getBusinessTransactionDurationSum());
        newApplicationReferenceMetric.setBusinessTransactionErrorCalls(applicationReferenceMetric.getBusinessTransactionErrorCalls());
        newApplicationReferenceMetric.setBusinessTransactionErrorDurationSum(applicationReferenceMetric.getBusinessTransactionErrorDurationSum());

        newApplicationReferenceMetric.setMqTransactionCalls(applicationReferenceMetric.getMqTransactionCalls());
        newApplicationReferenceMetric.setMqTransactionDurationSum(applicationReferenceMetric.getMqTransactionDurationSum());
        newApplicationReferenceMetric.setMqTransactionErrorCalls(applicationReferenceMetric.getMqTransactionErrorCalls());
        newApplicationReferenceMetric.setMqTransactionErrorDurationSum(applicationReferenceMetric.getMqTransactionErrorDurationSum());

        newApplicationReferenceMetric.setSatisfiedCount(applicationReferenceMetric.getSatisfiedCount());
        newApplicationReferenceMetric.setToleratingCount(applicationReferenceMetric.getToleratingCount());
        newApplicationReferenceMetric.setFrustratedCount(applicationReferenceMetric.getFrustratedCount());

        newApplicationReferenceMetric.setTimeBucket(applicationReferenceMetric.getTimeBucket());

        return newApplicationReferenceMetric;
    }
}
