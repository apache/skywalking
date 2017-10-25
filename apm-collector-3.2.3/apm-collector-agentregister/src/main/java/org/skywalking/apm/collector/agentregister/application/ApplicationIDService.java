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

package org.skywalking.apm.collector.agentregister.application;

import org.skywalking.apm.collector.agentregister.worker.application.ApplicationRegisterRemoteWorker;
import org.skywalking.apm.collector.cache.ApplicationCache;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.storage.define.register.ApplicationDataDefine;
import org.skywalking.apm.collector.stream.StreamModuleContext;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationIDService {

    private final Logger logger = LoggerFactory.getLogger(ApplicationIDService.class);

    public int getOrCreate(String applicationCode) {
        int applicationId = ApplicationCache.get(applicationCode);

        if (applicationId == 0) {
            StreamModuleContext context = (StreamModuleContext)CollectorContextHelper.INSTANCE.getContext(StreamModuleGroupDefine.GROUP_NAME);
            ApplicationDataDefine.Application application = new ApplicationDataDefine.Application(applicationCode, applicationCode, 0);
            try {
                context.getClusterWorkerContext().lookup(ApplicationRegisterRemoteWorker.WorkerRole.INSTANCE).tell(application);
            } catch (WorkerNotFoundException | WorkerInvokeException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return applicationId;
    }
}
