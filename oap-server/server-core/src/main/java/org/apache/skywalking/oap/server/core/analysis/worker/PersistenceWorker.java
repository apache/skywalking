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

package org.apache.skywalking.oap.server.core.analysis.worker;

import java.util.Collection;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.data.ReadWriteSafeCache;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

/**
 * PersistenceWorker take the responsibility to pushing data to the final storage. The target storage is based on the
 * activate storage implementation. This worker controls the persistence flow.
 *
 * @param <INPUT> The type of worker input. All inputs will be merged and saved.
 */
@Slf4j
public abstract class PersistenceWorker<INPUT extends StorageData> extends AbstractWorker<INPUT> {
    @Getter(AccessLevel.PROTECTED)
    private final ReadWriteSafeCache<INPUT> cache;

    PersistenceWorker(ModuleDefineHolder moduleDefineHolder, ReadWriteSafeCache<INPUT> cache) {
        super(moduleDefineHolder);
        this.cache = cache;
    }

    /**
     * Accept the input, and push the data into the cache.
     */
    void onWork(List<INPUT> input) {
        cache.write(input);
    }

    /**
     * The persistence process is driven by the {@link org.apache.skywalking.oap.server.core.storage.PersistenceTimer}.
     * This is a notification method for the worker when every round finished.
     *
     * @param tookTime The time costs in this round.
     */
    public abstract void endOfRound(long tookTime);

    /**
     * Prepare the batch persistence, transfer all prepared data to the executable data format based on the storage
     * implementations.
     *
     * @param lastCollection  the source of transformation, they are in memory object format.
     * @param prepareRequests data in the formats for the final persistence operations.
     */
    public abstract void prepareBatch(Collection<INPUT> lastCollection, List<PrepareRequest> prepareRequests);

    public void buildBatchRequests(List<PrepareRequest> prepareRequests) {
        final List<INPUT> dataList = getCache().read();
        prepareBatch(dataList, prepareRequests);
    }
}
