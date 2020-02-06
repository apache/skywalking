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

import org.apache.skywalking.apm.agent.core.context.tag.AbstractTag;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;

/**
 * @author zhaoyuguang
 */

public interface Constants {
    String OPERATION_NAME_SPRING_TRANSACTION_PREFIX = "TX/";
    String OPERATION_NAME_SPRING_TRANSACTION_GET_TRANSACTION_METHOD = OPERATION_NAME_SPRING_TRANSACTION_PREFIX + "get/";
    String OPERATION_NAME_SPRING_TRANSACTION_NO_TRANSACTION_DEFINITION_GIVEN = OPERATION_NAME_SPRING_TRANSACTION_GET_TRANSACTION_METHOD + "noTransactionDefinitionGiven";
    AbstractTag<String> TAG_SPRING_TRANSACTION_ISOLATION_LEVEL = Tags.ofKey("isolationLevel");
    AbstractTag<String> TAG_SPRING_TRANSACTION_PROPAGATION_BEHAVIOR = Tags.ofKey("propagationBehavior");
    AbstractTag<String> TAG_SPRING_TRANSACTION_TIMEOUT = Tags.ofKey("timeout");
    AbstractTag<String> TAG_SPRING_TRANSACTION_IS_NEW_TRANSACTION = Tags.ofKey("isNewTransaction");
    AbstractTag<String> TAG_SPRING_TRANSACTION_HAS_SAVEPOINT = Tags.ofKey("hasSavepoint");
    AbstractTag<String> TAG_SPRING_TRANSACTION_ROLLBACK_ONLY = Tags.ofKey("rollbackOnly");
    AbstractTag<String> TAG_SPRING_TRANSACTION_IS_COMPLETED = Tags.ofKey("isCompleted");
}
