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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.instance.refmetric;

import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetric;

/**
 * @author peng-yongsheng
 */
public class InstanceReferenceMetricCopy {

    public static InstanceReferenceMetric copy(InstanceReferenceMetric instanceReferenceMetric) {
        InstanceReferenceMetric newInstanceReferenceMetric = new InstanceReferenceMetric();
        newInstanceReferenceMetric.setId(instanceReferenceMetric.getId());
        newInstanceReferenceMetric.setMetricId(instanceReferenceMetric.getMetricId());
        newInstanceReferenceMetric.setSourceValue(instanceReferenceMetric.getSourceValue());

        newInstanceReferenceMetric.setFrontApplicationId(instanceReferenceMetric.getFrontApplicationId());
        newInstanceReferenceMetric.setFrontInstanceId(instanceReferenceMetric.getFrontInstanceId());
        newInstanceReferenceMetric.setBehindApplicationId(instanceReferenceMetric.getBehindApplicationId());
        newInstanceReferenceMetric.setBehindInstanceId(instanceReferenceMetric.getBehindInstanceId());

        newInstanceReferenceMetric.setTransactionCalls(instanceReferenceMetric.getTransactionCalls());
        newInstanceReferenceMetric.setTransactionDurationSum(instanceReferenceMetric.getTransactionDurationSum());
        newInstanceReferenceMetric.setTransactionErrorCalls(instanceReferenceMetric.getTransactionErrorCalls());
        newInstanceReferenceMetric.setTransactionErrorDurationSum(instanceReferenceMetric.getTransactionErrorDurationSum());

        newInstanceReferenceMetric.setBusinessTransactionCalls(instanceReferenceMetric.getBusinessTransactionCalls());
        newInstanceReferenceMetric.setBusinessTransactionDurationSum(instanceReferenceMetric.getBusinessTransactionDurationSum());
        newInstanceReferenceMetric.setBusinessTransactionErrorCalls(instanceReferenceMetric.getBusinessTransactionErrorCalls());
        newInstanceReferenceMetric.setBusinessTransactionErrorDurationSum(instanceReferenceMetric.getBusinessTransactionErrorDurationSum());

        newInstanceReferenceMetric.setMqTransactionCalls(instanceReferenceMetric.getMqTransactionCalls());
        newInstanceReferenceMetric.setMqTransactionDurationSum(instanceReferenceMetric.getMqTransactionDurationSum());
        newInstanceReferenceMetric.setMqTransactionErrorCalls(instanceReferenceMetric.getMqTransactionErrorCalls());
        newInstanceReferenceMetric.setMqTransactionErrorDurationSum(instanceReferenceMetric.getMqTransactionErrorDurationSum());

        newInstanceReferenceMetric.setTimeBucket(instanceReferenceMetric.getTimeBucket());

        return newInstanceReferenceMetric;
    }
}
