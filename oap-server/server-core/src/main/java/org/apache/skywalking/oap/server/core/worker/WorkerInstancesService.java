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
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Worker Instance Service hosts all remote handler workers with the stream data type.
 */
public class WorkerInstancesService implements IWorkerInstanceSetter, IWorkerInstanceGetter {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerInstancesService.class);

    private final Map<String, RemoteHandleWorker> instances;

    public WorkerInstancesService() {
        this.instances = new HashMap<>();
    }

    @Override
    public RemoteHandleWorker get(String nextWorkerName) {
        return instances.get(nextWorkerName);
    }

    @Override
    public void put(String remoteReceiverWorkName, AbstractWorker instance,
        Class<? extends StreamData> streamDataClass) {
        if (instances.containsKey(remoteReceiverWorkName)) {
            throw new UnexpectedException("Duplicate worker name:" + remoteReceiverWorkName);
        }
        instances.put(remoteReceiverWorkName, new RemoteHandleWorker(instance, streamDataClass));
        LOGGER.debug("Worker {} has been registered as {}", instance.toString(), remoteReceiverWorkName);
    }
}
