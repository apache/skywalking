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

package org.apache.skywalking.oap.server.core.storage;

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsSessionCache;

/**
 * SessionCacheCallback provides a bridge for storage implementations
 */
@RequiredArgsConstructor
public class SessionCacheCallback {
    private final MetricsSessionCache sessionCache;
    private final Metrics metrics;
    /**
     * In some cases, this callback could be shared by multiple executions, such as SQLExecutor#additionalSQLs.
     * This flag would make sure, once one of the generated executions is failure, the whole metric would be removed
     * from the cache, and would not be added back. As those are executed in a batch mode. The sequence is uncertain.
     */
    private volatile boolean isFailed = false;

    public void onInsertCompleted() {
        if (isFailed) {
            return;
        }
        sessionCache.cacheAfterFlush(metrics);
    }

    public void onUpdateFailure() {
        isFailed = true;
        sessionCache.remove(metrics);
    }
}
