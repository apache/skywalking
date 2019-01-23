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
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.data.EndOfBatchContext;
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
    private final DataCarrier<RegisterSource> dataCarrier;

    RegisterPersistentWorker(int workerId, String modelName, ModuleManager moduleManager,
        IRegisterDAO registerDAO, Scope scope) {
        super(workerId);
        this.modelName = modelName;
        this.sources = new HashMap<>();
        this.registerDAO = registerDAO;
        this.registerLockDAO = moduleManager.find(StorageModule.NAME).provider().getService(IRegisterLockDAO.class);
        this.scope = scope;
        this.dataCarrier = new DataCarrier<>("IndicatorPersistentWorker." + modelName, 1, 10000);
        this.dataCarrier.consume(new RegisterPersistentWorker.PersistentConsumer(this), 1, 200);
    }

    @Override public final void in(RegisterSource registerSource) {
        registerSource.setEndOfBatchContext(new EndOfBatchContext(false));
        dataCarrier.produce(registerSource);
    }

    private void onWork(RegisterSource registerSource) {
        if (!sources.containsKey(registerSource)) {
            sources.put(registerSource, registerSource);
        } else {
            sources.get(registerSource).combine(registerSource);
        }

        if (sources.size() > 1000 || registerSource.getEndOfBatchContext().isEndOfBatch()) {
            sources.values().forEach(source -> {
                try {
                    RegisterSource dbSource = registerDAO.get(modelName, source.id());
                    if (Objects.nonNull(dbSource)) {
                        if (dbSource.combine(source)) {
                            registerDAO.forceUpdate(modelName, dbSource);
                        }
                    } else {
                        int sequence;
                        if ((sequence = registerLockDAO.tryLockAndIncrement(scope)) != Const.NONE) {
                            try {
                                dbSource = registerDAO.get(modelName, source.id());
                                if (Objects.nonNull(dbSource)) {
                                    if (dbSource.combine(source)) {
                                        registerDAO.forceUpdate(modelName, dbSource);
                                    }
                                } else {
                                    source.setSequence(sequence);
                                    registerDAO.forceInsert(modelName, source);
                                }
                            } catch (Throwable t) {
                                logger.error(t.getMessage(), t);
                            } finally {
                                registerLockDAO.releaseLock(scope);
                            }
                        } else {
                            logger.info("{} inventory register try lock and increment sequence failure.", scope.name());
                        }
                    }
                } catch (Throwable t) {
                    logger.error(t.getMessage(), t);
                }
            });
            sources.clear();
        }
    }

    private class PersistentConsumer implements IConsumer<RegisterSource> {

        private final RegisterPersistentWorker persistent;

        private PersistentConsumer(RegisterPersistentWorker persistent) {
            this.persistent = persistent;
        }

        @Override public void init() {

        }

        @Override public void consume(List<RegisterSource> data) {
            Iterator<RegisterSource> sourceIterator = data.iterator();

            int i = 0;
            while (sourceIterator.hasNext()) {
                RegisterSource indicator = sourceIterator.next();
                i++;
                if (i == data.size()) {
                    indicator.getEndOfBatchContext().setEndOfBatch(true);
                }
                persistent.onWork(indicator);
            }
        }

        @Override public void onError(List<RegisterSource> data, Throwable t) {
            logger.error(t.getMessage(), t);
        }

        @Override public void onExit() {
        }
    }
}
