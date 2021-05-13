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
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.ExitSpan;
import org.apache.skywalking.apm.agent.core.context.trace.ExitTypeSpan;

import static org.apache.skywalking.apm.plugin.finagle.Constants.PENDING_OP_NAME;
import static org.apache.skywalking.apm.plugin.finagle.FinagleCtxs.getPeerHost;
import static org.apache.skywalking.apm.plugin.finagle.FinagleCtxs.getSWContextCarrier;

class ContextCarrierHelper {

    /**
     * In {@link ClientTracingFilterInterceptor}, we create {@link ExitSpan} without op name and peer information, and
     * we use {@link AnnotationInterceptor.Rpc} and {@link ClientDestTracingFilterInterceptor} to put op name and
     * peer information into LocalContext, but the order of these two interceptors are uncertain, so after each
     * interceptor, we check if the op name and peer information are exists in LocalContext, if it exists, we set it
     * to span and inject to contextCarrier.
     */
    static void tryInjectContext(AbstractSpan span) {
        /*
         * this may be a {@link NoopSpan}.
         */
        if (span != null && span.isExit()) {
            String operationName = span.getOperationName();
            if (PENDING_OP_NAME.equals(operationName)) {
                return;
            }
            String peer = getPeerHost();
            if (peer == null) {
                return;
            }
            span.setPeer(peer);

            ContextCarrier contextCarrier = new ContextCarrier();
            ((ExitTypeSpan) span).inject(contextCarrier);

            SWContextCarrier swContextCarrier = getSWContextCarrier();
            // we can ensure swContextCarrier is not null here
            swContextCarrier.setContextCarrier(contextCarrier);
            swContextCarrier.setOperationName(operationName);
        }
    }
}
