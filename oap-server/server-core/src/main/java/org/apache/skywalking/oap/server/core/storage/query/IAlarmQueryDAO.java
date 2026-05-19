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

package org.apache.skywalking.oap.server.core.storage.query;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.alarm.AlarmSnapshotRecord;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.input.AlarmQueryCondition;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.input.EntityIdConstraint;
import org.apache.skywalking.oap.server.core.query.mqe.MQEMetric;
import org.apache.skywalking.oap.server.core.query.mqe.MQEValues;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.AlarmSnapshot;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.storage.DAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;

public interface IAlarmQueryDAO extends DAO {

    Gson GSON = new Gson();

    Alarms getAlarm(final Integer scopeId, final String keyword, final int limit, final int from,
                    final Duration duration, final List<Tag> tags) throws IOException;

    /**
     * Comprehensive alarm query introduced in 11.0.0 (mirrors GraphQL
     * {@code queryAlarms(condition)}). Filters by entity / layer / ruleName
     * in addition to the legacy {@link #getAlarm} fields. Implementations
     * must treat null/empty list filter fields as "no filter".
     *
     * <p>Entity resolution: each {@link Entity} in
     * {@link AlarmQueryCondition#getEntities()} is translated by
     * {@link #resolveEntityFilters} into one or more EntityIdConstraint
     * entries (one for relations: exact id0=src AND id1=dest; two for
     * non-relation scopes: id0=X OR id1=X). The implementation OR's the
     * per-entity constraints to produce the final storage filter.
     *
     * @since 11.0.0
     */
    Alarms queryAlarms(AlarmQueryCondition condition, int limit, int from) throws IOException;

    /**
     * Translate a list of {@link Entity} filters into a list of
     * {@link EntityIdConstraint}s. Each entity produces one or two
     * constraints; the storage backend then ORs the constraints, AND-ing
     * the id0/id1 predicates within each constraint.
     *
     * <p>Semantics per Entity scope:
     * <ul>
     *   <li>Non-relation (Service / ServiceInstance / Endpoint / Process) — two
     *       constraints: {@code (id0=X, id1=null)} OR {@code (id0=null, id1=X)}.
     *       Matches alarms where this entity is the primary, OR where this
     *       entity is the destination of a relation alarm.</li>
     *   <li>Relation (ServiceRelation, ServiceInstanceRelation, EndpointRelation,
     *       ProcessRelation) — one constraint: {@code (id0=srcId, id1=destId)}.
     *       Exact relation match; broader matches require passing the
     *       endpoints as separate non-relation entities.</li>
     *   <li>{@code Scope.All} or invalid — no contribution.</li>
     * </ul>
     *
     * <p>If any supplied entity is invalid after defaulting (missing
     * required name fields for its inferred scope) the method throws
     * {@link IllegalArgumentException}. Returning an empty constraint list
     * for the no-entities case is fine — that's "no entity filter" — but
     * an invalid populated entity must not silently widen the query to
     * "all alarms"; failing loud surfaces the client mistake instead.
     *
     * @since 11.0.0
     */
    default List<EntityIdConstraint> resolveEntityFilters(final List<Entity> entities) {
        final List<EntityIdConstraint> constraints = new ArrayList<>();
        if (entities == null || entities.isEmpty()) {
            return constraints;
        }
        for (final Entity entity : entities) {
            if (entity == null) {
                continue;
            }
            // Auto-infer scope when the client omits it (mirrors the GraphQL
            // doc's "auto-inferred" promise). The inference picks the most
            // specific scope consistent with the populated name fields.
            if (entity.getScope() == null) {
                entity.setScope(inferScope(entity));
            }
            // Default `normal` flags to true when missing. The GraphQL Entity
            // input declares both nullable, but every scope that needs them
            // requires them per Entity.isValid(); defaulting to "normal
            // service" (agent-reporting, the common case) matches MQE-side
            // ergonomics and prevents the silent "all alarms" widening that
            // would happen if isValid() returned false and we skipped the
            // entry.
            if (entity.getScope() != null && entity.getNormal() == null) {
                entity.setNormal(Boolean.TRUE);
            }
            if (entity.getScope() != null && requiresDestNormal(entity.getScope())
                && entity.getDestNormal() == null) {
                entity.setDestNormal(Boolean.TRUE);
            }
            if (entity.getScope() == null || !entity.isValid()) {
                throw new IllegalArgumentException(
                    "queryAlarms entity is invalid (scope=" + entity.getScope()
                        + "); required name fields missing. Refusing to silently widen the "
                        + "filter to all alarms — see AlarmQueryCondition.entities documentation.");
            }
            switch (entity.getScope()) {
                case Service:
                case ServiceInstance:
                case Endpoint:
                case Process: {
                    final String id = entity.buildId();
                    constraints.add(new EntityIdConstraint(id, null));
                    constraints.add(new EntityIdConstraint(null, id));
                    break;
                }
                case ServiceRelation:
                    constraints.add(new EntityIdConstraint(
                        IDManager.ServiceID.buildId(entity.getServiceName(), entity.getNormal()),
                        IDManager.ServiceID.buildId(entity.getDestServiceName(), entity.getDestNormal())));
                    break;
                case ServiceInstanceRelation:
                    constraints.add(new EntityIdConstraint(
                        IDManager.ServiceInstanceID.buildId(
                            IDManager.ServiceID.buildId(entity.getServiceName(), entity.getNormal()),
                            entity.getServiceInstanceName()),
                        IDManager.ServiceInstanceID.buildId(
                            IDManager.ServiceID.buildId(entity.getDestServiceName(), entity.getDestNormal()),
                            entity.getDestServiceInstanceName())));
                    break;
                case EndpointRelation:
                    constraints.add(new EntityIdConstraint(
                        IDManager.EndpointID.buildId(
                            IDManager.ServiceID.buildId(entity.getServiceName(), entity.getNormal()),
                            entity.getEndpointName()),
                        IDManager.EndpointID.buildId(
                            IDManager.ServiceID.buildId(entity.getDestServiceName(), entity.getDestNormal()),
                            entity.getDestEndpointName())));
                    break;
                case ProcessRelation:
                    constraints.add(new EntityIdConstraint(
                        IDManager.ProcessID.buildId(
                            IDManager.ServiceInstanceID.buildId(
                                IDManager.ServiceID.buildId(entity.getServiceName(), entity.getNormal()),
                                entity.getServiceInstanceName()),
                            entity.getProcessName()),
                        IDManager.ProcessID.buildId(
                            IDManager.ServiceInstanceID.buildId(
                                IDManager.ServiceID.buildId(entity.getDestServiceName(), entity.getDestNormal()),
                                entity.getDestServiceInstanceName()),
                            entity.getDestProcessName())));
                    break;
                default:
                    // Scope.All — no ID contribution, equivalent to no entity filter.
                    break;
            }
        }
        return constraints;
    }

    /**
     * Pick the most specific scope consistent with which name fields the
     * caller populated on the entity. Returns {@code null} if the entity
     * looks empty (no name fields at all). Used by
     * {@link #resolveEntityFilters} to honor the GraphQL doc's "scope
     * auto-inferred" promise when the client omits {@code scope}.
     *
     * <p>Inference rules: any {@code dest*Name} populated picks the matching
     * relation scope (narrowed by the deepest {@code dest*} field set);
     * otherwise a non-relation scope is picked from the deepest source-side
     * field. The result is then validated via {@link Entity#isValid()} in
     * the calling resolver — inference does not bypass validation.
     */
    /** True for relation scopes — both source and destination sides require a normal flag. */
    default boolean requiresDestNormal(final Scope scope) {
        return scope == Scope.ServiceRelation
            || scope == Scope.ServiceInstanceRelation
            || scope == Scope.EndpointRelation
            || scope == Scope.ProcessRelation;
    }

    default Scope inferScope(final Entity entity) {
        if (entity.getDestProcessName() != null) {
            return Scope.ProcessRelation;
        }
        if (entity.getDestEndpointName() != null) {
            return Scope.EndpointRelation;
        }
        if (entity.getDestServiceInstanceName() != null) {
            return Scope.ServiceInstanceRelation;
        }
        if (entity.getDestServiceName() != null) {
            return Scope.ServiceRelation;
        }
        if (entity.getProcessName() != null) {
            return Scope.Process;
        }
        if (entity.getEndpointName() != null) {
            return Scope.Endpoint;
        }
        if (entity.getServiceInstanceName() != null) {
            return Scope.ServiceInstance;
        }
        if (entity.getServiceName() != null) {
            return Scope.Service;
        }
        return null;
    }

    /**
     * Parse the raw tags.
     */
    default void parseDataBinaryBase64(String dataBinaryBase64, List<KeyValue> tags) {
        parseDataBinary(Base64.getDecoder().decode(dataBinaryBase64), tags);
    }

    /**
     * Parse the raw tags.
     */
    default void parseDataBinary(byte[] dataBinary, List<KeyValue> tags) {
        List<Tag> tagList = GSON.fromJson(new String(dataBinary, Charsets.UTF_8), new TypeToken<List<Tag>>() {
        }.getType());
        tagList.forEach(pair -> tags.add(new KeyValue(pair.getKey(), pair.getValue())));
    }

    /**
     * Build the alarm message from the alarm record.
     * The Tags in JDBC storage is base64 encoded, need to decode in different way.
     */
    default AlarmMessage buildAlarmMessage(AlarmRecord alarmRecord) {
        AlarmMessage message = new AlarmMessage();
        message.setId(String.valueOf(alarmRecord.getId0()));
        message.setId1(String.valueOf(alarmRecord.getId1()));
        message.setUuid(alarmRecord.getUuid());
        message.setName(alarmRecord.getName());
        message.setMessage(alarmRecord.getAlarmMessage());
        message.setStartTime(alarmRecord.getStartTime());
        message.setScope(Scope.Finder.valueOf(alarmRecord.getScope()));
        message.setScopeId(alarmRecord.getScope());
        AlarmSnapshot alarmSnapshot = message.getSnapshot();
        message.setSnapshot(alarmSnapshot);
        String snapshot = alarmRecord.getSnapshot();
        if (StringUtil.isNotBlank(snapshot)) {
            AlarmSnapshotRecord alarmSnapshotRecord = GSON.fromJson(snapshot, AlarmSnapshotRecord.class);
            alarmSnapshot.setExpression(alarmSnapshotRecord.getExpression());
            JsonObject jsonObject = alarmSnapshotRecord.getMetrics();
            if (jsonObject != null) {
                for (final var obj : jsonObject.entrySet()) {
                    final var name = obj.getKey();
                    MQEMetric metrics = new MQEMetric();
                    metrics.setName(name);
                    List<MQEValues> values = GSON.fromJson(
                            obj.getValue().getAsString(), new TypeToken<List<MQEValues>>() {
                            }.getType());
                    metrics.setResults(values);
                    alarmSnapshot.getMetrics().add(metrics);
                }
            }
        }
        return message;
    }
}
