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

package org.apache.skywalking.oap.server.core.cache;

import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AsyncProfilerTaskCacheTest {
    private static final String SERVICE_ID = "c2VydmljZQ==.1";

    /**
     * A refresh lists the tasks created within five minutes of now, newest first as the storage DAOs return them: the
     * task created a moment ago, then one that just ended. The service's agent must be sent the newer one, whichever
     * order the list has.
     */
    @Test
    void keepsTheNewestTaskOfAServiceWhateverTheListOrder() {
        final long now = System.currentTimeMillis();
        final AsyncProfilerTask ended = task("ended", now - 180_000L);
        final AsyncProfilerTask created = task("created", now - 30_000L);

        final AsyncProfilerTaskCache newestFirst = new AsyncProfilerTaskCache(mock(ModuleManager.class), new CoreModuleConfig());
        newestFirst.saveTask(SERVICE_ID, created);
        newestFirst.saveTask(SERVICE_ID, ended);
        assertEquals("created", newestFirst.getAsyncProfilerTask(SERVICE_ID).getId());

        final AsyncProfilerTaskCache oldestFirst = new AsyncProfilerTaskCache(mock(ModuleManager.class), new CoreModuleConfig());
        oldestFirst.saveTask(SERVICE_ID, ended);
        oldestFirst.saveTask(SERVICE_ID, created);
        assertEquals("created", oldestFirst.getAsyncProfilerTask(SERVICE_ID).getId());
    }

    private static AsyncProfilerTask task(final String id, final long createTime) {
        return AsyncProfilerTask.builder().id(id).serviceId(SERVICE_ID).createTime(createTime).duration(60).build();
    }
}
