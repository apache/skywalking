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

import org.skywalking.apm.collector.queue.QueueCreator;
import org.skywalking.apm.collector.queue.QueueEventHandler;
import org.skywalking.apm.collector.queue.QueueExecutor;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractLocalAsyncWorkerProvider<T extends AbstractLocalAsyncWorker & QueueExecutor> extends AbstractWorkerProvider<T> {

    public abstract int queueSize();

    @Override final public WorkerRef create() throws ProviderNotFoundException {
        T localAsyncWorker = workerInstance(getClusterContext());
        localAsyncWorker.preStart();

        if (localAsyncWorker instanceof PersistenceWorker) {
            PersistenceWorkerContainer.INSTANCE.addWorker((PersistenceWorker)localAsyncWorker);
        }

        QueueCreator queueCreator = ((QueueModuleContext)CollectorContextHelper.INSTANCE.getContext(QueueModuleGroupDefine.GROUP_NAME)).getQueueCreator();
        QueueEventHandler queueEventHandler = queueCreator.create(queueSize(), localAsyncWorker);

        LocalAsyncWorkerRef workerRef = new LocalAsyncWorkerRef(role(), queueEventHandler);
        getClusterContext().put(workerRef);
        localAsyncWorker.putSelfRef(workerRef);
        return workerRef;
    }
}
