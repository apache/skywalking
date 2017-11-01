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
import java.util.Properties;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.define.DefineException;
import org.skywalking.apm.collector.core.module.Module;
import org.skywalking.apm.collector.core.module.ModuleProvider;
import org.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.skywalking.apm.collector.storage.StorageException;
import org.skywalking.apm.collector.storage.StorageModule;
import org.skywalking.apm.collector.storage.base.dao.DAOContainer;
import org.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.skywalking.apm.collector.storage.h2.base.dao.H2DAODefineLoader;
import org.skywalking.apm.collector.storage.h2.base.define.H2StorageInstaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class StorageModuleH2Provider extends ModuleProvider {

    private final Logger logger = LoggerFactory.getLogger(StorageModuleH2Provider.class);

    private static final String URL = "url";
    private static final String USER_NAME = "user_name";
    private static final String PASSWORD = "password";

    private H2Client client;

    @Override public String name() {
        return "h2";
    }

    @Override public Class<? extends Module> module() {
        return StorageModule.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {
        String url = config.getProperty(URL);
        String userName = config.getProperty(USER_NAME);
        String password = config.getProperty(PASSWORD);
        client = new H2Client(url, userName, password);
    }

    @Override public void start(Properties config) throws ServiceNotProvidedException {
        try {
            client.initialize();

            H2DAODefineLoader loader = new H2DAODefineLoader();
            List<H2DAO> h2DAOs = loader.load();
            h2DAOs.forEach(h2DAO -> {
                h2DAO.setClient(client);
                String interFaceName = h2DAO.getClass().getInterfaces()[0].getName();
                DAOContainer.INSTANCE.put(interFaceName, h2DAO);
            });

            H2StorageInstaller installer = new H2StorageInstaller();
            installer.install(client);
        } catch (H2ClientException | DefineException | StorageException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[0];
    }
}
