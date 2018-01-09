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

/**
 * The <code>AbstractLocalAsyncWorker</code> implementations represent workers,
 * which receive local asynchronous message.
 *
 * @author peng-yongsheng
 * @since v3.0-2017
 */
public abstract class AbstractLocalAsyncWorker<INPUT extends QueueData, OUTPUT extends QueueData> extends AbstractWorker<INPUT, OUTPUT> {

    public AbstractLocalAsyncWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }
}
