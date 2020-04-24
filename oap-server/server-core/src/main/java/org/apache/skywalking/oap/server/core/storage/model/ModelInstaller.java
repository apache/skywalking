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

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * The core module installation controller.
 */
@Slf4j
public abstract class ModelInstaller {
    private final ModuleManager moduleManager;

    public ModelInstaller(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    /**
     * Entrance of the storage entity installation work.
     */
    public final void install(Client client) throws StorageException {
        IModelManager modelGetter = moduleManager.find(CoreModule.NAME).provider().getService(IModelManager.class);

        List<Model> models = modelGetter.allModels();

        if (RunningMode.isNoInitMode()) {
            for (Model model : models) {
                while (!isExists(client, model)) {
                    try {
                        log.info(
                            "table: {} does not exist. OAP is running in 'no-init' mode, waiting... retry 3s later.",
                            model
                                .getName()
                        );
                        Thread.sleep(3000L);
                    } catch (InterruptedException e) {
                        log.error(e.getMessage());
                    }
                }
            }
        } else {
            for (Model model : models) {
                if (!isExists(client, model)) {
                    log.info("table: {} does not exist", model.getName());
                    createTable(client, model);
                }
            }
        }
    }

    /**
     * Installer implementation could use this API to request a column name replacement. This method delegates for
     * {@link IModelOverride}.
     */
    protected final void overrideColumnName(String columnName, String newName) {
        IModelOverride modelOverride = moduleManager.find(CoreModule.NAME).provider().getService(IModelOverride.class);
        modelOverride.overrideColumnName(columnName, newName);
    }

    /**
     * Check whether the storage entity exists. Need to implement based on the real storage.
     */
    protected abstract boolean isExists(Client client, Model model) throws StorageException;

    /**
     * Create the storage entity. All creations should be after the {@link #isExists(Client, Model)} check.
     */
    protected abstract void createTable(Client client, Model model) throws StorageException;
}
