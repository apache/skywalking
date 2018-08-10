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

package org.apache.skywalking.oap.server.core.register.worker;

import java.util.*;
import org.apache.skywalking.oap.server.core.register.RegisterSource;
import org.apache.skywalking.oap.server.core.source.Scope;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class RegisterPersistentWorker extends AbstractWorker<RegisterSource> {

    private static final Logger logger = LoggerFactory.getLogger(RegisterPersistentWorker.class);

    private final Scope scope;
    private final String modelName;
    private final Map<RegisterSource, RegisterSource> sources;
    private final IRegisterLockDAO registerLockDAO;
    private final IRegisterDAO registerDAO;

    public RegisterPersistentWorker(int workerId, String modelName, ModuleManager moduleManager,
        IRegisterDAO registerDAO, Scope scope) {
        super(workerId);
        this.modelName = modelName;
        this.sources = new HashMap<>();
        this.registerDAO = registerDAO;
        this.registerLockDAO = moduleManager.find(StorageModule.NAME).getService(IRegisterLockDAO.class);
        this.scope = scope;
    }

    @Override public final void in(RegisterSource registerSource) {
        if (!sources.containsKey(registerSource)) {
            sources.put(registerSource, registerSource);
        }
        if (registerSource.getEndOfBatchContext().isEndOfBatch()) {

            if (registerLockDAO.tryLock(scope)) {
                try {
                    sources.values().forEach(source -> {
                        try {
                            RegisterSource newSource = registerDAO.get(modelName, registerSource.id());
                            if (Objects.nonNull(newSource)) {
                                newSource.combine(newSource);
                                int sequence = registerDAO.max(modelName);
                                newSource.setSequence(sequence);
                                registerDAO.forceInsert(modelName, newSource);
                            } else {
                                registerDAO.forceUpdate(modelName, newSource);
                            }
                        } catch (Throwable t) {
                            logger.error(t.getMessage());
                        }
                    });
                } finally {
                    registerLockDAO.releaseLock(scope);
                }
            }
        }
    }
}
