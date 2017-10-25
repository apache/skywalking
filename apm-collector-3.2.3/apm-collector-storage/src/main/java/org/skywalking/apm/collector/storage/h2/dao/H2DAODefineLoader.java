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

package org.skywalking.apm.collector.storage.h2.dao;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.framework.Loader;
import org.skywalking.apm.collector.core.util.DefinitionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class H2DAODefineLoader implements Loader<List<H2DAO>> {

    private final Logger logger = LoggerFactory.getLogger(H2DAODefineLoader.class);

    @Override public List<H2DAO> load() throws DefineException {
        List<H2DAO> h2DAOs = new ArrayList<>();

        H2DAODefinitionFile definitionFile = new H2DAODefinitionFile();
        logger.info("h2 dao definition file name: {}", definitionFile.fileName());
        DefinitionLoader<H2DAO> definitionLoader = DefinitionLoader.load(H2DAO.class, definitionFile);
        for (H2DAO dao : definitionLoader) {
            logger.info("loaded h2 dao definition class: {}", dao.getClass().getName());
            h2DAOs.add(dao);
        }
        return h2DAOs;
    }
}
