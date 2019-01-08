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

package org.apache.skywalking.oap.server.core.storage.model;

import java.util.*;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.config.DownsamplingConfigService;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public abstract class ModelInstaller {

    private static final Logger logger = LoggerFactory.getLogger(ModelInstaller.class);

    private final ModuleManager moduleManager;

    public ModelInstaller(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public final void install(Client client) throws StorageException {
        IModelGetter modelGetter = moduleManager.find(CoreModule.NAME).provider().getService(IModelGetter.class);
        DownsamplingConfigService downsamplingConfigService = moduleManager.find(CoreModule.NAME).provider().getService(DownsamplingConfigService.class);

        List<Model> models = modelGetter.getModels();
        List<Model> allModels = new ArrayList<>();
        models.forEach(model -> {
            if (model.isIndicator()) {
                if (downsamplingConfigService.shouldToHour()) {
                    allModels.add(model.copy(model.getName() + Const.ID_SPLIT + Downsampling.Hour.getName()));
                }
                if (downsamplingConfigService.shouldToDay()) {
                    allModels.add(model.copy(model.getName() + Const.ID_SPLIT + Downsampling.Day.getName()));
                }
                if (downsamplingConfigService.shouldToMonth()) {
                    allModels.add(model.copy(model.getName() + Const.ID_SPLIT + Downsampling.Month.getName()));
                }
            }
        });
        allModels.addAll(models);

        boolean debug = System.getProperty("debug") != null;

        if (RunningMode.isNoInitMode()) {
            for (Model model : allModels) {
                while (!isExists(client, model)) {
                    try {
                        logger.info("table: {} does not exist. OAP is running in 'no-init' mode, waiting... retry 3s later.", model.getName());
                        Thread.sleep(3000L);
                    } catch (InterruptedException e) {
                    }
                }
            }
        } else {
            for (Model model : allModels) {
                if (!isExists(client, model)) {
                    logger.info("table: {} does not exist", model.getName());
                    createTable(client, model);
                } else if (debug) {
                    logger.info("table: {} exists", model.getName());
                    deleteTable(client, model);
                    createTable(client, model);
                }
                columnCheck(client, model);
            }
        }
    }

    public final void overrideColumnName(String columnName, String newName) {
        IModelOverride modelOverride = moduleManager.find(CoreModule.NAME).provider().getService(IModelOverride.class);
        modelOverride.overrideColumnName(columnName, newName);
    }

    protected abstract boolean isExists(Client client, Model model) throws StorageException;

    protected abstract void columnCheck(Client client, Model model) throws StorageException;

    protected abstract void deleteTable(Client client, Model model) throws StorageException;

    protected abstract void createTable(Client client, Model model) throws StorageException;
}
