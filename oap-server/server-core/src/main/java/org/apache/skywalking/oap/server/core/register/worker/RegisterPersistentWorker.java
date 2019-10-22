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

import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.*;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.register.RegisterSource;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class RegisterPersistentWorker extends AbstractWorker<RegisterSource> {

    private static final Logger logger = LoggerFactory.getLogger(RegisterPersistentWorker.class);

    private final int scopeId;
    private final String modelName;
    private final Map<RegisterSource, RegisterSource> sources;
    private final IRegisterLockDAO registerLockDAO;
    private final IRegisterDAO registerDAO;
    private final DataCarrier<RegisterSource> dataCarrier;
    private HistogramMetrics histogram;

    RegisterPersistentWorker(ModuleDefineHolder moduleDefineHolder, String modelName,
                             IRegisterDAO registerDAO, int scopeId) {
        super(moduleDefineHolder);
        this.modelName = modelName;
        this.sources = new HashMap<>();
        this.registerDAO = registerDAO;
        this.registerLockDAO = moduleDefineHolder.find(StorageModule.NAME).provider().getService(IRegisterLockDAO.class);
        this.scopeId = scopeId;
        this.dataCarrier = new DataCarrier<>("MetricsPersistentWorker." + modelName, 1, 1000);
        MetricsCreator metricsCreator = moduleDefineHolder.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class);

        histogram = metricsCreator.createHistogramMetric("register_persistent_worker_latency", "The process latency of register persistent worker",
                new MetricsTag.Keys("module"), new MetricsTag.Values(modelName));

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
        registerSource.resetEndOfBatch();
        dataCarrier.produce(registerSource);
    }

    private void onWork(RegisterSource registerSource) {
        if (!sources.containsKey(registerSource)) {
            sources.put(registerSource, registerSource);
        } else {
            sources.get(registerSource).combine(registerSource);
        }

        try (HistogramMetrics.Timer ignored = histogram.createTimer()) {
            if (sources.size() > 1000 || registerSource.isEndOfBatch()) {
                sources.values().forEach(source -> {
                    try {
                        RegisterSource dbSource = registerDAO.get(modelName, source.id());
                        if (Objects.nonNull(dbSource)) {
                            if (dbSource.combine(source)) {
                                registerDAO.forceUpdate(modelName, dbSource);
                            }
                        } else {
                            int sequence;
                            if ((sequence = registerLockDAO.getId(scopeId, source)) != Const.NONE) {
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
                                }
                            } else {
                                logger.info("{} inventory register try lock and increment sequence failure.", DefaultScopeDefine.nameOf(scopeId));
                            }
                        }
                    } catch (Throwable t) {
                        logger.error(t.getMessage(), t);
                    }
                });
                sources.clear();
            }
        } catch (IOException e) {
            logger.error("not except exception happen", e);
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
                RegisterSource registerSource = sourceIterator.next();
                i++;
                if (i == data.size()) {
                    registerSource.asEndOfBatch();
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
