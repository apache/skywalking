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

package org.skywalking.apm.collector.agent.stream.worker.trace.service;

import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.skywalking.apm.collector.storage.table.service.ServiceEntry;
import org.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorkerProvider;
import org.skywalking.apm.collector.stream.worker.impl.AggregationWorker;

/**
 * @author peng-yongsheng
 */
public class ServiceEntryAggregationWorker extends AggregationWorker<ServiceEntry, ServiceEntry> {

    public ServiceEntryAggregationWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return ServiceEntryAggregationWorker.class.hashCode();
    }

    public static class Factory extends AbstractLocalAsyncWorkerProvider<ServiceEntry, ServiceEntry, ServiceEntryAggregationWorker> {

        public Factory(ModuleManager moduleManager, QueueCreatorService<ServiceEntry> queueCreatorService) {
            super(moduleManager, queueCreatorService);
        }

        @Override public ServiceEntryAggregationWorker workerInstance(ModuleManager moduleManager) {
            return new ServiceEntryAggregationWorker(moduleManager);
        }

        @Override
        public int queueSize() {
            return 1024;
        }
    }
}
