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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.StreamDefinition;
import org.apache.skywalking.oap.server.core.analysis.StreamProcessor;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.config.DownSamplingConfigService;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelCreator;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceSetter;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

/**
 * MetricsStreamProcessor represents the entrance and creator of the metrics streaming aggregation work flow.
 *
 * {@link #in(Metrics)} provides the major entrance for metrics streaming calculation.
 *
 * {@link #create(ModuleDefineHolder, Stream, Class)} creates the workers and work flow for every metrics.
 */
public class MetricsStreamProcessor implements StreamProcessor<Metrics> {
    /**
     * Singleton instance.
     */
    private final static MetricsStreamProcessor PROCESSOR = new MetricsStreamProcessor();

    /**
     * Worker table hosts all entrance workers.
     */
    private Map<Class<? extends Metrics>, MetricsAggregateWorker> entryWorkers = new HashMap<>();

    /**
     * Worker table hosts all persistent workers.
     */
    @Getter
    private List<MetricsPersistentWorker> persistentWorkers = new ArrayList<>();

    /**
     * Hold and forward CoreModuleConfig#enableDatabaseSession to the persistent worker.
     */
    @Setter
    @Getter
    private boolean enableDatabaseSession;

    public static MetricsStreamProcessor getInstance() {
        return PROCESSOR;
    }

    @Override
    public void in(Metrics metrics) {
        MetricsAggregateWorker worker = entryWorkers.get(metrics.getClass());
        if (worker != null) {
            worker.in(metrics);
        }
    }

    /**
     * Create the workers and work flow for every metrics.
     *
     * @param moduleDefineHolder pointer of the module define.
     * @param stream             definition of the metrics class.
     * @param metricsClass       data type of the streaming calculation.
     */
    @Override
    public void create(ModuleDefineHolder moduleDefineHolder, Stream stream, Class<? extends Metrics> metricsClass) throws StorageException {
        this.create(moduleDefineHolder, StreamDefinition.from(stream), metricsClass);
    }

    @SuppressWarnings("unchecked")
    public void create(ModuleDefineHolder moduleDefineHolder,
                       StreamDefinition stream,
                       Class<? extends Metrics> metricsClass) throws StorageException {
        final StorageBuilderFactory storageBuilderFactory = moduleDefineHolder.find(StorageModule.NAME)
                                                                              .provider()
                                                                              .getService(StorageBuilderFactory.class);
        final Class<? extends StorageBuilder> builder = storageBuilderFactory.builderOf(metricsClass, stream.getBuilder());

        StorageDAO storageDAO = moduleDefineHolder.find(StorageModule.NAME).provider().getService(StorageDAO.class);
        IMetricsDAO metricsDAO;
        try {
            metricsDAO = storageDAO.newMetricsDao(builder.getDeclaredConstructor().newInstance());
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new UnexpectedException("Create " + stream.getBuilder().getSimpleName() + " metrics DAO failure.", e);
        }

        ModelCreator modelSetter = moduleDefineHolder.find(CoreModule.NAME).provider().getService(ModelCreator.class);
        DownSamplingConfigService configService = moduleDefineHolder.find(CoreModule.NAME)
                                                                    .provider()
                                                                    .getService(DownSamplingConfigService.class);

        MetricsPersistentWorker hourPersistentWorker = null;
        MetricsPersistentWorker dayPersistentWorker = null;

        MetricsTransWorker transWorker = null;

        final MetricsExtension metricsExtension = metricsClass.getAnnotation(MetricsExtension.class);
        /**
         * All metrics default are `supportDownSampling` and `insertAndUpdate`, unless it has explicit definition.
         */
        boolean supportDownSampling = true;
        boolean supportUpdate = true;
        if (metricsExtension != null) {
            supportDownSampling = metricsExtension.supportDownSampling();
            supportUpdate = metricsExtension.supportUpdate();
        }
        if (supportDownSampling) {
            if (configService.shouldToHour()) {
                Model model = modelSetter.add(
                    metricsClass, stream.getScopeId(), new Storage(stream.getName(), DownSampling.Hour), false);
                hourPersistentWorker = downSamplingWorker(moduleDefineHolder, metricsDAO, model, supportUpdate);
            }
            if (configService.shouldToDay()) {
                Model model = modelSetter.add(
                    metricsClass, stream.getScopeId(), new Storage(stream.getName(), DownSampling.Day), false);
                dayPersistentWorker = downSamplingWorker(moduleDefineHolder, metricsDAO, model, supportUpdate);
            }

            transWorker = new MetricsTransWorker(
                moduleDefineHolder, hourPersistentWorker, dayPersistentWorker);
        }

        Model model = modelSetter.add(
            metricsClass, stream.getScopeId(), new Storage(stream.getName(), DownSampling.Minute), false);
        MetricsPersistentWorker minutePersistentWorker = minutePersistentWorker(
            moduleDefineHolder, metricsDAO, model, transWorker, supportUpdate);

        String remoteReceiverWorkerName = stream.getName() + "_rec";
        IWorkerInstanceSetter workerInstanceSetter = moduleDefineHolder.find(CoreModule.NAME)
                                                                       .provider()
                                                                       .getService(IWorkerInstanceSetter.class);
        workerInstanceSetter.put(remoteReceiverWorkerName, minutePersistentWorker, metricsClass);

        MetricsRemoteWorker remoteWorker = new MetricsRemoteWorker(moduleDefineHolder, remoteReceiverWorkerName);
        MetricsAggregateWorker aggregateWorker = new MetricsAggregateWorker(
            moduleDefineHolder, remoteWorker, stream.getName());

        entryWorkers.put(metricsClass, aggregateWorker);
    }

    private MetricsPersistentWorker minutePersistentWorker(ModuleDefineHolder moduleDefineHolder,
                                                           IMetricsDAO metricsDAO,
                                                           Model model,
                                                           MetricsTransWorker transWorker,
                                                           boolean supportUpdate) {
        AlarmNotifyWorker alarmNotifyWorker = new AlarmNotifyWorker(moduleDefineHolder);
        ExportWorker exportWorker = new ExportWorker(moduleDefineHolder);

        MetricsPersistentWorker minutePersistentWorker = new MetricsPersistentWorker(
            moduleDefineHolder, model, metricsDAO, alarmNotifyWorker, exportWorker, transWorker, enableDatabaseSession,
            supportUpdate
        );
        persistentWorkers.add(minutePersistentWorker);

        return minutePersistentWorker;
    }

    private MetricsPersistentWorker downSamplingWorker(ModuleDefineHolder moduleDefineHolder,
                                                       IMetricsDAO metricsDAO,
                                                       Model model,
                                                       boolean supportUpdate) {
        MetricsPersistentWorker persistentWorker = new MetricsPersistentWorker(
            moduleDefineHolder, model, metricsDAO, enableDatabaseSession, supportUpdate);
        persistentWorkers.add(persistentWorker);

        return persistentWorker;
    }
}
