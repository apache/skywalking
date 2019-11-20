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
package org.apache.skywalking.apm.plugin.spring.transaction.context;

/**
 * @author zhaoyuguang
 */

public interface Constants {
    String OPERATION_NAME_SPRING_TRANSACTION_PREFIX = "TX/";
    String OPERATION_NAME_SPRING_TRANSACTION_GET_TRANSACTION_METHOD = OPERATION_NAME_SPRING_TRANSACTION_PREFIX + "get/";
    String OPERATION_NAME_SPRING_TRANSACTION_NO_TRANSACTION_DEFINITION_GIVEN = OPERATION_NAME_SPRING_TRANSACTION_GET_TRANSACTION_METHOD + "noTransactionDefinitionGiven";
    String TAG_SPRING_TRANSACTION_ISOLATION_LEVEL = "isolationLevel";
    String TAG_SPRING_TRANSACTION_PROPAGATION_BEHAVIOR = "propagationBehavior";
    String TAG_SPRING_TRANSACTION_TIMEOUT = "timeout";
    String TAG_SPRING_TRANSACTION_IS_NEW_TRANSACTION = "isNewTransaction";
    String TAG_SPRING_TRANSACTION_HAS_SAVEPOINT = "hasSavepoint";
    String TAG_SPRING_TRANSACTION_ROLLBACK_ONLY = "rollbackOnly";
    String TAG_SPRING_TRANSACTION_IS_COMPLETED = "isCompleted";
}
