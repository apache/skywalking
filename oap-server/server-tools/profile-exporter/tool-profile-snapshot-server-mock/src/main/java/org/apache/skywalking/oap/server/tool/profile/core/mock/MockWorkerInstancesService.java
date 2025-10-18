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

package org.apache.skywalking.oap.server.tool.profile.core.mock;

import org.apache.skywalking.oap.server.core.analysis.worker.MetricStreamKind;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceGetter;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceSetter;
import org.apache.skywalking.oap.server.core.worker.RemoteHandleWorker;
import org.apache.skywalking.oap.server.core.worker.WorkerInstancesService;

/**
 * Mock from {@link WorkerInstancesService}
 */
public class MockWorkerInstancesService implements IWorkerInstanceSetter, IWorkerInstanceGetter {
    @Override
    public RemoteHandleWorker get(String nextWorkerName) {
        return null;
    }

    @Override
    public void put(String remoteReceiverWorkName, AbstractWorker instance,
                    MetricStreamKind kind, Class<? extends StreamData> streamDataClass) {
    }
}
