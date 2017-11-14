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

import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.collector.queue.base.QueueExecutor;

/**
 * The <code>AbstractLocalAsyncWorker</code> implementations represent workers,
 * which receive local asynchronous message.
 *
 * @author peng-yongsheng
 * @since v3.0-2017
 */
public abstract class AbstractLocalAsyncWorker<INPUT, OUTPUT> extends AbstractWorker<INPUT, OUTPUT> implements QueueExecutor<INPUT> {

    public AbstractLocalAsyncWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    /**
     * Receive message
     *
     * @param message The persistence data or metric data.
     * @throws WorkerException The Exception happen in {@link #onWork(INPUT)}
     */
    final public void allocateJob(INPUT message) throws WorkerException {
        onWork(message);
    }

    @Override public final void execute(INPUT message) throws WorkerException {
        onWork(message);
    }
}
