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

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.StreamDefinition;
import org.apache.skywalking.oap.server.core.analysis.StreamProcessor;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.config.DownSamplingConfigService;
import org.apache.skywalking.oap.server.core.query.TTLStatusQuery;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelRegistry;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceSetter;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MetricsStreamProcessor represents the entrance and creator of the metrics streaming aggregation work flow.
 *
 * {@link #in(Metrics)} provides the major entrance for metrics streaming calculation.
 *
 * {@link #create(ModuleDefineHolder, Stream, Class)} creates the workers and work flow for every metrics.
 */
@Slf4j
public class MetricsStreamProcessor implements StreamProcessor<Metrics> {
    /**
     * Singleton instance.
     */
    private static final MetricsStreamProcessor PROCESSOR = new MetricsStreamProcessor();

    /**
     * Worker table hosts all entrance workers. Lock-free concurrent reads from {@link #in(Metrics)}
     * — hot path. Writes are serialized by callers (startup-once via {@link MeterSystem}'s
     * {@code synchronized create}, runtime rule hot-update via its per-file lock). The switch from
     * HashMap to ConcurrentHashMap closes a latent race that was previously safe only because all
     * writes happened at boot.
     */
    private final Map<Class<? extends Metrics>, MetricsAggregateWorker> entryWorkers = new ConcurrentHashMap<>();

    /**
     * Worker table hosts all persistent workers. CopyOnWriteArrayList is mandatory (not
     * optional): {@code PersistenceTimer.extractDataAndSave} calls
     * {@code workers.addAll(getPersistentWorkers())} which iterates the source list under its
     * own synchronization — with a plain ArrayList and concurrent runtime-rule add/remove, that
     * iteration could CME or drop entries. CoW makes the snapshot iterator-safe for readers and
     * accepts the write amplification, which only occurs during rule mutation (rare).
     */
    @Getter
    private final List<MetricsPersistentWorker> persistentWorkers = new CopyOnWriteArrayList<>();

    /**
     * Counts samples arriving at {@link #in(Metrics)} for a class that is not registered in
     * {@link #entryWorkers}. Bumped during structural rollout windows (new metric not yet known
     * on this node) and during hot-remove drain (handler gone but samples still in flight). The
     * counter is read by runtime-rule observability; emission to the telemetry pipeline is wired
     * by the runtime-rule module (avoiding a hard dependency on TelemetryModule here).
     */
    @Getter
    private final AtomicLong unroutableSampleCount = new AtomicLong();

    /**
     * Tracks which metric classes have already produced an unroutable warning, so the log line
     * fires at most once per class per process lifetime. Allows operators to see the transition
     * without flooding the log during extended rollout windows.
     */
    private final Map<Class<?>, Boolean> warnedUnroutableClasses = new ConcurrentHashMap<>();

    /**
     * Entry workers that are temporarily out of {@link #entryWorkers} but whose underlying
     * persistent workers, handler state, and storage model are all still live. A metric class
     * lands here during the Suspend phase of a runtime-rule structural update — samples arriving
     * during Suspend hit the null-worker path in {@link #in(Metrics)}, their drops accumulating
     * on {@link #unroutableSampleCount}, which matches the design contract that samples for the
     * bundle in flux are dropped for the duration. The entry is restored to {@link #entryWorkers}
     * on the matching {@link #resumeDispatch(Class)}, and the pre-suspend worker resumes
     * processing with its merge buffer / lastSendTime intact.
     *
     * <p>Distinct from {@link #removeMetric} which is destructive: Suspend keeps the measure and
     * class alive so a short-lived pause (seconds to a minute) is reversible without repeating
     * DDL or losing persistent-worker state.
     */
    private final Map<Class<? extends Metrics>, MetricsAggregateWorker> suspendedWorkers = new ConcurrentHashMap<>();

    /**
     * The period of L1 aggregation flush. Unit is ms.
     */
    @Setter
    @Getter
    private long l1FlushPeriod = 500;
    /**
     * The threshold of session time. Unit is ms. Default value is 70s.
     */
    @Setter
    private long storageSessionTimeout = 70_000;

    public static MetricsStreamProcessor getInstance() {
        return PROCESSOR;
    }

    @Override
    public void in(Metrics metrics) {
        MetricsAggregateWorker worker = entryWorkers.get(metrics.getClass());
        if (worker != null) {
            worker.in(metrics);
            return;
        }
        // Unknown class — either a structural rollout window (new metric not yet registered on
        // this node), a hot-remove window (handler gone, sample still in flight from a peer),
        // or a legitimate bug (sample for a class never registered). Bump the counter and warn
        // at most once per class so operators can see rollout transitions without log flood.
        unroutableSampleCount.incrementAndGet();
        if (warnedUnroutableClasses.putIfAbsent(metrics.getClass(), Boolean.TRUE) == null) {
            log.warn("Dropped sample for unregistered metric class {}; further drops for this "
                + "class will be silent until it is registered again.", metrics.getClass().getName());
        }
    }

    /**
     * Create the workers and work flow for OAL metrics.
     *
     * @param moduleDefineHolder pointer of the module define.
     * @param stream             definition of the metrics class.
     * @param metricsClass       data type of the streaming calculation.
     */
    public void create(ModuleDefineHolder moduleDefineHolder,
                       Stream stream,
                       Class<? extends Metrics> metricsClass) throws StorageException {
        this.create(moduleDefineHolder, StreamDefinition.from(stream), metricsClass, MetricStreamKind.OAL);
    }

    /**
     * Create the workers and work flow for MAL meter
     */
    public void create(ModuleDefineHolder moduleDefineHolder,
                       StreamDefinition stream,
                       Class<? extends Metrics> meterClass) throws StorageException {
        this.create(moduleDefineHolder, stream, meterClass, MetricStreamKind.MAL);
    }

    /**
     * Opt-aware variant invoked from the runtime-rule MAL path. Peer nodes pass
     * {@link StorageManipulationOpt#localCacheOnly()} so every downstream {@code ModelRegistry.add}
     * records per-resource outcomes and suppresses server-side install. Main-node on-demand
     * callers (REST {@code /addOrUpdate}) pass {@link StorageManipulationOpt#fullInstall()}.
     * Startup-path callers (stream registration for static rules) pass
     * {@link StorageManipulationOpt#createIfAbsent()} so boot never reshapes the backend.
     */
    public void create(ModuleDefineHolder moduleDefineHolder,
                       StreamDefinition stream,
                       Class<? extends Metrics> meterClass,
                       StorageManipulationOpt opt) throws StorageException {
        this.create(moduleDefineHolder, stream, meterClass, MetricStreamKind.MAL, opt);
    }

    private void create(ModuleDefineHolder moduleDefineHolder,
                        StreamDefinition stream,
                        Class<? extends Metrics> metricsClass,
                        MetricStreamKind kind) throws StorageException {
        this.create(moduleDefineHolder, stream, metricsClass, kind, StorageManipulationOpt.createIfAbsent());
    }

    private void create(ModuleDefineHolder moduleDefineHolder,
                        StreamDefinition stream,
                        Class<? extends Metrics> metricsClass,
                        MetricStreamKind kind,
                        StorageManipulationOpt opt) throws StorageException {
        final StorageBuilderFactory storageBuilderFactory = moduleDefineHolder.find(StorageModule.NAME)
                                                                              .provider()
                                                                              .getService(StorageBuilderFactory.class);
        final Class<? extends StorageBuilder> builder = storageBuilderFactory.builderOf(
            metricsClass, stream.getBuilder());

        StorageDAO storageDAO = moduleDefineHolder.find(StorageModule.NAME).provider().getService(StorageDAO.class);
        IMetricsDAO metricsDAO;
        try {
            metricsDAO = storageDAO.newMetricsDao(builder.getDeclaredConstructor().newInstance());
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException |
                 InvocationTargetException e) {
            throw new UnexpectedException("Create " + stream.getBuilder().getSimpleName() + " metrics DAO failure.", e);
        }

        ModelRegistry modelSetter = moduleDefineHolder.find(CoreModule.NAME).provider().getService(ModelRegistry.class);
        DownSamplingConfigService configService = moduleDefineHolder.find(CoreModule.NAME)
                                                                    .provider()
                                                                    .getService(DownSamplingConfigService.class);
        TTLStatusQuery ttlStatusQuery = moduleDefineHolder.find(CoreModule.NAME)
                                                          .provider()
                                                          .getService(TTLStatusQuery.class);

        MetricsPersistentWorker hourPersistentWorker = null;
        MetricsPersistentWorker dayPersistentWorker = null;

        MetricsTransWorker transWorker = null;

        final MetricsExtension metricsExtension = metricsClass.getAnnotation(MetricsExtension.class);
        /**
         * All metrics default are `supportDownSampling` and `insertAndUpdate`, unless it has explicit definition.
         */
        boolean supportDownSampling = true;
        boolean supportUpdate = true;
        boolean timeRelativeID = true;
        if (metricsExtension != null) {
            supportDownSampling = metricsExtension.supportDownSampling();
            supportUpdate = metricsExtension.supportUpdate();
            timeRelativeID = metricsExtension.timeRelativeID();
        }
        if (supportDownSampling) {
            if (configService.shouldToHour()) {
                Model model = modelSetter.add(
                    metricsClass, stream.getScopeId(),
                    new Storage(stream.getName(), timeRelativeID, DownSampling.Hour),
                    opt
                );
                int hourTTL = ttlStatusQuery.getMetricsTTL(model);
                hourPersistentWorker = downSamplingWorker(moduleDefineHolder, metricsDAO, model, supportUpdate, kind, hourTTL);
            }
            if (configService.shouldToDay()) {
                Model model = modelSetter.add(
                    metricsClass, stream.getScopeId(),
                    new Storage(stream.getName(), timeRelativeID, DownSampling.Day),
                    opt
                );
                int dayTTL = ttlStatusQuery.getMetricsTTL(model);
                dayPersistentWorker = downSamplingWorker(moduleDefineHolder, metricsDAO, model, supportUpdate, kind, dayTTL);
            }

            transWorker = new MetricsTransWorker(
                moduleDefineHolder, hourPersistentWorker, dayPersistentWorker);
        }

        Model model = modelSetter.add(
            metricsClass, stream.getScopeId(),
            new Storage(stream.getName(), timeRelativeID, DownSampling.Minute),
            opt
        );

        // Shape-mismatch gate — boot registers under create-if-absent, which records
        // SKIPPED_SHAPE_MISMATCH outcomes when the backend already holds a shape that
        // differs from what the model declares. We must NOT register workers against a
        // backend the installer refused to reshape: ingest would silently write against an
        // inconsistent schema (or land rows the query side can't decode). Boot continues
        // with the metric disabled — operator reconciles explicitly via the runtime-rule
        // on-demand endpoint (the only workflow that may change backend schema).
        if (opt.hasShapeMismatch()) {
            log.error("Shape mismatch for metric {} — installer refused to reshape the "
                + "backend; skipping worker registration so ingest won't write against an "
                + "inconsistent schema. Operator action: reshape via POST /runtime/rule/addOrUpdate "
                + "or align the rule shape with the backend. First mismatch: {}",
                metricsClass.getName(), opt.firstShapeMismatch());
            return;
        }

        int minuteTTL = ttlStatusQuery.getMetricsTTL(model);
        MetricsPersistentWorker minutePersistentWorker = minutePersistentWorker(
            moduleDefineHolder, metricsDAO, model, transWorker, supportUpdate, kind, metricsClass, minuteTTL);

        String remoteReceiverWorkerName = stream.getName() + "_rec";
        IWorkerInstanceSetter workerInstanceSetter = moduleDefineHolder.find(CoreModule.NAME)
                                                                       .provider()
                                                                       .getService(IWorkerInstanceSetter.class);
        workerInstanceSetter.put(remoteReceiverWorkerName, minutePersistentWorker, kind, metricsClass);

        MetricsRemoteWorker remoteWorker = new MetricsRemoteWorker(moduleDefineHolder, remoteReceiverWorkerName);
        MetricsAggregateWorker aggregateWorker = new MetricsAggregateWorker(
            moduleDefineHolder, remoteWorker, stream.getName(), l1FlushPeriod, metricsClass);
        entryWorkers.put(metricsClass, aggregateWorker);
    }

    private MetricsPersistentWorker minutePersistentWorker(ModuleDefineHolder moduleDefineHolder,
                                                           IMetricsDAO metricsDAO,
                                                           Model model,
                                                           MetricsTransWorker transWorker,
                                                           boolean supportUpdate,
                                                           MetricStreamKind kind,
                                                           Class<? extends Metrics> metricsClass,
                                                           int metricsDataTTL) {
        AlarmNotifyWorker alarmNotifyWorker = new AlarmNotifyWorker(moduleDefineHolder);
        ExportMetricsWorker exportWorker = new ExportMetricsWorker(moduleDefineHolder);

        MetricsPersistentMinWorker minutePersistentWorker = new MetricsPersistentMinWorker(
            moduleDefineHolder, model, metricsDAO, alarmNotifyWorker, exportWorker, transWorker,
            supportUpdate, storageSessionTimeout, metricsDataTTL, kind, metricsClass
        );
        persistentWorkers.add(minutePersistentWorker);

        return minutePersistentWorker;
    }

    private MetricsPersistentWorker downSamplingWorker(ModuleDefineHolder moduleDefineHolder,
                                                       IMetricsDAO metricsDAO,
                                                       Model model,
                                                       boolean supportUpdate,
                                                       MetricStreamKind kind,
                                                       int metricsDataTTL) {
        MetricsPersistentWorker persistentWorker = new MetricsPersistentWorker(
            moduleDefineHolder, model, metricsDAO,
            supportUpdate, storageSessionTimeout, metricsDataTTL, kind
        );
        persistentWorkers.add(persistentWorker);

        return persistentWorker;
    }

    /**
     * Remove the streaming-calculation chain for a runtime-removed metric class. Symmetric to
     * {@link #create(ModuleDefineHolder, Stream, Class)} / {@link #create(ModuleDefineHolder,
     * StreamDefinition, Class)}: drops the L1 entry worker, drains and deregisters the L2 min
     * worker, removes all down-sampling persistent workers that share the same model name.
     *
     * <p>Order matters and is load-bearing — draining before deregister prevents data loss:
     * <ol>
     *   <li>Remove from {@link #entryWorkers} first — stops new {@link #in(Metrics)} routes.</li>
     *   <li>Drain L1: flush {@code MergableBufferedData} downstream to L2, then deregister the
     *       L1 queue handler.</li>
     *   <li>Drain L2: unconditionally build batch requests from the min worker's cache and
     *       submit via {@code IBatchDAO.flush}. Wait for the future to complete before
     *       deregistering the L2 queue handler, so no pending-flush data is orphaned.</li>
     *   <li>Remove all {@link MetricsPersistentWorker} entries (minute + any down-sampling
     *       variants) for this model name from {@link #persistentWorkers}.</li>
     * </ol>
     *
     * <p>Any samples in-flight through the shared L1 / L2 queue partitions for this class after
     * the corresponding handler is removed will hit the null-handler path and be dropped (bumped
     * on {@code BatchQueue}'s dropped-type warn-once counter). This is the accepted cost of
     * moving metric shape atomically during a structural apply.
     *
     * <p>Not safe to call concurrently with {@link #create(ModuleDefineHolder, Stream, Class)} or
     * another {@link #removeMetric} for the same class — the runtime-rule module serializes via
     * its per-file lock; startup registrations are single-threaded. Safe to call concurrently
     * with {@link #in(Metrics)} for unrelated metric classes.
     *
     * @param moduleDefineHolder pointer of the module define, used to obtain {@code IBatchDAO}
     *                           for synchronous L2 drain submission
     * @param metricsClass       the metric class to deregister
     * @return {@code true} if an entry worker existed and was removed, {@code false} if no such
     *         metric class was registered (idempotent, caller can ignore)
     */
    public boolean removeMetric(final ModuleDefineHolder moduleDefineHolder,
                                final Class<? extends Metrics> metricsClass) {
        // removeMetric supersedes any pending Suspend. If the class is currently suspended
        // (parked in suspendedWorkers, absent from entryWorkers), we still need to drain L1,
        // flush L2, and deregister the queue handlers — a structural removal has to reach
        // the same end state regardless of whether dispatch was paused at the time. Pull the
        // worker out of whichever map holds it and feed the same drain path below.
        MetricsAggregateWorker aggregateWorker = entryWorkers.remove(metricsClass);
        if (aggregateWorker == null) {
            aggregateWorker = suspendedWorkers.remove(metricsClass);
        } else {
            // Not suspended at removal time, but belt-and-suspenders: clear any stale
            // suspended entry so post-remove resumeDispatch doesn't resurrect anything.
            suspendedWorkers.remove(metricsClass);
        }
        if (aggregateWorker == null) {
            return false;
        }
        // Clear the dropped-sample memo so a re-register of the same class after a remove-add
        // cycle gets a fresh warn on any accidental samples arriving during its own rollout.
        warnedUnroutableClasses.remove(metricsClass);

        // L1 drain + deregister: safe because no new samples can arrive here — the entryWorkers
        // entry was atomic-removed above.
        try {
            aggregateWorker.drainAndDeregister();
        } catch (final Throwable t) {
            log.error("L1 drain failed for metric class {}; proceeding with L2 drain anyway",
                metricsClass.getName(), t);
        }

        // Find every persistent worker that belongs to this metric's model name. Downsampling
        // variants share the same model name but different DownSampling enum values.
        // MetricsPersistentMinWorker is the only subclass that registers on the L2 queue, so
        // deregistration is targeted only at min workers.
        final String modelName = aggregateWorker.getModelName();
        final List<MetricsPersistentWorker> victims = new ArrayList<>();
        for (final MetricsPersistentWorker w : persistentWorkers) {
            if (modelName != null && modelName.equals(w.getModel().getName())) {
                victims.add(w);
            }
        }

        // L2 drain: pull all pending requests from every victim's cache and submit synchronously.
        final IBatchDAO batchDAO;
        try {
            batchDAO = moduleDefineHolder.find(StorageModule.NAME).provider().getService(IBatchDAO.class);
        } catch (final Throwable t) {
            log.error("Cannot resolve IBatchDAO for metric class {} drain; pending L2 data will "
                + "be orphaned (accepted in structural window).", metricsClass.getName(), t);
            // Proceed with worker removal anyway — do not block the hot-remove on storage resolution.
            finalizeRemoval(moduleDefineHolder, metricsClass, modelName, victims);
            return true;
        }

        final List<PrepareRequest> pending = new ArrayList<>();
        for (final MetricsPersistentWorker w : victims) {
            try {
                pending.addAll(w.drainPendingRequests());
            } catch (final Throwable t) {
                log.error("L2 drain collect failed for model {} on worker {}; continuing",
                    modelName, w.getClass().getSimpleName(), t);
            }
        }
        if (!pending.isEmpty()) {
            try {
                batchDAO.flush(pending).join();
            } catch (final Throwable t) {
                log.error("L2 flush failed for metric class {}; {} pending requests orphaned "
                    + "(accepted structural-window loss).", metricsClass.getName(), pending.size(), t);
            }
        }

        finalizeRemoval(moduleDefineHolder, metricsClass, modelName, victims);
        return true;
    }

    /**
     * Reversible pause of streaming dispatch for a single metric class. Used by the runtime-rule
     * Suspend phase — the peer receives a cluster RPC telling it "stop serving this bundle while
     * the main node moves its schema" and calls through to this primitive.
     *
     * <p>Semantics:
     * <ul>
     *   <li>The entry worker is removed from {@link #entryWorkers} and parked in
     *       {@link #suspendedWorkers}. Samples arriving at {@link #in(Metrics)} for this class
     *       hit the null-worker path and increment {@link #unroutableSampleCount} — the accepted
     *       cost of the structural window.</li>
     *   <li>Persistent workers (L2) stay registered. Already-buffered L2 data continues flushing
     *       to storage on the normal timer, so no in-flight samples are lost.</li>
     *   <li>{@code StorageModels} registration is untouched — the measure stays, no DDL fires.</li>
     * </ul>
     *
     * <p>Reverse via {@link #resumeDispatch(Class)} — the parked worker is put back atomically,
     * retaining its {@code MergableBufferedData} state and {@code lastSendTime} across the pause.
     * Idempotent: calling suspend on an already-suspended class returns {@code false}.
     *
     * @return {@code true} if a live entry worker was parked, {@code false} if no entry worker
     *         was present (already suspended, or not registered at all).
     */
    public boolean suspendDispatch(final Class<? extends Metrics> metricsClass) {
        final MetricsAggregateWorker worker = entryWorkers.remove(metricsClass);
        if (worker == null) {
            return false;
        }
        suspendedWorkers.put(metricsClass, worker);
        return true;
    }

    /**
     * Inverse of {@link #suspendDispatch(Class)}: re-install the parked entry worker into
     * {@link #entryWorkers} so {@link #in(Metrics)} dispatches to it again. Idempotent: returns
     * {@code false} if nothing was parked for this class. Called by the runtime-rule apply path
     * on the SUSPENDED → RUNNING transition and by the Suspend-aborted rollback path when a
     * main-node verify failure rolls the peers back to the pre-suspend handler set.
     */
    public boolean resumeDispatch(final Class<? extends Metrics> metricsClass) {
        final MetricsAggregateWorker worker = suspendedWorkers.remove(metricsClass);
        if (worker == null) {
            return false;
        }
        entryWorkers.put(metricsClass, worker);
        return true;
    }

    /** Test/observability-only: whether a metric class is currently parked. */
    public boolean isDispatchSuspended(final Class<? extends Metrics> metricsClass) {
        return suspendedWorkers.containsKey(metricsClass);
    }

    private void finalizeRemoval(final ModuleDefineHolder moduleDefineHolder,
                                 final Class<? extends Metrics> metricsClass,
                                 final String modelName,
                                 final List<MetricsPersistentWorker> victims) {
        // Deregister L2 handler for the min worker, then drop all victims from the shared list.
        for (final MetricsPersistentWorker w : victims) {
            if (w instanceof MetricsPersistentMinWorker) {
                try {
                    ((MetricsPersistentMinWorker) w).deregisterFromL2Queue(metricsClass);
                } catch (final Throwable t) {
                    log.error("L2 queue deregister failed for metric class {}",
                        metricsClass.getName(), t);
                }
            }
        }
        persistentWorkers.removeAll(victims);

        // Drop the {modelName}_rec entry from WorkerInstancesService — the counterpart of the
        // put(...) call in create(). Without this the receiver-name slot stays occupied, and
        // a subsequent re-register of the same metric name (shape-break remove+apply,
        // operator recovery push via /addOrUpdate?force=true, reconciler STRUCTURAL path)
        // fails with "Duplicate worker
        // name". Idempotent — the remove silently ignores unknown keys.
        if (modelName != null) {
            try {
                final IWorkerInstanceSetter workerInstanceSetter = moduleDefineHolder
                    .find(CoreModule.NAME).provider().getService(IWorkerInstanceSetter.class);
                workerInstanceSetter.remove(modelName + "_rec");
            } catch (final Throwable t) {
                log.error("Failed to deregister worker-instance slot {}_rec for metric {}; "
                    + "a subsequent re-register of the same name will fail with \"Duplicate "
                    + "worker name\".", modelName, metricsClass.getName(), t);
            }
        }
    }
}
