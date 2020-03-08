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

package org.apache.skywalking.apm.plugin.finagle;

import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.trace.ExitSpan;

import static org.apache.skywalking.apm.plugin.finagle.ContextHolderFactory.getLocalContextHolder;
import static org.apache.skywalking.apm.plugin.finagle.ContextHolderFactory.getMarshalledContextHolder;
import static org.apache.skywalking.apm.plugin.finagle.FinagleCtxs.RPC;
import static org.apache.skywalking.apm.plugin.finagle.FinagleCtxs.getOperationName;
import static org.apache.skywalking.apm.plugin.finagle.FinagleCtxs.getPeerHost;
import static org.apache.skywalking.apm.plugin.finagle.FinagleCtxs.getSpan;

/**
 * We need set peer host to {@link ContextCarrier} in {@link ClientDestTracingFilterInterceptor}, but there is no
 * public method to do this, so we use this helper to achieve it.
 */
class ContextCarrierHelper {

    static void tryInjectContext() {
        String operationName = getOperationName();
        if (operationName == null) {
            return;
        }
        String peer = getPeerHost();
        if (peer == null) {
            return;
    }
        ExitSpan span = (ExitSpan) getSpan();
        /*
         * if peer and operationName is not null, we can ensure that span is not null
         */
        span.setPeer(peer);
        span.setOperationName(operationName);

        ContextCarrier contextCarrier = new ContextCarrier();
        span.inject(contextCarrier);

        SWContextCarrier swContextCarrier = SWContextCarrier.of(contextCarrier);
        swContextCarrier.setOperationName(operationName);
        getMarshalledContextHolder().let(SWContextCarrier$.MODULE$, swContextCarrier);

        /*
         * clear contexts
         */
        getLocalContextHolder().remove(RPC);
    }
}
