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

package org.skywalking.apm.collector.core.framework;

import java.util.LinkedHashMap;
import java.util.Map;
import org.skywalking.apm.collector.core.cluster.ClusterModuleContext;

/**
 * @author peng-yongsheng
 */
public enum CollectorContextHelper {
    INSTANCE;

    private ClusterModuleContext clusterModuleContext;
    private Map<String, Context> contexts = new LinkedHashMap<>();

    public Context getContext(String moduleGroupName) {
        return contexts.get(moduleGroupName);
    }

    public ClusterModuleContext getClusterModuleContext() {
        return this.clusterModuleContext;
    }

    public void putContext(Context context) {
        if (contexts.containsKey(context.getGroupName())) {
            throw new UnsupportedOperationException("This module context was put, do not allow put a new one");
        } else {
            contexts.put(context.getGroupName(), context);
        }
    }

    public void putClusterContext(ClusterModuleContext clusterModuleContext) {
        this.clusterModuleContext = clusterModuleContext;
    }
}
