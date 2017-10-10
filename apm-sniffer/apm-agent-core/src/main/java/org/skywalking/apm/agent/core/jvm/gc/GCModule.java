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

package org.skywalking.apm.agent.core.jvm.gc;

import java.lang.management.GarbageCollectorMXBean;
import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.network.proto.GC;
import org.skywalking.apm.network.proto.GCPhrase;

/**
 * @author wusheng
 */
public abstract class GCModule implements GCMetricAccessor {
    private List<GarbageCollectorMXBean> beans;

    public GCModule(List<GarbageCollectorMXBean> beans) {
        this.beans = beans;
    }

    @Override
    public List<GC> getGCList() {
        List<GC> gcList = new LinkedList<GC>();
        for (GarbageCollectorMXBean bean : beans) {
            String name = bean.getName();
            GCPhrase phrase;
            if (name.equals(getNewGCName())) {
                phrase = GCPhrase.NEW;
            } else if (name.equals(getOldGCName())) {
                phrase = GCPhrase.OLD;
            } else {
                continue;
            }

            gcList.add(
                GC.newBuilder().setPhrase(phrase)
                    .setCount(bean.getCollectionCount())
                    .setTime(bean.getCollectionTime())
                    .build()
            );
        }

        return gcList;
    }

    protected abstract String getOldGCName();

    protected abstract String getNewGCName();
}
