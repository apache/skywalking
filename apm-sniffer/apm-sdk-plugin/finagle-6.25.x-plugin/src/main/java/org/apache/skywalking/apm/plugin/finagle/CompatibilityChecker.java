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

import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

import static org.apache.skywalking.apm.plugin.finagle.ContextHolderFactory.getLocalContextHolder;
import static org.apache.skywalking.apm.plugin.finagle.ContextHolderFactory.getMarshalledContextHolder;

public class CompatibilityChecker {

    static ILog LOGGER = LogManager.getLogger(CompatibilityChecker.class);

    private static boolean COMPATIBILITY = false;

    static {
        try {
            if (FinagleCtxs.RPC != null
                    && FinagleCtxs.SW_SPAN != null
                    && checkContextHolder()) {
                COMPATIBILITY = true;
            }
        } catch (Throwable throwable) {
            LOGGER.error("this plugin does not support underlying finagle version.", throwable);
        }
    }

    public static boolean isCompatible() {
        return COMPATIBILITY;
    }

    private static boolean checkContextHolder() {
        try {
            getLocalContextHolder();
            getMarshalledContextHolder();
            return true;
        } catch (Throwable throwable) {
            throw throwable;
        }
    }
}
