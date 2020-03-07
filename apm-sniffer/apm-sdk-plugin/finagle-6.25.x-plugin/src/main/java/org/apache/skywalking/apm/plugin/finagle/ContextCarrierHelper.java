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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * We need set peer host to {@link ContextCarrier} in {@link ClientDestTracingFilterInterceptor}, but there is no
 * public method to do this, so we use this helper to achieve it.
 */
class ContextCarrierHelper {

    private static Method SET_PEERHOST_METHOD = null;

    static {
        try {
            SET_PEERHOST_METHOD = ContextCarrier.class.getDeclaredMethod("setPeerHost", String.class);
            SET_PEERHOST_METHOD.setAccessible(true);
        } catch (NoSuchMethodException e) {
            // ignore
        }
    }

    static void setPeerHost(ContextCarrier contextCarrier, String peer) {
        if (SET_PEERHOST_METHOD != null) {
            try {
                SET_PEERHOST_METHOD.invoke(contextCarrier, peer);
            } catch (IllegalAccessException | InvocationTargetException e) {
                // ignore
            }
        }
    }
}
