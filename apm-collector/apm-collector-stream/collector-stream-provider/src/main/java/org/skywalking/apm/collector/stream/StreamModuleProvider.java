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

package org.skywalking.apm.collector.stream;

import java.util.Properties;
import org.skywalking.apm.collector.core.module.Module;
import org.skywalking.apm.collector.core.module.ModuleNotFoundException;
import org.skywalking.apm.collector.core.module.ModuleProvider;
import org.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.skywalking.apm.collector.queue.QueueModule;
import org.skywalking.apm.collector.queue.service.QueueCreatorService;
import org.skywalking.apm.collector.remote.RemoteModule;
import org.skywalking.apm.collector.remote.service.RemoteSenderService;
import org.skywalking.apm.collector.storage.StorageModule;
import org.skywalking.apm.collector.storage.service.DAOService;
import org.skywalking.apm.collector.stream.timer.PersistenceTimer;

/**
 * @author peng-yongsheng
 */
public class StreamModuleProvider extends ModuleProvider {

    @Override public String name() {
        return "worker";
    }

    @Override public Class<? extends Module> module() {
        return StreamModule.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {
    }

    @Override public void start(Properties config) throws ServiceNotProvidedException {
        PersistenceTimer persistenceTimer = new PersistenceTimer();
        try {
            QueueCreatorService queueCreatorService = getManager().find(QueueModule.NAME).getService(QueueCreatorService.class);
            RemoteSenderService remoteSenderService = getManager().find(RemoteModule.NAME).getService(RemoteSenderService.class);
            DAOService daoService = getManager().find(StorageModule.NAME).getService(DAOService.class);
            persistenceTimer.start(daoService);
        } catch (ModuleNotFoundException e) {
            throw new ServiceNotProvidedException(e.getMessage());
        }
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[] {RemoteModule.NAME, QueueModule.NAME, StorageModule.NAME};
    }
}
