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

package org.apache.skywalking.apm.collector.analysis.worker.model.impl;

import org.apache.skywalking.apm.collector.analysis.worker.model.base.AbstractLocalAsyncWorkerProvider;
import org.apache.skywalking.apm.collector.core.data.StreamData;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;

/**
 * @author peng-yongsheng
 */
public abstract class NonMergePersistenceWorkerProvider<INPUT_AND_OUTPUT extends StreamData, WORKER_TYPE extends NonMergePersistenceWorker<INPUT_AND_OUTPUT>> extends AbstractLocalAsyncWorkerProvider<INPUT_AND_OUTPUT, INPUT_AND_OUTPUT, WORKER_TYPE> {

    public NonMergePersistenceWorkerProvider(ModuleManager moduleManager) {
        super(moduleManager);
    }
}
