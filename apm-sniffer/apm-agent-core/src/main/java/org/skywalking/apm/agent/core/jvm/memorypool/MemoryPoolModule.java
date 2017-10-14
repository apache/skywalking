/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.agent.core.jvm.memorypool;

import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.network.proto.MemoryPool;
import org.skywalking.apm.network.proto.PoolType;

/**
 * @author wusheng
 */
public abstract class MemoryPoolModule implements MemoryPoolMetricAccessor {
    private List<MemoryPoolMXBean> beans;

    public MemoryPoolModule(List<MemoryPoolMXBean> beans) {
        this.beans = beans;
    }

    @Override
    public List<MemoryPool> getMemoryPoolMetricList() {
        List<MemoryPool> poolList = new LinkedList<MemoryPool>();
        for (MemoryPoolMXBean bean : beans) {
            String name = bean.getName();
            PoolType type = null;
            if (name.equals(getCodeCacheName())) {
                type = PoolType.CODE_CACHE_USAGE;
            } else if (name.equals(getEdenName())) {
                type = PoolType.NEWGEN_USAGE;
            } else if (name.equals(getOldName())) {
                type = PoolType.OLDGEN_USAGE;
            } else if (name.equals(getSurvivorName())) {
                type = PoolType.SURVIVOR_USAGE;
            } else if (name.equals(getMetaspaceName())) {
                type = PoolType.METASPACE_USAGE;
            } else if (name.equals(getPermName())) {
                type = PoolType.PERMGEN_USAGE;
            } else {
                continue;
            }

            MemoryUsage usage = bean.getUsage();
            poolList.add(MemoryPool.newBuilder().setType(type)
                .setInit(usage.getInit())
                .setMax(usage.getMax())
                .setCommited(usage.getCommitted())
                .setUsed(usage.getUsed())
                .build());
        }
        return poolList;
    }

    protected abstract String getPermName();

    protected abstract String getCodeCacheName();

    protected abstract String getEdenName();

    protected abstract String getOldName();

    protected abstract String getSurvivorName();

    protected abstract String getMetaspaceName();
}
