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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * The core module installation controller.
 */
@RequiredArgsConstructor
@Slf4j
public abstract class ModelInstaller implements ModelCreator.CreatingListener {
    protected final Client client;
    private final ModuleManager moduleManager;

    public void whenCreating(Model model) throws StorageException {
        if (RunningMode.isNoInitMode()) {
            while (!isExists(model)) {
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
        } else {
            if (!isExists(model)) {
                log.info("table: {} does not exist", model.getName());
                createTable(model);
            }
        }
    }

    /**
     * Installer implementation could use this API to request a column name replacement. This method delegates for
     * {@link ModelManipulator}.
     */
    protected final void overrideColumnName(String columnName, String newName) {
        ModelManipulator modelOverride = moduleManager.find(CoreModule.NAME)
                                                      .provider()
                                                      .getService(ModelManipulator.class);
        modelOverride.overrideColumnName(columnName, newName);
    }

    /**
     * Check whether the storage entity exists. Need to implement based on the real storage.
     */
    protected abstract boolean isExists(Model model) throws StorageException;

    /**
     * Create the storage entity. All creations should be after the {@link #isExists(Model)} check.
     */
    protected abstract void createTable(Model model) throws StorageException;
}
