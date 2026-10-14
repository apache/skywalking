/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.library.kubernetes;

import org.apache.skywalking.oap.server.library.util.VirtualThreads;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Creates the executors of work that must stay off request threads and off fabric8's shared scheduler thread, as it
 * may block: reading from the API server or writing a log line.
 */
final class BackgroundExecutors {
    private static final long KEEP_ALIVE_SECONDS = 60;

    private BackgroundExecutors() {
    }

    /**
     * @return an executor of a virtual thread per task on JDK 25+, otherwise of one daemon thread that ends once idle
     */
    static ExecutorService create(final String name) {
        return VirtualThreads.createExecutor(
            name,
            () -> new ThreadPoolExecutor(0, 1, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
                final Thread thread = new Thread(r, name);
                thread.setDaemon(true);
                return thread;
            })
        );
    }
}
