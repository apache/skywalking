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

package org.apache.skywalking.apm.agent.core.jvm.memorypool;

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.network.language.agent.v3.MemoryPool;
import org.apache.skywalking.apm.network.language.agent.v3.PoolType;

public class UnknownMemoryPool implements MemoryPoolMetricsAccessor {
    @Override
    public List<MemoryPool> getMemoryPoolMetricsList() {
        List<MemoryPool> poolList = new LinkedList<MemoryPool>();
        poolList.add(MemoryPool.newBuilder().setType(PoolType.CODE_CACHE_USAGE).build());
        poolList.add(MemoryPool.newBuilder().setType(PoolType.NEWGEN_USAGE).build());
        poolList.add(MemoryPool.newBuilder().setType(PoolType.OLDGEN_USAGE).build());
        poolList.add(MemoryPool.newBuilder().setType(PoolType.SURVIVOR_USAGE).build());
        poolList.add(MemoryPool.newBuilder().setType(PoolType.PERMGEN_USAGE).build());
        poolList.add(MemoryPool.newBuilder().setType(PoolType.METASPACE_USAGE).build());
        return new LinkedList<MemoryPool>();
    }
}
