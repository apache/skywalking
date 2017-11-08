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

/**
 * The <code>AbstractRemoteWorker</code> implementations represent workers,
 * which receive remote messages.
 * <p>
 * Usually, the implementations are doing persistent, or aggregate works.
 *
 * @author peng-yongsheng
 * @since v3.0-2017
 */
public abstract class AbstractRemoteWorker<INPUT extends Data, OUTPUT extends Data> extends AbstractWorker<INPUT, OUTPUT> {

    /**
     * This method use for message producer to call for send message.
     *
     * @param message The persistence data or metric data.
     * @throws Exception The Exception happen in {@link #onWork(INPUT)}
     */
    final public void allocateJob(INPUT message) {
        try {
            onWork(message);
        } catch (WorkerException e) {
        }
    }
}
