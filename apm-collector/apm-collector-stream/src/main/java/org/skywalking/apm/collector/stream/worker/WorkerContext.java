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

package org.skywalking.apm.collector.stream.worker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public abstract class WorkerContext implements Context {

    private final Logger logger = LoggerFactory.getLogger(WorkerContext.class);

    private Map<String, RemoteWorkerRef> remoteWorkerRefs;
    private Map<String, List<WorkerRef>> roleWorkers;
    private Map<String, Role> roles;

    WorkerContext() {
        this.roleWorkers = new HashMap<>();
        this.roles = new HashMap<>();
        this.remoteWorkerRefs = new HashMap<>();
    }

    private Map<String, List<WorkerRef>> getRoleWorkers() {
        return this.roleWorkers;
    }

    @Override final public WorkerRefs lookup(Role role) throws WorkerNotFoundException {
        if (getRoleWorkers().containsKey(role.roleName())) {
            return new WorkerRefs(getRoleWorkers().get(role.roleName()), role.workerSelector());
        } else {
            throw new WorkerNotFoundException("role=" + role.roleName() + ", no available worker.");
        }
    }

    @Override final public RemoteWorkerRef lookupInSide(String roleName) throws WorkerNotFoundException {
        if (remoteWorkerRefs.containsKey(roleName)) {
            return remoteWorkerRefs.get(roleName);
        } else {
            throw new WorkerNotFoundException("role=" + roleName + ", no available worker.");
        }
    }

    public final void putRole(Role role) {
        roles.put(role.roleName(), role);
    }

    public final Role getRole(String roleName) {
        return roles.get(roleName);
    }

    @Override final public void put(WorkerRef workerRef) {
        logger.debug("put worker reference into context, role name: {}", workerRef.getRole().roleName());
        if (!getRoleWorkers().containsKey(workerRef.getRole().roleName())) {
            getRoleWorkers().putIfAbsent(workerRef.getRole().roleName(), new ArrayList<>());
        }
        getRoleWorkers().get(workerRef.getRole().roleName()).add(workerRef);

        if (workerRef instanceof RemoteWorkerRef) {
            RemoteWorkerRef remoteWorkerRef = (RemoteWorkerRef)workerRef;
            if (!remoteWorkerRef.isAcrossJVM()) {
                remoteWorkerRefs.put(workerRef.getRole().roleName(), remoteWorkerRef);
            }
        }
    }

    @Override final public void remove(WorkerRef workerRef) {
        getRoleWorkers().remove(workerRef.getRole().roleName());
    }
}
