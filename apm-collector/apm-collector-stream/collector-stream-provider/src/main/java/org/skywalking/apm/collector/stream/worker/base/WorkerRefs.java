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

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class WorkerRefs<T extends WorkerRef> {

    private final Logger logger = LoggerFactory.getLogger(WorkerRefs.class);

    private List<T> workerRefs;
    private Role role;

    protected WorkerRefs(List<T> workerRefs) {
        this.workerRefs = workerRefs;
    }

    protected WorkerRefs(List<T> workerRefs, Role role) {
        this.workerRefs = workerRefs;
        this.role = role;
    }

    public void tell(Object message) throws WorkerInvokeException {
//        logger.debug("WorkerSelector instance of {}", workerSelector.getClass());
        workerRefs.forEach(workerRef -> {
            if (workerRef instanceof RemoteWorkerRef) {
                logger.debug("message hashcode: {}, select workers: {}", message.hashCode(), workerRef.toString());
            }
        });
//        workerSelector.select(workerRefs, message).tell(message);
    }
}
