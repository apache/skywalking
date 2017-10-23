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
 * The <code>WorkerSelector</code> should be implemented by any class whose instances
 * are intended to provide select a {@link WorkerRef} from a {@link WorkerRef} list.
 * <p>
 * Actually, the <code>WorkerRef</code> is designed to provide a routing ability in the collector cluster
 *
 * @author peng-yongsheng
 * @since v3.0-2017
 */
public interface WorkerSelector<T extends WorkerRef> {

    /**
     * select a {@link WorkerRef} from a {@link WorkerRef} list.
     *
     * @param members given {@link WorkerRef} list, which size is greater than 0;
     * @param message the {@link AbstractWorker} is going to send.
     * @return the selected {@link WorkerRef}
     */
    T select(List<T> members, Object message);
}
