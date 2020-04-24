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

package org.apache.skywalking.oap.server.core.analysis.worker;

import java.io.IOException;
import org.apache.skywalking.oap.server.core.analysis.config.NoneStream;
import org.apache.skywalking.oap.server.core.storage.INoneStreamDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * None persistent use {@link INoneStreamDAO#insert(Model, NoneStream)} on get new data
 */
public class NoneStreamPersistentWorker extends AbstractWorker<NoneStream> {

    private static final Logger logger = LoggerFactory.getLogger(NoneStreamPersistentWorker.class);

    private final Model model;
    private final INoneStreamDAO configDAO;

    public NoneStreamPersistentWorker(ModuleDefineHolder moduleDefineHolder, Model model, INoneStreamDAO configDAO) {
        super(moduleDefineHolder);
        this.model = model;
        this.configDAO = configDAO;
    }

    @Override
    public void in(NoneStream noneStream) {
        try {
            configDAO.insert(model, noneStream);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
