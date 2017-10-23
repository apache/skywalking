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

package org.skywalking.apm.collector.stream;

import java.util.HashMap;
import java.util.Map;
import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;

/**
 * @author peng-yongsheng
 */
public class StreamModuleContext extends Context {

    private Map<Integer, DataDefine> dataDefineMap;
    private ClusterWorkerContext clusterWorkerContext;

    public StreamModuleContext(String groupName) {
        super(groupName);
        dataDefineMap = new HashMap<>();
    }

    public void putAllDataDefine(Map<Integer, DataDefine> dataDefineMap) {
        this.dataDefineMap.putAll(dataDefineMap);
    }

    public DataDefine getDataDefine(int dataDefineId) {
        return this.dataDefineMap.get(dataDefineId);
    }

    public ClusterWorkerContext getClusterWorkerContext() {
        return clusterWorkerContext;
    }

    public void setClusterWorkerContext(ClusterWorkerContext clusterWorkerContext) {
        this.clusterWorkerContext = clusterWorkerContext;
    }
}
