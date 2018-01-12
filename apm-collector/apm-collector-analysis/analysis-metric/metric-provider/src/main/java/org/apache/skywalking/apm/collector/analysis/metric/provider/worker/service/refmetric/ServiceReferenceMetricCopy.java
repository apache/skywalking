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

import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetric;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceMetricCopy {

    public static ServiceReferenceMetric copy(ServiceReferenceMetric serviceReferenceMetric) {
        ServiceReferenceMetric newServiceReferenceMetric = new ServiceReferenceMetric();
        newServiceReferenceMetric.setId(serviceReferenceMetric.getId());
        newServiceReferenceMetric.setMetricId(serviceReferenceMetric.getMetricId());
        newServiceReferenceMetric.setSourceValue(serviceReferenceMetric.getSourceValue());

        newServiceReferenceMetric.setFrontApplicationId(serviceReferenceMetric.getFrontApplicationId());
        newServiceReferenceMetric.setFrontInstanceId(serviceReferenceMetric.getFrontInstanceId());
        newServiceReferenceMetric.setFrontServiceId(serviceReferenceMetric.getFrontServiceId());
        newServiceReferenceMetric.setBehindApplicationId(serviceReferenceMetric.getBehindApplicationId());
        newServiceReferenceMetric.setBehindInstanceId(serviceReferenceMetric.getBehindInstanceId());
        newServiceReferenceMetric.setBehindServiceId(serviceReferenceMetric.getBehindServiceId());

        newServiceReferenceMetric.setTransactionCalls(serviceReferenceMetric.getTransactionCalls());
        newServiceReferenceMetric.setTransactionDurationSum(serviceReferenceMetric.getTransactionDurationSum());
        newServiceReferenceMetric.setTransactionErrorCalls(serviceReferenceMetric.getTransactionErrorCalls());
        newServiceReferenceMetric.setTransactionErrorDurationSum(serviceReferenceMetric.getTransactionErrorDurationSum());

        newServiceReferenceMetric.setBusinessTransactionCalls(serviceReferenceMetric.getBusinessTransactionCalls());
        newServiceReferenceMetric.setBusinessTransactionDurationSum(serviceReferenceMetric.getBusinessTransactionDurationSum());
        newServiceReferenceMetric.setBusinessTransactionErrorCalls(serviceReferenceMetric.getBusinessTransactionErrorCalls());
        newServiceReferenceMetric.setBusinessTransactionErrorDurationSum(serviceReferenceMetric.getBusinessTransactionErrorDurationSum());

        newServiceReferenceMetric.setMqTransactionCalls(serviceReferenceMetric.getMqTransactionCalls());
        newServiceReferenceMetric.setMqTransactionDurationSum(serviceReferenceMetric.getMqTransactionDurationSum());
        newServiceReferenceMetric.setMqTransactionErrorCalls(serviceReferenceMetric.getMqTransactionErrorCalls());
        newServiceReferenceMetric.setMqTransactionErrorDurationSum(serviceReferenceMetric.getMqTransactionErrorDurationSum());

        newServiceReferenceMetric.setTimeBucket(serviceReferenceMetric.getTimeBucket());
        return newServiceReferenceMetric;
    }
}
