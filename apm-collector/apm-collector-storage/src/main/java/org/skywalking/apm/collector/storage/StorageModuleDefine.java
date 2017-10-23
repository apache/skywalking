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

package org.skywalking.apm.collector.storage;

import java.util.List;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.core.cluster.ClusterDataListenerDefine;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.framework.UnexpectedException;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.core.storage.StorageException;
import org.skywalking.apm.collector.core.storage.StorageInstaller;

/**
 * @author peng-yongsheng
 */
public abstract class StorageModuleDefine extends ModuleDefine implements ClusterDataListenerDefine {

    @Override protected void initializeOtherContext() {
        try {
            StorageModuleContext context = (StorageModuleContext)CollectorContextHelper.INSTANCE.getContext(StorageModuleGroupDefine.GROUP_NAME);
            Client client = createClient();
            client.initialize();
            context.setClient(client);
            injectClientIntoDAO(client);

            storageInstaller().install(client);
        } catch (ClientException | StorageException | DefineException e) {
            throw new UnexpectedException(e.getMessage());
        }
    }

    @Override public final List<Handler> handlerList() {
        return null;
    }

    @Override protected final Server server() {
        return null;
    }

    @Override protected final ModuleRegistration registration() {
        return null;
    }

    @Override public final ClusterDataListener listener() {
        return null;
    }

    public abstract StorageInstaller storageInstaller();

    public abstract void injectClientIntoDAO(Client client) throws DefineException;
}
