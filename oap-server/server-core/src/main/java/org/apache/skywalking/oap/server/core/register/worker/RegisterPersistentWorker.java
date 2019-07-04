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

import java.io.IOException;
import java.util.*;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.*;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.analysis.data.EndOfBatchContext;
import org.apache.skywalking.oap.server.core.register.RegisterSource;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.slf4j.*;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * @author peng-yongsheng
 */
public class RegisterPersistentWorker extends AbstractWorker<RegisterSource> {

    private static final Logger logger = LoggerFactory.getLogger(RegisterPersistentWorker.class);
    private final Cache<String, RegisterSource> registerSourceCache;

    private final int scopeId;
    private final String modelName;
    private final Map<RegisterSource, RegisterSource> sources;
    private final IRegisterLockDAO registerLockDAO;
    private final IRegisterDAO registerDAO;
    private final IBatchDAO batchDAO;
    private final DataCarrier<RegisterSource> dataCarrier;
    private final List batchPersistenList;

    RegisterPersistentWorker(ModuleDefineHolder moduleDefineHolder, String modelName,
                             IRegisterDAO registerDAO, int scopeId) {
        super(moduleDefineHolder);
        this.modelName = modelName;
        this.sources = new HashMap<>();
        this.registerDAO = registerDAO;
        this.registerLockDAO = moduleDefineHolder.find(StorageModule.NAME).provider().getService(IRegisterLockDAO.class);
        this.batchDAO = moduleDefineHolder.find(StorageModule.NAME).provider().getService(IBatchDAO.class);
        this.scopeId = scopeId;
        this.dataCarrier = new DataCarrier<>("MetricsPersistentWorker." + modelName, 1, 1000);
        this.batchPersistenList = new ArrayList<>();
        this.registerSourceCache = CacheBuilder.newBuilder().concurrencyLevel(1).initialCapacity(1000).maximumSize(1000).build();
        String name = "REGISTER_L2";
        int size = BulkConsumePool.Creator.recommendMaxSize() / 8;
        if (size == 0) {
            size = 1;
        }
        BulkConsumePool.Creator creator = new BulkConsumePool.Creator(name, size, 200);
        try {
            ConsumerPoolFactory.INSTANCE.createIfAbsent(name, creator);
        } catch (Exception e) {
            throw new UnexpectedException(e.getMessage(), e);
        }

        this.dataCarrier.consume(ConsumerPoolFactory.INSTANCE.get(name), new RegisterPersistentWorker.PersistentConsumer(this));
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
                RegisterSource cacheSource = null;
                try {
                    RegisterSource dbSource = this.getRegisterSourceCache(source.id());
                    if (Objects.nonNull(dbSource)) {
                        if (dbSource.combine(source)) {
                            batchPersistenList.add(registerDAO.prepareBatchUpdate(modelName, dbSource));
                            cacheSource = dbSource;
                        }
                    } else {
                        int sequence;
                        if ((sequence = registerLockDAO.getId(scopeId, source)) != Const.NONE) {
                            try {
                                dbSource = this.getRegisterSourceCache(source.id());
                                if (Objects.nonNull(dbSource)) {
                                    if (dbSource.combine(source)) {
                                        batchPersistenList.add(registerDAO.prepareBatchUpdate(modelName, dbSource));
                                        cacheSource = dbSource;
                                    }
                                } else {
                                    source.setSequence(sequence);
                                    batchPersistenList.add(registerDAO.prepareBatchInsert(modelName, source));
                                    cacheSource = source;
                                }
                            } catch (Throwable t) {
                                logger.error(t.getMessage(), t);
                                cacheSource = null;
                            }
                        } else {
                            logger.info("{} inventory register try lock and increment sequence failure.", DefaultScopeDefine.nameOf(scopeId));
                            cacheSource = null;
                        }
                    }
                } catch (Throwable t) {
                    logger.error(t.getMessage(), t);
                    cacheSource = null;
                }
                if (nonNull(cacheSource)) {
                    registerSourceCache.put(source.id(), cacheSource);
                } else {
                    registerSourceCache.invalidate(source.id());
                }
            });
            try {
                batchDAO.immediateBatchPersistence(batchPersistenList);
            } catch (Exception e) {
                logger.error("batch persistence error", e);
                this.invalidateCache(sources);
            }
            batchPersistenList.clear();
            sources.clear();
        }
    }

    private RegisterSource getRegisterSourceCache(String sourceId) throws IOException {
        RegisterSource registerSource = registerSourceCache.getIfPresent(sourceId);
        if (isNull(registerSource)) {
            registerSource = registerDAO.get(modelName, sourceId);
            if (nonNull(registerSource)) {
                registerSourceCache.put(sourceId, registerSource);
            }
        }
        return registerSource;
    }

    private void invalidateCache(Map<RegisterSource, RegisterSource> sources) {
        List invalidateKeys = new ArrayList<>(sources.size());
        sources.values().forEach(source -> {
            invalidateKeys.add(source.id());
        });
        registerSourceCache.invalidateAll(invalidateKeys);
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
                RegisterSource registerSource = sourceIterator.next();
                i++;
                if (i == data.size()) {
                    registerSource.getEndOfBatchContext().setEndOfBatch(true);
                }
                persistent.onWork(registerSource);
            }
        }

        @Override public void onError(List<RegisterSource> data, Throwable t) {
            logger.error(t.getMessage(), t);
        }

        @Override public void onExit() {
        }
    }
}
