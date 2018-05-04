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

package org.apache.skywalking.apm.collector.core.data;

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.core.define.DefineException;
import org.apache.skywalking.apm.collector.core.define.DefinitionLoader;
import org.apache.skywalking.apm.collector.core.define.Loader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class StorageDefineLoader implements Loader<List<TableDefine>> {

    private static final Logger logger = LoggerFactory.getLogger(StorageDefineLoader.class);

    @Override public List<TableDefine> load() throws DefineException {
        List<TableDefine> tableDefines = new LinkedList<>();

        StorageDefinitionFile definitionFile = new StorageDefinitionFile();
        logger.info("storage definition file name: {}", definitionFile.fileName());
        DefinitionLoader<TableDefine> definitionLoader = DefinitionLoader.load(TableDefine.class, definitionFile);
        for (TableDefine tableDefine : definitionLoader) {
            logger.info("loaded storage definition class: {}", tableDefine.getClass().getName());
            tableDefines.add(tableDefine);
        }
        return tableDefines;
    }
}
