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

package org.apache.skywalking.oap.server.core.oal.rt;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Load the OAL Engine runtime, because runtime module depends on core, so we have to use class::forname to locate it.
 *
 * @author wusheng
 */
public class OALEngineLoader {
    private static volatile OALEngine ENGINE = null;
    private static ReentrantLock INIT_LOCK = new ReentrantLock();

    public static OALEngine get() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        if (ENGINE == null) {
            INIT_LOCK.lock();
            try {
                if (ENGINE == null) {
                    init();
                }
            } finally {
                INIT_LOCK.unlock();
            }
        }
        return ENGINE;
    }

    private static void init() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Class<?> engineRTClass = Class.forName("org.apache.skywalking.oal.rt.OALRuntime");
        ENGINE = (OALEngine)engineRTClass.newInstance();
    }
}
