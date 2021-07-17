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

package org.apache.skywalking.apm.plugin.kafka.define;

import org.apache.skywalking.apm.agent.core.context.ContextManager;

public class InterceptorMethod {

    public static void beginKafkaPollAndInvokeIteration(String operationName) {
        ContextManager.getRuntimeContext().put(Constants.KAFKA_FLAG, new KafkaContext(operationName));
    }

    public static Object endKafkaPollAndInvokeIteration(Object ret) {
        KafkaContext context = (KafkaContext) ContextManager.getRuntimeContext().get(Constants.KAFKA_FLAG);
        if (context == null) {
            return ret;
        }
        if (context.isNeedStop()) {
            ContextManager.stopSpan();
        } else {
            ContextManager.getRuntimeContext().remove(Constants.KAFKA_FLAG);
        }
        return ret;
    }

    public static void handleMethodException(Throwable t) {
        KafkaContext context = (KafkaContext) ContextManager.getRuntimeContext().get(Constants.KAFKA_FLAG);
        if (context != null && context.isNeedStop()) {
            ContextManager.activeSpan().log(t);
        }
    }
}
