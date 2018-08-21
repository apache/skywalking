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

package org.apache.skywalking.apm.collector.analysis.worker.model.base;

import org.apache.skywalking.apm.collector.core.data.QueueData;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractLocalAsyncWorkerProvider<INPUT extends QueueData, OUTPUT extends QueueData, WORKER_TYPE extends AbstractLocalAsyncWorker<INPUT, OUTPUT>> extends AbstractWorkerProvider<INPUT, OUTPUT, WORKER_TYPE> {

    public abstract int queueSize();

    public AbstractLocalAsyncWorkerProvider(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override
    public final WorkerRef<INPUT, OUTPUT> create(WorkerCreateListener workerCreateListener) {
        WORKER_TYPE localAsyncWorker = workerInstance(getModuleManager());
        workerCreateListener.addWorker(localAsyncWorker);

        LocalAsyncWorkerRef<INPUT, OUTPUT> localAsyncWorkerRef = new LocalAsyncWorkerRef<>(localAsyncWorker);
        DataCarrier<INPUT> dataCarrier = new DataCarrier<>(1, 10000);
        localAsyncWorkerRef.setQueueEventHandler(dataCarrier);
        dataCarrier.consume(localAsyncWorkerRef, 1);
        return localAsyncWorkerRef;
    }
}
