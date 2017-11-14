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

package org.skywalking.apm.collector.storage.es.service;

import org.skywalking.apm.collector.storage.base.dao.DAO;
import org.skywalking.apm.collector.storage.base.dao.DAOContainer;
import org.skywalking.apm.collector.storage.base.dao.IPersistenceDAO;
import org.skywalking.apm.collector.storage.service.DAOService;

/**
 * @author peng-yongsheng
 */
public class ElasticSearchDAOService implements DAOService {

    private final DAOContainer daoContainer;

    public ElasticSearchDAOService(DAOContainer daoContainer) {
        this.daoContainer = daoContainer;
    }

    @Override public DAO get(Class<? extends DAO> daoInterfaceClass) {
        return daoContainer.get(daoInterfaceClass.getName());
    }

    @Override public IPersistenceDAO getPersistenceDAO(Class<? extends IPersistenceDAO> daoInterfaceClass) {
        return daoContainer.getPersistenceDAO(daoInterfaceClass.getName());
    }
}
