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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.service.metric;

import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetric;

/**
 * @author peng-yongsheng
 */
public class ServiceMetricCopy {

    public static ServiceMetric copy(ServiceMetric serviceMetric) {
        ServiceMetric newServiceMetric = new ServiceMetric();
        newServiceMetric.setId(serviceMetric.getId());
        newServiceMetric.setMetricId(serviceMetric.getMetricId());
        newServiceMetric.setSourceValue(serviceMetric.getSourceValue());

        newServiceMetric.setApplicationId(serviceMetric.getApplicationId());
        newServiceMetric.setInstanceId(serviceMetric.getInstanceId());
        newServiceMetric.setServiceId(serviceMetric.getServiceId());

        newServiceMetric.setTransactionCalls(serviceMetric.getTransactionCalls());
        newServiceMetric.setTransactionDurationSum(serviceMetric.getTransactionDurationSum());
        newServiceMetric.setTransactionErrorCalls(serviceMetric.getTransactionErrorCalls());
        newServiceMetric.setTransactionErrorDurationSum(serviceMetric.getTransactionErrorDurationSum());

        newServiceMetric.setBusinessTransactionCalls(serviceMetric.getBusinessTransactionCalls());
        newServiceMetric.setBusinessTransactionDurationSum(serviceMetric.getBusinessTransactionDurationSum());
        newServiceMetric.setBusinessTransactionErrorCalls(serviceMetric.getBusinessTransactionErrorCalls());
        newServiceMetric.setBusinessTransactionErrorDurationSum(serviceMetric.getBusinessTransactionErrorDurationSum());

        newServiceMetric.setMqTransactionCalls(serviceMetric.getMqTransactionCalls());
        newServiceMetric.setMqTransactionDurationSum(serviceMetric.getMqTransactionDurationSum());
        newServiceMetric.setMqTransactionErrorCalls(serviceMetric.getMqTransactionErrorCalls());
        newServiceMetric.setMqTransactionErrorDurationSum(serviceMetric.getMqTransactionErrorDurationSum());

        newServiceMetric.setTimeBucket(serviceMetric.getTimeBucket());

        return newServiceMetric;
    }
}
