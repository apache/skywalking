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

package org.skywalking.apm.collector.stream.worker.base;

import org.skywalking.apm.collector.core.data.Data;
import org.skywalking.apm.collector.remote.service.RemoteClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class RemoteWorkerRef extends WorkerRef {

    private final Logger logger = LoggerFactory.getLogger(RemoteWorkerRef.class);

    private final Boolean acrossJVM;
    private final AbstractRemoteWorker remoteWorker;
    private final RemoteClient remoteClient;

    public RemoteWorkerRef(Role role, AbstractRemoteWorker remoteWorker) {
        super(role);
        this.remoteWorker = remoteWorker;
        this.acrossJVM = false;
        this.remoteClient = null;
    }

    public RemoteWorkerRef(Role role, RemoteClient remoteClient) {
        super(role);
        this.remoteWorker = null;
        this.acrossJVM = true;
        this.remoteClient = remoteClient;
    }

    @Override
    public void tell(Object message) throws WorkerInvokeException {
        if (acrossJVM) {
            try {
                remoteClient.send(getRole().roleName(), (Data)message, getRole().dataDefine().remoteDataMappingId());
            } catch (Throwable e) {
                logger.error(e.getMessage(), e);
            }
        } else {
            remoteWorker.allocateJob(message);
        }
    }

    public Boolean isAcrossJVM() {
        return acrossJVM;
    }

    @Override public String toString() {
        return "acrossJVM: " + isAcrossJVM();
    }
}
