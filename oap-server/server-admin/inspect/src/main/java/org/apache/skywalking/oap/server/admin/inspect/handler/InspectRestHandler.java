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

package org.apache.skywalking.oap.server.admin.inspect.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.annotation.Blocking;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import com.linecorp.armeria.server.annotation.Post;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.query.graphql.mqe.rt.MQEExecutor;
import org.apache.skywalking.oap.server.admin.inspect.decoder.EntityDecoder;
import org.apache.skywalking.oap.server.admin.inspect.request.InspectValuesRequest;
import org.apache.skywalking.oap.server.admin.inspect.response.EntitiesResponse;
import org.apache.skywalking.oap.server.admin.inspect.response.EntityRow;
import org.apache.skywalking.oap.server.admin.inspect.response.ErrorResponse;
import org.apache.skywalking.oap.server.admin.inspect.response.MetricRow;
import org.apache.skywalking.oap.server.admin.inspect.response.MetricsResponse;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.query.MetadataQueryService;
import org.apache.skywalking.oap.server.core.query.MetricsMetadataQueryService;
import org.apache.skywalking.oap.server.core.query.enumeration.MetricsType;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.mqe.ExpressionResult;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ForeignMetricMeta;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.core.storage.model.IModelManager;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * REST handler for the inspect API.
 *
 * <p>{@code GET /inspect/metrics} surfaces the metric catalog (filtered to
 * remove internal NOT_VALUE columns and {@link Scope#All} entries).
 * {@code GET /inspect/entities} enumerates entities holding values for a
 * metric in a time range, decoded into MQE-ready form.
 */
@Slf4j
public class InspectRestHandler {

    private static final int LIMIT_DEFAULT = 300;
    private static final int LIMIT_MAX = 300;
    /** Value types a caller may declare for a foreign (locally-undefined) metric. */
    private static final Set<String> ACCEPTED_FOREIGN_VALUE_TYPES =
        Set.of("LONG", "INT", "DOUBLE", "LABELED");
    /** A value column is interpolated into JDBC SQL on the read path; restrict it to a bare identifier. */
    private static final Pattern VALUE_COLUMN_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final ObjectMapper VALUES_MAPPER = new ObjectMapper();

    private final ModuleManager moduleManager;

    public InspectRestHandler(final ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    @Get("/inspect/metrics")
    public HttpResponse listMetrics(@Param("regex") final Optional<String> regex,
                                    @Param("type") final Optional<List<String>> types,
                                    @Param("catalog") final Optional<List<String>> catalogs,
                                    @Param("mqeQueryable") final Optional<Boolean> mqeQueryableOpt) {
        final Pattern namePattern;
        try {
            namePattern = regex.map(Pattern::compile).orElse(null);
        } catch (final PatternSyntaxException e) {
            // Bad operator input: surface as the inspect ErrorResponse shape (400),
            // matching every other client-input validation in this handler. Without
            // this catch the framework would render a generic 500.
            return error(HttpStatus.BAD_REQUEST,
                "regex is not a valid Java regular expression: " + e.getDescription());
        }
        final Set<String> typeFilter = toSet(types);
        final Set<String> catalogFilter = toSet(catalogs);
        final boolean mqeQueryable = mqeQueryableOpt.orElse(false);

        // Group all registered Models by metric name → set of downsamplings. One pass.
        final Map<String, Set<DownSampling>> downsamplingsByMetric =
            modelManager().allModels().stream()
                .collect(Collectors.groupingBy(
                    Model::getName,
                    Collectors.mapping(Model::getDownsampling, Collectors.toCollection(HashSet::new))));

        final List<MetricRow> rows = new ArrayList<>();
        for (final Map.Entry<String, ValueColumnMetadata.ValueColumn> entry
            : ValueColumnMetadata.INSTANCE.getAllMetadata().entrySet()) {
            final String name = entry.getKey();
            final ValueColumnMetadata.ValueColumn vc = entry.getValue();

            // NOT_VALUE: persisted-but-not-queryable internal columns (topology lines,
            // service entries) per Column.java:93-97. Never appears in inspect.
            if (vc.getDataType() == Column.ValueDataType.NOT_VALUE) {
                continue;
            }

            final Scope scope = Scope.Finder.valueOf(vc.getScopeId());
            // Scope.All is deprecated and not routable through MQE; skip silently.
            if (scope == Scope.All) {
                continue;
            }

            if (namePattern != null && !namePattern.matcher(name).matches()) {
                continue;
            }

            final MetricsType type = MetricsMetadataQueryService.typeOfMetrics(name);
            final String typeName = type.name();
            if (!typeFilter.isEmpty() && !typeFilter.contains(typeName)) {
                continue;
            }
            // `mqeQueryable=true` is a strict alias for "row would be accepted by
            // /inspect/entities". Filter MUST mirror that handler's reject set exactly:
            // type ∈ {REGULAR_VALUE, LABELED_VALUE} AND scope ∈ {Service, ServiceInstance,
            // Endpoint, ServiceRelation, ServiceInstanceRelation, EndpointRelation}.
            // Without the scope check, a Process-scope REGULAR_VALUE (e.g., a continuous-
            // profiling MAL metric) would survive this filter and then 400 at the entities
            // endpoint.
            if (mqeQueryable
                && (!isMqeDispatchableType(type) || !isSupportedScope(scope))) {
                continue;
            }

            final String catalog = DefaultScopeDefine.catalogOf(vc.getScopeId());
            if (!catalogFilter.isEmpty() && !catalogFilter.contains(catalog)) {
                continue;
            }

            final List<String> downs = sortedDownsamplingNames(
                downsamplingsByMetric.getOrDefault(name, Collections.emptySet()));

            rows.add(new MetricRow(
                name, typeName, catalog, vc.getScopeId(), scope.name(),
                vc.getValueCName(), downs));
        }
        return HttpResponse.ofJson(MediaType.JSON_UTF_8, new MetricsResponse(rows));
    }

    /**
     * Enumerate the entities holding values for a metric in a time range.
     *
     * <p>For a metric defined on this OAP, only {@code metric} + time params are needed; metadata
     * is read from the local registry and the response carries exact field names, scope, and a
     * re-queryable {@code mqeEntity}.
     *
     * <p>For a metric persisted by ANOTHER OAP that this node does not define (no local registry
     * entry, no OAL/MAL text to recover it from), the caller MUST also supply the metric's storage
     * metadata, which cannot be inferred from the name:
     * <ul>
     *   <li>{@code valueColumn} — the metric's value column, a property of its aggregation FUNCTION:
     *       one of {@code value} (common scalar), {@code double_value}, {@code int_value},
     *       {@code percentage}, {@code datatable_value} (labeled), {@code dataset} (histogram). On
     *       MySQL / PostgreSQL pass the reserved-word-overridden physical name ({@code value_}).</li>
     *   <li>{@code valueType} — how to read/decode the value: {@code LONG} / {@code INT} /
     *       {@code DOUBLE} (scalar) or {@code LABELED} (DataTable). HISTOGRAM/heatmap and
     *       SAMPLED_RECORD are out of scope for this endpoint.</li>
     * </ul>
     */
    @Get("/inspect/entities")
    public HttpResponse listEntities(@Param("metric") final String metric,
                                     @Param("start") final String start,
                                     @Param("end") final String end,
                                     @Param("step") final String stepStr,
                                     @Param("limit") final Optional<Integer> limitOpt,
                                     @Param("valueColumn") final Optional<String> valueColumnOpt,
                                     @Param("valueType") final Optional<String> valueTypeOpt) {
        // Resolve metadata.
        final Optional<ValueColumnMetadata.ValueColumn> vcOpt =
            ValueColumnMetadata.INSTANCE.readValueColumnDefinition(metric);
        if (vcOpt.isEmpty()) {
            // Foreign metric: not defined on this OAP. There is no OAL/MAL text or local model to
            // recover its value column / type / scope from, so the caller must supply them. Without
            // both, fall back to the original "unknown metric" rejection.
            if (valueColumnOpt.isEmpty() || valueTypeOpt.isEmpty()) {
                return error(HttpStatus.BAD_REQUEST,
                    "metric unknown locally: " + metric + " — provide valueColumn and valueType to "
                        + "inspect a metric persisted by another OAP");
            }
            return listForeignEntities(metric, valueColumnOpt.get(), valueTypeOpt.get(),
                start, end, stepStr, limitOpt);
        }
        final ValueColumnMetadata.ValueColumn vc = vcOpt.get();

        if (vc.getDataType() == Column.ValueDataType.NOT_VALUE) {
            return error(HttpStatus.BAD_REQUEST, "unknown metric: " + metric);
        }

        final MetricsType type = MetricsMetadataQueryService.typeOfMetrics(metric);
        if (type == MetricsType.HEATMAP) {
            return error(HttpStatus.BAD_REQUEST,
                "metric type HEATMAP is not MQE-queryable; /inspect/entities only "
                    + "accepts REGULAR_VALUE and LABELED_VALUE");
        }
        if (type == MetricsType.SAMPLED_RECORD) {
            return error(HttpStatus.BAD_REQUEST,
                "metric type SAMPLED_RECORD is out of scope for /inspect/entities");
        }

        final Scope scope = Scope.Finder.valueOf(vc.getScopeId());
        if (!isSupportedScope(scope)) {
            if (scope == Scope.Process || scope == Scope.ProcessRelation) {
                return error(HttpStatus.BAD_REQUEST, "process scope is out of scope");
            }
            return error(HttpStatus.BAD_REQUEST, "scope " + scope + " is out of scope");
        }

        // Validate step. SECOND is rejected explicitly even though Step.valueOf("SECOND")
        // would parse, because second-precision is not in the inspect API contract — the
        // docs and error message advertise MINUTE / HOUR / DAY only.
        final Step step;
        try {
            step = Step.valueOf(stepStr.toUpperCase());
        } catch (Exception e) {
            return error(HttpStatus.BAD_REQUEST,
                "step must be one of MINUTE / HOUR / DAY (got " + stepStr + ")");
        }
        if (step == Step.SECOND) {
            return error(HttpStatus.BAD_REQUEST,
                "step must be one of MINUTE / HOUR / DAY (got SECOND)");
        }
        final Set<DownSampling> supported = supportedDownsamplings(metric);
        if (!supported.contains(stepToDownsampling(step))) {
            return error(HttpStatus.BAD_REQUEST,
                "step " + step + " not supported by metric " + metric
                    + " (" + sortedDownsamplingNames(supported) + ")");
        }

        // Validate limit.
        final int limit = limitOpt.orElse(LIMIT_DEFAULT);
        if (limit < 1 || limit > LIMIT_MAX) {
            return error(HttpStatus.BAD_REQUEST, "limit must be between 1 and " + LIMIT_MAX);
        }

        final Duration duration = new Duration();
        duration.setStart(start);
        duration.setEnd(end);
        duration.setStep(step);

        // Validate the date strings up front by triggering the same parse the storage path will
        // run. Joda throws IllegalArgumentException on bad format; the framework's
        // verifyDateTimeString throws UnexpectedException on unsupported steps. Catch both here
        // so a malformed `start`/`end` returns 400 instead of bubbling out as a 500.
        try {
            duration.getStartTimeBucket();
            duration.getEndTimeBucket();
        } catch (IllegalArgumentException | UnexpectedException e) {
            return error(HttpStatus.BAD_REQUEST,
                "start / end must follow the step's date format (DAY: yyyy-MM-dd, HOUR: "
                    + "yyyy-MM-dd HH, MINUTE: yyyy-MM-dd HHmm, SECOND: yyyy-MM-dd HHmmss): "
                    + e.getMessage());
        }

        final List<String> entityIds;
        try {
            entityIds = metricsQueryDAO()
                .listEntityIdsInRange(metric, vc.getValueCName(), null, duration, limit);
        } catch (IOException e) {
            log.warn("listEntityIdsInRange failed for metric={} step={}", metric, step, e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }

        final List<EntityRow> rows = new ArrayList<>();
        for (final String entityId : entityIds) {
            final EntityDecoder.Decoded decoded;
            try {
                decoded = EntityDecoder.decode(scope, entityId);
            } catch (Exception e) {
                // Malformed id from storage — skip but keep the response well-formed.
                log.warn("Failed to decode entity_id={} for scope={}", entityId, scope, e);
                continue;
            }
            final List<String> layers = lookupLayers(decoded.serviceIdForLayer);
            if (layers.isEmpty()) {
                rows.add(new EntityRow(entityId, decoded.decodedFields, null, decoded.mqeEntity));
            } else {
                for (final String layer : layers) {
                    rows.add(new EntityRow(entityId, decoded.decodedFields, layer, decoded.mqeEntity));
                }
            }
        }

        final EntitiesResponse body = new EntitiesResponse(metric, scope.name(),
            step.name(), start, end, rows);
        return HttpResponse.ofJson(MediaType.JSON_UTF_8, body);
    }

    /**
     * Foreign-metric path: the metric is not defined on this OAP, so the caller supplied
     * {@code valueColumn} + {@code valueType}. Nothing is resolved from the local registry — the
     * storage DAO derives the physical target from its own running config — and each entity_id is
     * decoded structurally (scope-free), emitting a generic {@code name} leaf and no
     * {@code mqeEntity}. Errors and empty results flow straight back to the caller; an empty result
     * means "no rows in range", not a reliable "metric absent".
     */
    private HttpResponse listForeignEntities(final String metric,
                                             final String valueColumn,
                                             final String valueType,
                                             final String start,
                                             final String end,
                                             final String stepStr,
                                             final Optional<Integer> limitOpt) {
        final String type = valueType.toUpperCase();
        if (!ACCEPTED_FOREIGN_VALUE_TYPES.contains(type)) {
            return error(HttpStatus.BAD_REQUEST,
                "valueType must be one of LONG / INT / DOUBLE / LABELED (got " + valueType + ")");
        }

        final Step step;
        try {
            step = Step.valueOf(stepStr.toUpperCase());
        } catch (Exception e) {
            return error(HttpStatus.BAD_REQUEST,
                "step must be one of MINUTE / HOUR / DAY (got " + stepStr + ")");
        }
        if (step == Step.SECOND) {
            return error(HttpStatus.BAD_REQUEST,
                "step must be one of MINUTE / HOUR / DAY (got SECOND)");
        }

        final int limit = limitOpt.orElse(LIMIT_DEFAULT);
        if (limit < 1 || limit > LIMIT_MAX) {
            return error(HttpStatus.BAD_REQUEST, "limit must be between 1 and " + LIMIT_MAX);
        }

        final Duration duration = new Duration();
        duration.setStart(start);
        duration.setEnd(end);
        duration.setStep(step);
        try {
            duration.getStartTimeBucket();
            duration.getEndTimeBucket();
        } catch (IllegalArgumentException | UnexpectedException e) {
            return error(HttpStatus.BAD_REQUEST,
                "start / end must follow the step's date format (DAY: yyyy-MM-dd, HOUR: "
                    + "yyyy-MM-dd HH, MINUTE: yyyy-MM-dd HHmm): " + e.getMessage());
        }

        final List<String> entityIds;
        try {
            entityIds = metricsQueryDAO().listEntityIdsInRange(metric, valueColumn, type, duration, limit);
        } catch (Exception e) {
            // Optimistic read: surface the storage error directly. A wrong valueColumn/valueType, an
            // unsupported storage mode (e.g. ES logicSharding), or a missing table lands here.
            log.warn("foreign-metric listEntityIdsInRange failed for metric={} step={}", metric, step, e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }

        final List<EntityRow> rows = new ArrayList<>();
        for (final String entityId : entityIds) {
            final EntityDecoder.Decoded decoded;
            try {
                decoded = EntityDecoder.decodeUnknownScope(entityId);
            } catch (Exception e) {
                log.warn("Failed to structurally decode entity_id={}", entityId, e);
                continue;
            }
            final List<String> layers = lookupLayers(decoded.serviceIdForLayer);
            if (layers.isEmpty()) {
                rows.add(new EntityRow(entityId, decoded.decodedFields, null, decoded.mqeEntity));
            } else {
                for (final String layer : layers) {
                    rows.add(new EntityRow(entityId, decoded.decodedFields, layer, decoded.mqeEntity));
                }
            }
        }

        // scope is null: a foreign metric's structural kind is per-row in `decoded`, and a single
        // metric's entities all share one structure anyway.
        final EntitiesResponse body = new EntitiesResponse(metric, null, step.name(), start, end, rows);
        return HttpResponse.ofJson(MediaType.JSON_UTF_8, body);
    }

    /**
     * Read the VALUES of metric(s) persisted by another OAP that this node does not define. The body
     * carries an MQE expression plus, in {@code foreignMetrics}, the metadata for each foreign metric
     * it references (value column + type). The same MQE engine the public GraphQL surface uses is run
     * synchronously with that metadata overlaid PROVIDE-IF-ABSENT (the catalog always wins), returning
     * the native {@code ExpressionResult}. Marked {@code @Blocking}: the eval + storage read are
     * synchronous and must not run on the event loop. Only scalar (LONG/INT/DOUBLE) and labeled
     * (best-effort) value series are supported; {@code top_n} and record/heatmap shapes need a local
     * model and surface as an error.
     */
    @Blocking
    @Post("/inspect/values")
    public HttpResponse listValues(final HttpData requestBody) {
        final InspectValuesRequest req;
        try {
            req = VALUES_MAPPER.readValue(requestBody.toStringUtf8(), InspectValuesRequest.class);
        } catch (Exception e) {
            return error(HttpStatus.BAD_REQUEST, "invalid request body: " + e.getMessage());
        }
        if (req.getExpression() == null || req.getExpression().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "expression is required");
        }
        if (req.getEntity() == null || req.getEntity().getScope() == null) {
            return error(HttpStatus.BAD_REQUEST, "entity (with a scope) is required");
        }
        // The scope alone is not enough: the entity must carry the name fields its scope needs
        // (e.g. serviceName + normal for Service), or buildId() yields a bogus id that the read
        // silently misses — surface that as a 400 instead of an empty 200.
        if (!req.getEntity().isValid()) {
            return error(HttpStatus.BAD_REQUEST,
                "entity is missing required fields for scope " + req.getEntity().getScope()
                    + " (Service needs serviceName + normal; ServiceInstance/Endpoint also need "
                    + "serviceInstanceName / endpointName)");
        }
        if (req.getForeignMetrics() == null || req.getForeignMetrics().isEmpty()) {
            return error(HttpStatus.BAD_REQUEST,
                "foreignMetrics is required; a locally-defined metric should be queried via the public "
                    + "GraphQL execExpression");
        }

        final Step step;
        try {
            step = Step.valueOf(String.valueOf(req.getStep()).toUpperCase());
        } catch (Exception e) {
            return error(HttpStatus.BAD_REQUEST,
                "step must be one of MINUTE / HOUR / DAY (got " + req.getStep() + ")");
        }
        if (step == Step.SECOND) {
            return error(HttpStatus.BAD_REQUEST, "step must be one of MINUTE / HOUR / DAY (got SECOND)");
        }

        final int scopeId = req.getEntity().getScope().getScopeId();
        final List<ForeignMetricMeta> foreign = new ArrayList<>();
        for (final InspectValuesRequest.ForeignMetricInput fm : req.getForeignMetrics()) {
            if (fm.getName() == null || fm.getName().isBlank()) {
                return error(HttpStatus.BAD_REQUEST, "each foreignMetrics entry needs a name");
            }
            if (fm.getValueColumn() == null || !VALUE_COLUMN_PATTERN.matcher(fm.getValueColumn()).matches()) {
                return error(HttpStatus.BAD_REQUEST, "valueColumn is invalid: " + fm.getValueColumn());
            }
            final String type = fm.getValueType() == null ? "" : fm.getValueType().toUpperCase();
            if (!ACCEPTED_FOREIGN_VALUE_TYPES.contains(type)) {
                return error(HttpStatus.BAD_REQUEST,
                    "valueType must be one of LONG / INT / DOUBLE / LABELED (got " + fm.getValueType() + ")");
            }
            if (ValueColumnMetadata.INSTANCE.readValueColumnDefinition(fm.getName()).isPresent()) {
                return error(HttpStatus.BAD_REQUEST,
                    "metric " + fm.getName() + " is defined locally; query it via the GraphQL "
                        + "execExpression and drop it from foreignMetrics");
            }
            foreign.add(new ForeignMetricMeta(fm.getName(), fm.getValueColumn(), type, scopeId, 0));
        }

        final Duration duration = new Duration();
        duration.setStart(req.getStart());
        duration.setEnd(req.getEnd());
        duration.setStep(step);
        try {
            duration.getStartTimeBucket();
            duration.getEndTimeBucket();
        } catch (IllegalArgumentException | UnexpectedException e) {
            return error(HttpStatus.BAD_REQUEST,
                "start / end must follow the step's date format (DAY: yyyy-MM-dd, HOUR: yyyy-MM-dd HH, "
                    + "MINUTE: yyyy-MM-dd HHmm): " + e.getMessage());
        }

        final ExpressionResult result;
        try {
            result = new MQEExecutor(moduleManager)
                .execute(req.getExpression(), req.getEntity(), duration, foreign);
        } catch (Exception e) {
            // Optimistic read: a foreign top_n / record shape, or a wrong valueColumn/valueType,
            // surfaces here rather than as garbage.
            log.warn("inspect values execute failed for expression={}", req.getExpression(), e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
        if (result.getError() != null) {
            // e.g. an unsupported shape resolved to UNKNOWN — never put that on the wire as a 200.
            return error(HttpStatus.BAD_REQUEST, result.getError());
        }
        return HttpResponse.ofJson(MediaType.JSON_UTF_8, result);
    }

    /**
     * Mirror of the {@code /inspect/entities} type acceptance set. Kept in one place so
     * the {@code mqeQueryable=true} filter on {@code /inspect/metrics} and the actual
     * rejection in {@code /inspect/entities} cannot drift.
     */
    private static boolean isMqeDispatchableType(final MetricsType type) {
        return type == MetricsType.REGULAR_VALUE || type == MetricsType.LABELED_VALUE;
    }

    private boolean isSupportedScope(final Scope scope) {
        switch (scope) {
            case Service:
            case ServiceInstance:
            case Endpoint:
            case ServiceRelation:
            case ServiceInstanceRelation:
            case EndpointRelation:
                return true;
            default:
                return false;
        }
    }

    private List<String> lookupLayers(final String serviceId) {
        if (serviceId == null) {
            return Collections.emptyList();
        }
        try {
            final Service svc = metadataQueryService().getService(serviceId);
            if (svc == null || svc.getLayers() == null || svc.getLayers().isEmpty()) {
                return Collections.emptyList();
            }
            // Stable ordering across rows so multi-layer responses don't shuffle.
            return new ArrayList<>(new TreeSet<>(svc.getLayers()));
        } catch (Exception e) {
            log.debug("Layer lookup failed for serviceId={}", serviceId, e);
            return Collections.emptyList();
        }
    }

    private static DownSampling stepToDownsampling(final Step step) {
        switch (step) {
            case DAY:
                return DownSampling.Day;
            case HOUR:
                return DownSampling.Hour;
            case MINUTE:
                return DownSampling.Minute;
            case SECOND:
                return DownSampling.Second;
            default:
                throw new IllegalArgumentException("unsupported step: " + step);
        }
    }

    private Set<DownSampling> supportedDownsamplings(final String metricName) {
        final Set<DownSampling> result = new HashSet<>();
        for (final Model m : modelManager().allModels()) {
            if (metricName.equals(m.getName())) {
                result.add(m.getDownsampling());
            }
        }
        return result;
    }

    private static List<String> sortedDownsamplingNames(final Set<DownSampling> set) {
        // None / Second / Minute / Hour / Day — sort by ordinal so MINUTE precedes HOUR precedes DAY
        // in the response, matching the API contract example shape.
        final List<DownSampling> list = new ArrayList<>(set);
        list.sort((a, b) -> Integer.compare(a.ordinal(), b.ordinal()));
        return list.stream().map(DownSampling::name).map(String::toUpperCase).collect(Collectors.toList());
    }

    private static Set<String> toSet(final Optional<List<String>> opt) {
        if (opt.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(opt.get());
    }

    private static HttpResponse error(final HttpStatus status, final String message) {
        return HttpResponse.ofJson(status, MediaType.JSON_UTF_8, new ErrorResponse(message));
    }

    private IModelManager modelManager() {
        return moduleManager.find(CoreModule.NAME).provider().getService(IModelManager.class);
    }

    private IMetricsQueryDAO metricsQueryDAO() {
        return moduleManager.find(StorageModule.NAME).provider().getService(IMetricsQueryDAO.class);
    }

    private MetadataQueryService metadataQueryService() {
        return moduleManager.find(CoreModule.NAME).provider().getService(MetadataQueryService.class);
    }
}
