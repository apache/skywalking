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

package org.apache.skywalking.oap.server.core.analysis.meter;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.NotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.SystemUtils;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.classloader.BytecodeClassDefiner;
import org.apache.skywalking.oap.server.core.analysis.StreamDefinition;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.meter.dynamic.MeterClassPackageHolder;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.MeterFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.ModelRegistry;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * MeterSystem provides the API way to create {@link MetricsStreamProcessor} rather than manual analysis metrics or OAL
 * script.
 *
 * @since 8.0.0
 */
@Slf4j
public class MeterSystem implements Service {
    private static final String METER_CLASS_PACKAGE = "org.apache.skywalking.oap.server.core.analysis.meter.dynamic.";
    private ModuleManager manager;
    private ClassPool classPool;
    private Map<String, Class<? extends AcceptableValue>> functionRegister = new HashMap<>();
    /**
     * Host the dynamic meter prototype classes. These classes could be create dynamically through {@link
     * Object#clone()} in the runtime;
     */
    private Map<String, MeterDefinition> meterPrototypes = new HashMap<>();

    public MeterSystem(final ModuleManager manager) {
        this.manager = manager;
        classPool = ClassPool.getDefault();

        ClassPath classpath = null;
        try {
            classpath = ClassPath.from(MeterSystem.class.getClassLoader());
        } catch (IOException e) {
            throw new UnexpectedException("Load class path failure.");
        }
        ImmutableSet<ClassPath.ClassInfo> classes = classpath.getTopLevelClassesRecursive("org.apache.skywalking");
        for (ClassPath.ClassInfo classInfo : classes) {
            Class<?> functionClass = classInfo.load();

            if (functionClass.isAnnotationPresent(MeterFunction.class)) {
                MeterFunction metricsFunction = functionClass.getAnnotation(MeterFunction.class);
                if (!AcceptableValue.class.isAssignableFrom(functionClass)) {
                    throw new IllegalArgumentException(
                        "Function " + functionClass.getCanonicalName() + " doesn't implement AcceptableValue.");
                }
                functionRegister.put(
                    metricsFunction.functionName(),
                    (Class<? extends AcceptableValue>) functionClass
                );
            }
        }
    }

    /**
     * Create streaming calculation of the given metrics name. This methods is synchronized due to heavy implementation
     * including creating dynamic class. Don't use this in concurrency runtime.
     *
     * @param metricsName  The name used as the storage eneity and in the query stage.
     * @param functionName The function provided through {@link MeterFunction}.
     * @throws IllegalArgumentException if the parameter can't match the expectation.
     * @throws UnexpectedException      if binary code manipulation fails or stream core failure.
     */
    public synchronized <T> void create(String metricsName,
                                        String functionName,
                                        ScopeType type) throws IllegalArgumentException {
        final Class<? extends AcceptableValue> meterFunction = functionRegister.get(functionName);

        if (meterFunction == null) {
            throw new IllegalArgumentException("Function " + functionName + " can't be found.");
        }
        Type acceptance = null;
        for (final Type genericInterface : meterFunction.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericInterface;
                if (parameterizedType.getRawType().getTypeName().equals(AcceptableValue.class.getName())) {
                    Type[] arguments = parameterizedType.getActualTypeArguments();
                    acceptance = arguments[0];
                    break;
                }
            }
        }
        try {
            create(metricsName, functionName, type, Class.forName(Objects.requireNonNull(acceptance).getTypeName()));
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Create streaming calculation of the given metrics name. This methods is synchronized due to heavy implementation
     * including creating dynamic class. Don't use this in concurrency runtime.
     *
     * @param metricsName  The name used as the storage eneity and in the query stage.
     * @param functionName The function provided through {@link MeterFunction}.
     * @throws IllegalArgumentException if the parameter can't match the expectation.
     * @throws UnexpectedException      if binary code manipulation fails or stream core failure.
     */
    public synchronized <T> void create(String metricsName,
                                        String functionName,
                                        ScopeType type,
                                        Class<T> dataType) throws IllegalArgumentException {
        // Static boot path: create-if-absent semantics so a backend that already holds this
        // metric under a different shape is preserved and reported, not silently reshaped.
        createInternal(metricsName, functionName, type, dataType, classPool, MeterClassPackageHolder.class,
            StorageManipulationOpt.schemaCreateIfAbsent());
    }

    /**
     * Runtime-rule overload at the 3-arg entry point: resolves {@code dataType} reflectively
     * from the function's {@link AcceptableValue} parameterization (same derivation as the
     * no-pool 3-arg overload at {@link #create(String, String, ScopeType)}) and threads the
     * caller-supplied per-file {@code ClassPool} + {@code ClassLoader} through so the
     * generated Metrics subclass is defined directly IN the runtime-rule loader (not in the
     * neighbor's loader). Lets the runtime-rule bundle drop every class it created together.
     */
    /**
     * Runtime-rule entry point: create a streaming calculation under a caller-supplied
     * per-file {@code ClassPool} + {@code ClassLoader}, with a caller-specified
     * {@link StorageManipulationOpt} policy. Main-node apply passes
     * {@link StorageManipulationOpt#withSchemaChange()} (the usual install path); peer-node apply
     * passes {@link StorageManipulationOpt#withoutSchemaChange()} so local state is populated
     * (MeterSystem meterPrototypes, BanyanDB MetadataRegistry, StorageModels entry) without
     * firing server-side {@code createMeasure} / {@code update}.
     */
    public synchronized void create(String metricsName,
                                    String functionName,
                                    ScopeType type,
                                    ClassPool pool,
                                    ClassLoader targetClassLoader,
                                    StorageManipulationOpt opt) throws IllegalArgumentException {
        final Class<? extends AcceptableValue> meterFunction = functionRegister.get(functionName);
        if (meterFunction == null) {
            throw new IllegalArgumentException("Function " + functionName + " can't be found.");
        }
        Type acceptance = null;
        for (final Type genericInterface : meterFunction.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericInterface;
                if (parameterizedType.getRawType().getTypeName().equals(AcceptableValue.class.getName())) {
                    Type[] arguments = parameterizedType.getActualTypeArguments();
                    acceptance = arguments[0];
                    break;
                }
            }
        }
        try {
            createInternal(metricsName, functionName, type,
                Class.forName(Objects.requireNonNull(acceptance).getTypeName()),
                pool, targetClassLoader, opt);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private <T> void createInternal(final String metricsName,
                                    final String functionName,
                                    final ScopeType type,
                                    final Class<T> dataType,
                                    final ClassPool pool,
                                    final ClassLoader targetClassLoader,
                                    final StorageManipulationOpt opt) throws IllegalArgumentException {
        final Class<? extends AcceptableValue> meterFunction = functionRegister.get(functionName);
        if (meterFunction == null) {
            throw new IllegalArgumentException("Function " + functionName + " can't be found.");
        }
        boolean foundDataType = false;
        String acceptance = null;
        for (final Type genericInterface : meterFunction.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericInterface;
                if (parameterizedType.getRawType().getTypeName().equals(AcceptableValue.class.getName())) {
                    Type[] arguments = parameterizedType.getActualTypeArguments();
                    if (arguments[0].equals(dataType)) {
                        foundDataType = true;
                    } else {
                        acceptance = arguments[0].getTypeName();
                    }
                }
                if (foundDataType) {
                    break;
                }
            }
        }
        if (!foundDataType) {
            throw new IllegalArgumentException("Function " + functionName
                + " requires <" + acceptance + "> in AcceptableValue"
                + " but using " + dataType.getName() + " in the creation");
        }
        final CtClass parentClass;
        try {
            parentClass = pool.get(meterFunction.getCanonicalName());
            if (!Metrics.class.isAssignableFrom(meterFunction)) {
                throw new IllegalArgumentException(
                    "Function " + functionName + " doesn't inherit from Metrics.");
            }
        } catch (NotFoundException e) {
            throw new IllegalArgumentException("Function " + functionName + " can't be found by javaassist.");
        }
        final String className = formatName(metricsName);
        // Prototype-first short-circuit (fires on runtime FILTER_ONLY re-apply). Every
        // runtime apply hands in a fresh {@code ClassPool}, so the pool-based existence
        // check below cannot see a Metrics class the previous apply defined in a now-dead
        // pool. Without this guard, every FILTER_ONLY update generated a new Metrics class,
        // new MetricsStreamProcessor workers, and a new prototype that shadowed the old
        // one in {@link #meterPrototypes} — a removeMetric by name could only tear down the
        // latest generation, leaving prior workers + classloaders pinned forever. Match on
        // scope + data type + function class; any of those differing is a genuine shape
        // change and the existing IllegalArgumentException on the pool path fires below.
        final MeterDefinition existingDefinition = meterPrototypes.get(metricsName);
        if (existingDefinition != null
            && existingDefinition.getScopeType() == type
            && existingDefinition.getDataType().equals(dataType)
            && existingDefinition.getMeterPrototype().getClass().getSuperclass() == meterFunction) {
            log.debug("Metric {} already registered with matching shape; reusing existing "
                + "Metrics class + workers (FILTER_ONLY re-apply path).", metricsName);
            return;
        }
        try {
            CtClass existingMetric = pool.get(METER_CLASS_PACKAGE + className);
            if (existingMetric.getSuperclass() != parentClass
                || type != meterPrototypes.get(metricsName).getScopeType()) {
                throw new IllegalArgumentException(
                    metricsName + " has been defined, but calculate function or/are scope type is/are different.");
            }
            log.info("Metric {} is already defined, so skip the metric creation.", metricsName);
            return;
        } catch (NotFoundException ignored) {
            // proceed — class not yet defined in this pool
        }
        CtClass metricsClass = pool.makeClass(METER_CLASS_PACKAGE + className, parentClass);
        try {
            metricsClass.addConstructor(CtNewConstructor.make("public " + className + "() {}", metricsClass));
            metricsClass.addMethod(CtNewMethod.make(
                "public org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue createNew() {"
                    + "    org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue meterVar = new " + METER_CLASS_PACKAGE + className + "();"
                    + "    ((org.apache.skywalking.oap.server.core.analysis.meter.Meter)meterVar).initMeta(\"" + metricsName + "\", " + type.getScopeId() + ");"
                    + "    return meterVar;"
                    + "}",
                metricsClass));
        } catch (CannotCompileException e) {
            throw new UnexpectedException(e.getMessage(), e);
        }
        final Class targetClass;
        try {
            // Explicit targetClassLoader — the generated class goes directly into the
            // per-file RuleClassLoader, not the neighbor class's loader. Two paths:
            //
            //   - {@link BytecodeClassDefiner} loaders (the runtime-rule {@code
            //     RuleClassLoader} is the only known implementor today): hand the loader
            //     the raw bytecode via its public {@code defineClass(String, byte[])}.
            //     This sidesteps Javassist's deprecated 2-arg {@code toClass(loader,
            //     ProtectionDomain)} which reflects into {@code java.lang.ClassLoader.
            //     defineClass} and requires {@code --add-opens java.base/java.lang} on
            //     JDK 17+ — a JVM-flag tax we don't want to put on every operator.
            //
            //   - Other loaders (legacy callers): keep the 2-arg toClass for back-compat.
            //     No new constraints on existing static rule paths; they don't use this
            //     overload anyway, so this branch is effectively dead today and exists as
            //     a safety net.
            if (targetClassLoader instanceof BytecodeClassDefiner) {
                targetClass = ((BytecodeClassDefiner) targetClassLoader)
                    .defineClass(METER_CLASS_PACKAGE + className, metricsClass.toBytecode());
            } else {
                targetClass = metricsClass.toClass(targetClassLoader, null);
            }
            AcceptableValue prototype = (AcceptableValue) targetClass.newInstance();
            meterPrototypes.put(metricsName, new MeterDefinition(type, prototype, dataType, true));
            MetricsStreamProcessor.getInstance().create(
                manager,
                new StreamDefinition(
                    metricsName, type.getScopeId(), prototype.builder(), MetricsStreamProcessor.class),
                targetClass,
                opt);
            // Roll back the prototype if the installer refused to reshape the backend
            // (SKIPPED_SHAPE_MISMATCH recorded on opt). Leaving the prototype in
            // meterPrototypes would mean dispatch lookups succeed for a metric whose
            // storage workers MetricsStreamProcessor refused to register — samples would
            // fail silently later rather than at registration time. Boot continues with
            // this metric inactive; operator reshapes explicitly via the runtime-rule
            // on-demand endpoint.
            if (opt.hasShapeMismatch()) {
                meterPrototypes.remove(metricsName);
            }
        } catch (CannotCompileException | IllegalAccessException | InstantiationException
                 | StorageException | IOException e) {
            // Also roll back on exception paths — an unsuccessful create must not leave a
            // prototype stranded in the registry. {@link IOException} surfaces from
            // {@code metricsClass.toBytecode()} when serialising the generated bytes; treat
            // it the same as a Javassist compile failure so the apply rolls back cleanly.
            meterPrototypes.remove(metricsName);
            throw new UnexpectedException(e.getMessage(), e);
        }
    }

    /**
     * Runtime-rule overload: create a streaming calculation whose dynamically-generated
     * {@code Metrics} subclass is made in the caller-supplied {@code ClassPool} and loaded through
     * the caller-supplied {@code classLoaderNeighbor} (a class already loaded by the target
     * per-file {@code RuleClassLoader}).
     *
     * <p>Used by MAL/LAL hot-update so all of a rule file's generated classes — the
     * {@code MalExpression} / {@code LalExpression} + closure companions produced by the DSL
     * generators AND the {@code Metrics} subclass produced here — share one classloader and can
     * all be dropped together for GC on hot-remove. Without this overload, the Metrics class
     * would remain pinned in the default pool and the default loader, blocking shape-breaking
     * re-registration and leaking classes across churn.
     *
     * <p>Startup path is unchanged — the existing overloads continue to use the instance-field
     * default pool and {@link MeterClassPackageHolder} as the loader neighbor.
     *
     * @param metricsName          storage entity name
     * @param functionName         function provided through {@link MeterFunction}
     * @param type                 scope type
     * @param dataType             accepted value data type
     * @param pool                 per-file Javassist pool, typically constructed as
     *                             {@code new ClassPool(ClassPool.getDefault())} with
     *                             {@code LoaderClassPath(ruleLoader)} appended
     * @param classLoaderNeighbor  a class loaded by the per-file {@code RuleClassLoader}; used
     *                             by Javassist's {@code toClass(Class)} on Java 9+ to resolve
     *                             the target loader. On Java 8, its classloader is passed to
     *                             the legacy {@code toClass(ClassLoader, ProtectionDomain)}
     */
    public synchronized <T> void create(String metricsName,
                                        String functionName,
                                        ScopeType type,
                                        Class<T> dataType,
                                        ClassPool pool,
                                        Class<?> classLoaderNeighbor) throws IllegalArgumentException {
        if (pool == null) {
            throw new IllegalArgumentException("pool must not be null");
        }
        if (classLoaderNeighbor == null) {
            throw new IllegalArgumentException("classLoaderNeighbor must not be null");
        }
        createInternal(metricsName, functionName, type, dataType, pool, classLoaderNeighbor,
            StorageManipulationOpt.withSchemaChange());
    }

    /**
     * Remove a previously-registered metric by name. Symmetric to {@link #create(String, String,
     * ScopeType, Class)} / the pool-aware overload. Used by runtime rule hot-remove (MAL/LAL)
     * to retire a metric class cleanly.
     *
     * <p>Steps:
     * <ol>
     *   <li>Drops the {@link #meterPrototypes} entry so {@link #buildMetrics(String, Class)}
     *       rejects further builds for this name.</li>
     *   <li>Delegates to {@link MetricsStreamProcessor#removeMetric} — L1/L2 drain, worker
     *       deregistration, shared-queue handler removal.</li>
     *   <li>Cascades through {@link ModelRegistry#remove(Class, StorageManipulationOpt)} to drop every downsampling
     *       variant's {@code Model} from the registry; listener {@code whenRemoving} fires for
     *       each (BanyanDB drops the measure, JDBC/ES no-op).</li>
     *   <li>Detaches the {@link CtClass} from the default {@link #classPool} so a later
     *       shape-breaking re-create (e.g. {@code sum}→{@code histogram}) passes the pre-check at
     *       {@link #create(String, String, ScopeType, Class)} rather than failing with "already
     *       defined... calculate function or/are scope type is/are different". For runtime-path
     *       metrics the CtClass lives in a per-file pool owned by the bundle; dropping the
     *       bundle's pool reference collects the CtClass automatically, so the default-pool
     *       detach is a best-effort no-op in that case ({@link NotFoundException} is expected).</li>
     * </ol>
     *
     * <p>Not safe to call concurrently with {@link #create(String, String, ScopeType, Class)} or
     * another {@link #removeMetric} — the MeterSystem monitor serializes them. Callers must hold
     * no other lock that could invert with the MeterSystem monitor. The runtime-rule
     * module's per-file lock is always acquired before this monitor, never after.
     *
     * @param metricsName the metric name to retire
     * @return {@code true} if a metric was found and removed, {@code false} otherwise
     */
    public synchronized boolean removeMetric(final String metricsName) {
        return removeMetric(metricsName, StorageManipulationOpt.withSchemaChange());
    }

    /**
     * Opt-aware {@code removeMetric}. Runtime-rule peer-side callers pass
     * {@link StorageManipulationOpt#withoutSchemaChange()} so {@code ModelInstaller.dropTable} is
     * NOT invoked on the shared storage — the cluster main owns that side-effect.
     *
     * <p>Order is backend-first / local-state-second so failure is retriable. The earlier
     * version evicted {@code meterPrototypes} and called the cascade in parallel; if the
     * cascade threw (BanyanDB {@code dropTable} failure), the local state was already torn
     * down — there was nothing for the next {@code /inactivate} or reconciler tick to drive
     * a backend retry against, and the operator could not recover the orphaned measure
     * without an OAP restart. Now the cascade runs first; on success we drop the local
     * caches; on failure we leave {@code meterPrototypes} populated and the CtClass attached
     * so a retry hits the backend again.
     *
     * <p>Failure surface: under {@code withSchemaChange} the storage-model cascade failure is
     * propagated as a {@link RuntimeException}. The REST {@code /inactivate} path depends on
     * this to surface 500 {@code teardown_deferred} when BanyanDB's delete-measure threw —
     * without it the handler would return 200 inactivated despite the measure still being
     * live. Under {@code withoutSchemaChange} the cascade fires {@code whenRemoving} but the
     * peer's {@code ModelInstaller.dropTable} is suppressed by policy, so any throw is
     * logged and swallowed — the peer has no backend debt. Streaming-chain drain failures
     * are always logged and swallowed: stale workers self-drain within one tick.
     */
    public synchronized boolean removeMetric(final String metricsName, final StorageManipulationOpt opt) {
        final MeterDefinition def = meterPrototypes.get(metricsName);
        if (def == null) {
            return false;
        }
        final Class<?> prototypeClass = def.getMeterPrototype().getClass();

        // Cascade storage-model removal (Hour / Day / Minute) FIRST. ModelRegistry.remove
        // fires whenRemoving on every listener, so each backend's ModelInstaller.dropTable
        // runs — real delete for BanyanDB, no-op for JDBC / Elasticsearch, skipped outright
        // when the caller is a peer-side (WITHOUT_SCHEMA_CHANGE) apply. If a listener throws,
        // ModelRegistry.remove keeps the model in its registry so this retry path stays
        // open: the caller (Reconciler unregisterBundle) preserves appliedMal[key] and the
        // next tick (or operator retry) re-enters this method, finds meterPrototypes still
        // populated, and re-fires the cascade.
        try {
            final ModelRegistry modelCreator = manager.find(CoreModule.NAME)
                                                     .provider()
                                                     .getService(ModelRegistry.class);
            modelCreator.remove(prototypeClass, opt);
        } catch (final Throwable t) {
            log.error("Failed to cascade storage-model removal for metric {}", metricsName, t);
            if (opt.getFlags().isEscalateToCaller()) {
                throw new RuntimeException(
                    "Storage-model cascade failed for metric " + metricsName
                        + "; backend drop did not complete. Local state preserved for retry.",
                    t);
            }
            // Non-escalating opt (peer-side withoutSchemaChange, etc.) — backend drop is
            // suppressed by policy anyway, so a listener throw here is the listener's
            // local bookkeeping misbehaving, not real backend debt. Fall through to
            // clear local state.
        }

        // Backend cascade succeeded (or local-cache-only, where it doesn't matter). Drop
        // the prototype and drain the workers. Worker drain failure is non-fatal and
        // logged: stale workers self-drain within one tick, and the prototype + Model are
        // already gone so future samples can't reach them.
        meterPrototypes.remove(metricsName);
        try {
            MetricsStreamProcessor.getInstance().removeMetric(manager, (Class) prototypeClass);
        } catch (final Throwable t) {
            log.error("Failed to remove streaming chain for metric {}; prototype + storage "
                + "model already gone.", metricsName, t);
        }

        // Detach the CtClass from the default pool so a future shape-breaking re-create passes
        // the pre-check at the head of createInternal. Static-path metrics own a CtClass in
        // the instance default pool and must be detached explicitly; runtime-path metrics
        // live in a per-file pool that goes away with the bundle, so the detach here is
        // unnecessary. The flag is authoritative — do not reach for the default pool and
        // swallow NotFoundException as a substitute.
        if (!def.isRuntimeManaged()) {
            try {
                final CtClass staleCtClass = classPool.get(METER_CLASS_PACKAGE + formatName(metricsName));
                staleCtClass.detach();
            } catch (final NotFoundException e) {
                log.warn("removeMetric({}): static-path metric was expected in default pool but "
                    + "was not present; shape-break re-registration may fail the pre-check. "
                    + "This indicates the metric was registered through an unexpected path.",
                    metricsName);
            }
        }
        return true;
    }

    /**
     * Reversible pause of streaming dispatch for a set of metric names. Used by the
     * runtime-rule Suspend phase: the receiving OAP node instructs peers (and itself, for
     * local consistency) to stop serving a bundle while the main node applies the structural
     * DDL + verify. Peers resume via {@link #resumeDispatch} once the row is upserted with
     * the new content.
     *
     * <p>Delegates to {@link MetricsStreamProcessor#suspendDispatch(Class)} per metric. The
     * measure, persistent workers, and storage-model registration stay live — only the entry
     * dispatch is parked. Idempotent: names not registered or already suspended are skipped.
     *
     * @return count of metrics that actually transitioned into the suspended state.
     */
    public synchronized int suspendDispatch(final Set<String> metricsNames) {
        if (metricsNames == null || metricsNames.isEmpty()) {
            return 0;
        }
        int suspended = 0;
        final MetricsStreamProcessor processor = MetricsStreamProcessor.getInstance();
        for (final String name : metricsNames) {
            final MeterDefinition def = meterPrototypes.get(name);
            if (def == null) {
                continue;
            }
            final Class<?> prototypeClass = def.getMeterPrototype().getClass();
            try {
                if (processor.suspendDispatch((Class) prototypeClass)) {
                    suspended++;
                }
            } catch (final Throwable t) {
                log.warn("suspendDispatch failed for metric {}; continuing with the rest.", name, t);
            }
        }
        return suspended;
    }

    /**
     * Inverse of {@link #suspendDispatch}: re-installs the parked entry workers so samples
     * dispatch again. Idempotent; names not currently parked are skipped.
     *
     * @return count of metrics that actually transitioned back to live dispatch.
     */
    public synchronized int resumeDispatch(final Set<String> metricsNames) {
        if (metricsNames == null || metricsNames.isEmpty()) {
            return 0;
        }
        int resumed = 0;
        final MetricsStreamProcessor processor = MetricsStreamProcessor.getInstance();
        for (final String name : metricsNames) {
            final MeterDefinition def = meterPrototypes.get(name);
            if (def == null) {
                continue;
            }
            final Class<?> prototypeClass = def.getMeterPrototype().getClass();
            try {
                if (processor.resumeDispatch((Class) prototypeClass)) {
                    resumed++;
                }
            } catch (final Throwable t) {
                log.warn("resumeDispatch failed for metric {}; continuing with the rest.", name, t);
            }
        }
        return resumed;
    }

    private <T> void createInternal(final String metricsName,
                                    final String functionName,
                                    final ScopeType type,
                                    final Class<T> dataType,
                                    final ClassPool pool,
                                    final Class<?> classLoaderNeighbor,
                                    final StorageManipulationOpt opt) throws IllegalArgumentException {
        /*
         * Create a new meter class dynamically.
         */
        final Class<? extends AcceptableValue> meterFunction = functionRegister.get(functionName);

        if (meterFunction == null) {
            throw new IllegalArgumentException("Function " + functionName + " can't be found.");
        }

        boolean foundDataType = false;
        String acceptance = null;
        for (final Type genericInterface : meterFunction.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericInterface;
                if (parameterizedType.getRawType().getTypeName().equals(AcceptableValue.class.getName())) {
                    Type[] arguments = parameterizedType.getActualTypeArguments();
                    if (arguments[0].equals(dataType)) {
                        foundDataType = true;
                    } else {
                        acceptance = arguments[0].getTypeName();
                    }
                }
                if (foundDataType) {
                    break;
                }
            }
        }
        if (!foundDataType) {
            throw new IllegalArgumentException("Function " + functionName
                                                   + " requires <" + acceptance + "> in AcceptableValue"
                                                   + " but using " + dataType.getName() + " in the creation");
        }

        final CtClass parentClass;
        try {
            parentClass = pool.get(meterFunction.getCanonicalName());
            if (!Metrics.class.isAssignableFrom(meterFunction)) {
                throw new IllegalArgumentException(
                    "Function " + functionName + " doesn't inherit from Metrics.");
            }
        } catch (NotFoundException e) {
            throw new IllegalArgumentException("Function " + functionName + " can't be found by javaassist.");
        }
        final String className = formatName(metricsName);

        /*
         * Prototype-first short-circuit for runtime FILTER_ONLY re-apply — see the same
         * guard in the default-pool {@code createInternal} above for the full rationale. The
         * pool-based check below can't detect an existing registration because every
         * runtime apply hands in a fresh pool; without this, each FILTER_ONLY iteration
         * leaks a new Metrics class + a new worker chain + a new classloader.
         */
        final MeterDefinition existingDefinition = meterPrototypes.get(metricsName);
        if (existingDefinition != null
            && existingDefinition.getScopeType() == type
            && existingDefinition.getDataType().equals(dataType)
            && existingDefinition.getMeterPrototype().getClass().getSuperclass() == meterFunction) {
            log.debug("Metric {} already registered with matching shape; reusing existing "
                + "Metrics class + workers (FILTER_ONLY re-apply path).", metricsName);
            return;
        }

        /*
         * Check whether the metrics class is already defined or not
         */
        try {
            CtClass existingMetric = pool.get(METER_CLASS_PACKAGE + className);
            if (existingMetric.getSuperclass() != parentClass || type != meterPrototypes.get(metricsName)
                                                                                        .getScopeType()) {
                throw new IllegalArgumentException(
                    metricsName + " has been defined, but calculate function or/are scope type is/are different.");
            }
            log.info("Metric {} is already defined, so skip the metric creation.", metricsName);
            return;
        } catch (NotFoundException e) {
            // proceed — class not yet defined in this pool
        }

        CtClass metricsClass = pool.makeClass(METER_CLASS_PACKAGE + className, parentClass);

        /*
         * Create empty construct
         */
        try {
            CtConstructor defaultConstructor = CtNewConstructor.make(
                "public " + className + "() {}", metricsClass);
            metricsClass.addConstructor(defaultConstructor);
        } catch (CannotCompileException e) {
            log.error("Can't add empty constructor in " + className + ".", e);
            throw new UnexpectedException(e.getMessage(), e);
        }

        /*
         * Generate `AcceptableValue<T> createNew()` method.
         */
        try {
            metricsClass.addMethod(CtNewMethod.make(
                ""
                    + "public org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue createNew() {"
                    + "    org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue meterVar = new " + METER_CLASS_PACKAGE + className + "();"
                    + "    ((org.apache.skywalking.oap.server.core.analysis.meter.Meter)meterVar).initMeta(\"" + metricsName + "\", " + type.getScopeId() + ");"
                    + "    return meterVar;"
                    + " }"
                , metricsClass));
        } catch (CannotCompileException e) {
            log.error("Can't generate createNew method for " + className + ".", e);
            throw new UnexpectedException(e.getMessage(), e);
        }

        Class targetClass;
        try {
            if (SystemUtils.isJavaVersionAtMost(JavaVersion.JAVA_1_8)) {
                targetClass = metricsClass.toClass(classLoaderNeighbor.getClassLoader(), null);
            } else {
                targetClass = metricsClass.toClass(classLoaderNeighbor);
            }
            AcceptableValue prototype = (AcceptableValue) targetClass.newInstance();
            meterPrototypes.put(metricsName, new MeterDefinition(type, prototype, dataType, false));

            log.debug("Generate metrics class, " + metricsClass.getName());

            MetricsStreamProcessor.getInstance().create(
                manager,
                new StreamDefinition(
                    metricsName, type.getScopeId(), prototype.builder(), MetricsStreamProcessor.class),
                targetClass,
                opt
            );
            // Same shape-mismatch guard as the static-catalog createInternal path. Under
            // full-install mode the opt shouldn't ever carry a SKIPPED_SHAPE_MISMATCH
            // outcome (the installer reshapes), but defensively roll the prototype back if
            // it does — a dispatch lookup against a prototype whose workers never came up
            // produces confusing silent-drop failures later.
            if (opt.hasShapeMismatch()) {
                meterPrototypes.remove(metricsName);
            }
        } catch (CannotCompileException | IllegalAccessException | InstantiationException | StorageException e) {
            log.error("Can't compile/load/init " + className + ".", e);
            meterPrototypes.remove(metricsName);
            throw new UnexpectedException(e.getMessage(), e);
        }
    }

    /**
     * Create an {@link AcceptableValue} instance for streaming calculation. AcceptableValue instance is stateful,
     * shouldn't do {@link AcceptableValue#accept(MeterEntity, Object)} once it is pushed into {@link
     * #doStreamingCalculation(AcceptableValue)}.
     *
     * @param metricsName A defined metrics name. Use {@link #create(String, String, ScopeType, Class)} to define a new
     *                    one.
     * @param dataType    class type of the input of {@link AcceptableValue}
     * @return usable an {@link AcceptableValue} instance.
     */
    public <T> AcceptableValue<T> buildMetrics(String metricsName,
                                               Class<T> dataType) {
        MeterDefinition meterDefinition = meterPrototypes.get(metricsName);
        if (meterDefinition == null) {
            throw new IllegalArgumentException("Uncreated metrics " + metricsName);
        }
        if (!meterDefinition.getDataType().equals(dataType)) {
            throw new IllegalArgumentException(
                "Unmatched metrics data type, request for " + dataType.getName()
                    + ", but defined as " + meterDefinition.getDataType());
        }

        return meterDefinition.getMeterPrototype().createNew();
    }

    /**
     * Active the {@link MetricsStreamProcessor#in(Metrics)} for streaming calculation.
     *
     * @param acceptableValue should only be created through {@link #create(String, String, ScopeType, Class)}
     */
    public void doStreamingCalculation(AcceptableValue acceptableValue) {
        final long timeBucket = acceptableValue.getTimeBucket();
        if (timeBucket == 0L) {
            // Avoid no timestamp data, which could be harmful for the storage.
            acceptableValue.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        }
        MetricsStreamProcessor.getInstance().in((Metrics) acceptableValue);
    }

    private static String formatName(String metricsName) {
        return metricsName.toLowerCase();
    }

    @RequiredArgsConstructor
    @Getter
    private static class MeterDefinition {
        private final ScopeType scopeType;
        private final AcceptableValue meterPrototype;
        private final Class<?> dataType;
        /**
         * {@code true} when the generated {@code Metrics} class lives in a caller-supplied
         * per-file {@code ClassPool}/{@code ClassLoader} (runtime-rule hot-update path —
         * {@link #create(String, String, ScopeType, Class, ClassPool, Class, StorageManipulationOpt)}).
         * {@code false} when the class lives in the instance {@link #classPool} (static boot
         * path). Read by {@link #removeMetric} to decide whether a default-pool {@code CtClass}
         * detach is required (static path) or a no-op (runtime path — the per-file pool
         * goes away with the bundle, taking the CtClass with it).
         */
        private final boolean runtimeManaged;
    }
}
