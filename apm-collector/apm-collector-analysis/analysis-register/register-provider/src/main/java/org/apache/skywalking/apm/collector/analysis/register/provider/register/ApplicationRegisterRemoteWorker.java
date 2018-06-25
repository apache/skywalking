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

package org.apache.skywalking.apm.collector.analysis.register.provider.register;

import org.apache.skywalking.apm.collector.analysis.register.define.graph.WorkerIdDefine;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.*;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.remote.service.*;
import org.apache.skywalking.apm.collector.storage.table.register.Application;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ApplicationRegisterRemoteWorker extends AbstractRemoteWorker<Application, Application> {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationRegisterRemoteWorker.class);

    private ApplicationRegisterRemoteWorker(ModuleManager moduleManager) {
        super(moduleManager);
    }

    @Override public int id() {
        return WorkerIdDefine.APPLICATION_REGISTER_REMOTE_WORKER;
    }

    @Override protected void onWork(Application message) {
        if (logger.isDebugEnabled()) {
            logger.debug("application code: {}", message.getApplicationCode());
        }

        onNext(message);
    }

    @Override public Selector selector() {
        return Selector.ForeverFirst;
    }

    public static class Factory extends AbstractRemoteWorkerProvider<Application, Application, ApplicationRegisterRemoteWorker> {

        public Factory(ModuleManager moduleManager, RemoteSenderService remoteSenderService, int graphId) {
            super(moduleManager, remoteSenderService, graphId);
        }

        @Override public ApplicationRegisterRemoteWorker workerInstance(ModuleManager moduleManager) {
            return new ApplicationRegisterRemoteWorker(moduleManager);
        }
    }
}
