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

import org.apache.skywalking.apm.collector.core.graph.Next;
import org.apache.skywalking.apm.collector.core.graph.NodeProcessor;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractWorker<INPUT, OUTPUT> implements NodeProcessor<INPUT, OUTPUT> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractWorker.class);

    private final ModuleManager moduleManager;

    AbstractWorker(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public final ModuleManager getModuleManager() {
        return moduleManager;
    }

    private Next<OUTPUT> next;

    /**
     * The data process logic in this method.
     *
     * @param message Cast the message object to a expect subclass.
     * @throws WorkerException Don't handle the exception, throw it.
     */
    protected abstract void onWork(INPUT message) throws WorkerException;

    @Override public final void process(INPUT input, Next<OUTPUT> next) {
        this.next = next;
        try {
            onWork(input);
        } catch (WorkerException e) {
            logger.error(e.getMessage(), e);
        }
    }

    protected final void onNext(OUTPUT message) {
        next.execute(message);
    }
}
