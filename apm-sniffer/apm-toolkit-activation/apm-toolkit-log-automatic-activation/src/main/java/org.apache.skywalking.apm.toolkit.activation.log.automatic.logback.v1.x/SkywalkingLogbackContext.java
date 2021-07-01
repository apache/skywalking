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

package org.apache.skywalking.apm.toolkit.activation.log.automatic.logback.v1.x;

import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * the cache of traceId for log
 * <p>
 */
public class SkywalkingLogbackContext {

    private static Map<ILoggingEvent, String> LOGBACKCONTEXT = new HashMap();

    public static String getAndFree(ILoggingEvent event) throws Exception {
        if (LOGBACKCONTEXT == null) {
            throw new Exception("can not get span from the ILoggingEvent");
        }
        String tid = LOGBACKCONTEXT.get(event);
        LOGBACKCONTEXT.remove(event);

        return tid;
    }

    public static boolean set(ILoggingEvent event, String traceId) throws Exception {
        if (LOGBACKCONTEXT != null) {
            if (LOGBACKCONTEXT.containsKey(event)) {
                throw new Exception("the ILoggingEvent already exist");
            }
            String put = LOGBACKCONTEXT.put(event, traceId);
            if (put != null) {
                return true;
            }
        }
        return false;
    }

}
