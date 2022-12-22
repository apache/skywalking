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

package org.apache.skywalking.oap.server.core.cluster;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;

@Slf4j
public abstract class ClusterCoordinator implements ClusterRegister, ClusterNodesQuery, ClusterWatcherRegister {
    private boolean started = false;
    private final List<ClusterWatcher> clusterWatchers = new ArrayList<>();

    public void startCoordinator() throws ModuleStartException {
        if (!started) {
            start();
            started = true;
        } else {
            throw new ModuleStartException("Cluster coordinator has been started, should not start again.");
        }
    }

    /**
     * Initialize the required resources, such as healthy checker and listener.
     */
    protected abstract void start() throws ModuleStartException;

    @Override
    public void registerWatcher(final ClusterWatcher watcher) {
        this.clusterWatchers.add(watcher);
    }

    protected void notifyWatchers(List<RemoteInstance> remoteInstances) {
        if (log.isDebugEnabled()) {
            log.debug("Notify watchers and update cluster instances:{}", remoteInstances.toString());
        }
        this.clusterWatchers.forEach(
            clusterWatcher -> clusterWatcher.onClusterNodesChanged(remoteInstances));
    }
}
