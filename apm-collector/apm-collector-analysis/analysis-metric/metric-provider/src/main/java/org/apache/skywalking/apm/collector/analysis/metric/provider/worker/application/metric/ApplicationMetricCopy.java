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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.application.metric;

import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetric;

/**
 * @author peng-yongsheng
 */
public class ApplicationMetricCopy {

    public static ApplicationMetric copy(ApplicationMetric applicationMetric) {
        ApplicationMetric newApplicationMetric = new ApplicationMetric();
        newApplicationMetric.setId(applicationMetric.getId());
        newApplicationMetric.setMetricId(applicationMetric.getMetricId());
        newApplicationMetric.setSourceValue(applicationMetric.getSourceValue());

        newApplicationMetric.setApplicationId(applicationMetric.getApplicationId());

        newApplicationMetric.setTransactionCalls(applicationMetric.getTransactionCalls());
        newApplicationMetric.setTransactionDurationSum(applicationMetric.getTransactionDurationSum());
        newApplicationMetric.setTransactionErrorCalls(applicationMetric.getTransactionErrorCalls());
        newApplicationMetric.setTransactionErrorDurationSum(applicationMetric.getTransactionErrorDurationSum());

        newApplicationMetric.setBusinessTransactionCalls(applicationMetric.getBusinessTransactionCalls());
        newApplicationMetric.setBusinessTransactionDurationSum(applicationMetric.getBusinessTransactionDurationSum());
        newApplicationMetric.setBusinessTransactionErrorCalls(applicationMetric.getBusinessTransactionErrorCalls());
        newApplicationMetric.setBusinessTransactionErrorDurationSum(applicationMetric.getBusinessTransactionErrorDurationSum());

        newApplicationMetric.setMqTransactionCalls(applicationMetric.getMqTransactionCalls());
        newApplicationMetric.setMqTransactionDurationSum(applicationMetric.getMqTransactionDurationSum());
        newApplicationMetric.setMqTransactionErrorCalls(applicationMetric.getMqTransactionErrorCalls());
        newApplicationMetric.setMqTransactionErrorDurationSum(applicationMetric.getMqTransactionErrorDurationSum());

        newApplicationMetric.setSatisfiedCount(applicationMetric.getSatisfiedCount());
        newApplicationMetric.setToleratingCount(applicationMetric.getToleratingCount());
        newApplicationMetric.setFrustratedCount(applicationMetric.getFrustratedCount());

        newApplicationMetric.setTimeBucket(applicationMetric.getTimeBucket());

        return newApplicationMetric;
    }
}
