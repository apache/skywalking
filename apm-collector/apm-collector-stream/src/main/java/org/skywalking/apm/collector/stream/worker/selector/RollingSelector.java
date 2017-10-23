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

package org.skywalking.apm.collector.stream.worker.selector;

import java.util.List;
import org.skywalking.apm.collector.stream.worker.AbstractWorker;
import org.skywalking.apm.collector.stream.worker.WorkerRef;

/**
 * The <code>RollingSelector</code> is a simple implementation of {@link WorkerSelector}.
 * It choose {@link WorkerRef} nearly random, by round-robin.
 *
 * @author peng-yongsheng
 * @since v3.0-2017
 */
public class RollingSelector implements WorkerSelector<WorkerRef> {

    private int index = 0;

    /**
     * Use round-robin to select {@link WorkerRef}.
     *
     * @param members given {@link WorkerRef} list, which size is greater than 0;
     * @param message message the {@link AbstractWorker} is going to send.
     * @return the selected {@link WorkerRef}
     */
    @Override
    public WorkerRef select(List<WorkerRef> members, Object message) {
        int size = members.size();
        index++;
        int selectIndex = Math.abs(index) % size;
        return members.get(selectIndex);
    }
}
