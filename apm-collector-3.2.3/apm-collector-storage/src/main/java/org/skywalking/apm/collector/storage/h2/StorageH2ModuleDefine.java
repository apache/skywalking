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

package org.skywalking.apm.collector.storage.h2;

import java.util.List;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.storage.StorageInstaller;
import org.skywalking.apm.collector.storage.StorageModuleDefine;
import org.skywalking.apm.collector.storage.StorageModuleGroupDefine;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.collector.storage.h2.dao.H2DAODefineLoader;
import org.skywalking.apm.collector.storage.h2.define.H2StorageInstaller;

/**
 * @author peng-yongsheng
 */
public class StorageH2ModuleDefine extends StorageModuleDefine {

    public static final String MODULE_NAME = "h2";

    @Override protected String group() {
        return StorageModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override public final boolean defaultModule() {
        return true;
    }

    @Override protected ModuleConfigParser configParser() {
        return new StorageH2ConfigParser();
    }

    @Override protected Client createClient() {
        return new H2Client(StorageH2Config.URL, StorageH2Config.USER_NAME, StorageH2Config.PASSWORD);
    }

    @Override public StorageInstaller storageInstaller() {
        return new H2StorageInstaller();
    }

    @Override public void injectClientIntoDAO(Client client) throws DefineException {
        H2DAODefineLoader loader = new H2DAODefineLoader();
        List<H2DAO> h2DAOs = loader.load();
        h2DAOs.forEach(h2DAO -> {
            h2DAO.setClient((H2Client)client);
            String interFaceName = h2DAO.getClass().getInterfaces()[0].getName();
            DAOContainer.INSTANCE.put(interFaceName, h2DAO);
        });
    }
}
