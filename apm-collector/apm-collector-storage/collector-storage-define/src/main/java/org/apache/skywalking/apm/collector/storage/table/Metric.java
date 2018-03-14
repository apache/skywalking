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

package org.apache.skywalking.apm.collector.storage.table;

/**
 * @author peng-yongsheng
 */
public interface Metric {

    Integer getSourceValue();

    void setSourceValue(Integer sourceValue);

    Long getTimeBucket();

    void setTimeBucket(Long timeBucket);

    Long getTransactionCalls();

    void setTransactionCalls(Long transactionCalls);

    Long getTransactionErrorCalls();

    void setTransactionErrorCalls(Long transactionErrorCalls);

    Long getTransactionDurationSum();

    void setTransactionDurationSum(Long transactionDurationSum);

    Long getTransactionErrorDurationSum();

    void setTransactionErrorDurationSum(Long transactionErrorDurationSum);

    Long getTransactionAverageDuration();

    void setTransactionAverageDuration(Long transactionAverageDuration);

    Long getBusinessTransactionCalls();

    void setBusinessTransactionCalls(Long businessTransactionCalls);

    Long getBusinessTransactionErrorCalls();

    void setBusinessTransactionErrorCalls(Long businessTransactionErrorCalls);

    Long getBusinessTransactionDurationSum();

    void setBusinessTransactionDurationSum(Long businessTransactionDurationSum);

    Long getBusinessTransactionErrorDurationSum();

    void setBusinessTransactionErrorDurationSum(Long businessTransactionErrorDurationSum);

    Long getBusinessTransactionAverageDuration();

    void setBusinessTransactionAverageDuration(Long businessTransactionAverageDuration);

    Long getMqTransactionCalls();

    void setMqTransactionCalls(Long mqTransactionCalls);

    Long getMqTransactionErrorCalls();

    void setMqTransactionErrorCalls(Long mqTransactionErrorCalls);

    Long getMqTransactionDurationSum();

    void setMqTransactionDurationSum(Long mqTransactionDurationSum);

    Long getMqTransactionErrorDurationSum();

    void setMqTransactionErrorDurationSum(Long mqTransactionErrorDurationSum);

    Long getMqTransactionAverageDuration();

    void setMqTransactionAverageDuration(Long mqTransactionAverageDuration);
}
