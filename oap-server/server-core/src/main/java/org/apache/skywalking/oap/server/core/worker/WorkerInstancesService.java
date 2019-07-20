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

package org.apache.skywalking.oap.server.core.worker;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Worker Instance Service hosts all remote handler workers, including metrics and register.
 * All this kind of works should implemenet {@link IRemoteHandleWorker} to adapt {@link RemoteServiceGrpc}
 *
 * @author peng-yongsheng, wusheng
 */
public class WorkerInstancesService implements IWorkerInstanceSetter, IWorkerInstanceGetter {
    private static final Logger logger = LoggerFactory.getLogger(WorkerInstancesService.class);

    private final Map<String, AbstractWorker> instances;

    public WorkerInstancesService() {
        this.instances = new HashMap<>();
    }

    @Override public AbstractWorker get(String nextWorkerName) {
        return instances.get(nextWorkerName);
    }

    @Override public void put(String remoteReceiverWorkName, AbstractWorker instance) {
        if (instances.containsKey(remoteReceiverWorkName)) {
            throw new UnexpectedException("Duplicate worker name:" + remoteReceiverWorkName);
        }
        if (!(instance instanceof IRemoteHandleWorker)) {
            throw new IllegalStateException("Worker " + instance.getClass().getName() + " must implement IRemoteHandleWorker.");
        }
        instances.put(remoteReceiverWorkName, instance);
        logger.debug("Worker {} has been registered as {}", instance.toString(), remoteReceiverWorkName);
    }
}
