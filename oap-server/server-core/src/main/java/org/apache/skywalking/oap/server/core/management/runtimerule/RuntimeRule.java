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

package org.apache.skywalking.oap.server.core.management.runtimerule;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.management.ManagementData;
import org.apache.skywalking.oap.server.core.analysis.worker.ManagementStreamProcessor;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.RUNTIME_RULE;

/**
 * RuntimeRule is the persisted representation of a runtime-managed MAL or LAL rule file.
 *
 * <p>One row per (catalog, name) pair mirroring the on-disk static layout:
 * <ul>
 *   <li>{@code catalog} — {@code otel-rules} | {@code log-mal-rules} |
 *       {@code telegraf-rules} | {@code lal}</li>
 *   <li>{@code name} — relative path under the catalog root without extension, may contain
 *       {@code /} (e.g. {@code aws-gateway/gateway-service})</li>
 *   <li>{@code content} — raw file bytes, byte-identical to the original request body; marked
 *       {@code storageOnly=true} so no backend tries to index the blob</li>
 *   <li>{@code status} — {@code ACTIVE} or {@code INACTIVE}; runtime rows always take precedence
 *       over static rules with the same (catalog, name) at load time</li>
 *   <li>{@code updateTime} — last modification epoch millis</li>
 * </ul>
 *
 * <p>No {@code version} column: writes are last-write-wins and reconciliation on peer nodes is
 * driven by content-hash comparison rather than a monotonic counter. No {@code lastApplyError}
 * column either: compile/apply happens per-node after persistence and can diverge across nodes,
 * so errors surface inline in the HTTP response, in OAP server logs, and in a per-node in-memory
 * map exposed via the runtime-rule list API.
 */
@Setter
@Getter
@ScopeDeclaration(id = RUNTIME_RULE, name = "RuntimeRule")
@Stream(name = RuntimeRule.INDEX_NAME, scopeId = RUNTIME_RULE, builder = RuntimeRule.Builder.class, processor = ManagementStreamProcessor.class)
@EqualsAndHashCode(of = {
    "catalog", "name"
}, callSuper = false)
public class RuntimeRule extends ManagementData {
    public static final String INDEX_NAME = "runtimerule";
    public static final String CATALOG = "catalog";
    public static final String NAME = "name";
    public static final String CONTENT = "content";
    public static final String STATUS = "status";
    public static final String UPDATE_TIME = "update_time";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    @Column(name = CATALOG)
    private String catalog;
    @Column(name = NAME)
    private String name;
    /**
     * Raw file bytes (MAL YAML / LAL YAML). Blob stored only, never queried or filtered.
     * Size limit matches {@code UITemplate.configuration} — 1 MB is generous for a single rule
     * file; larger files should be split.
     */
    @Column(name = CONTENT, storageOnly = true, length = 1_000_000)
    private String content;
    @Column(name = STATUS)
    private String status;
    /**
     * Boxed {@link Long} (not primitive {@code long}) so the column type matches sibling
     * {@code ManagementData} entities (UITemplate, UIMenu) that share the ES merging-index
     * {@code sw_management}; a primitive-vs-boxed mismatch is rejected by
     * {@code IndexController.checkModelColumnConflicts} at startup.
     */
    @Column(name = UPDATE_TIME)
    private Long updateTime = 0L;

    @Override
    public StorageID id() {
        return new StorageID().append(CATALOG, catalog).append(NAME, name);
    }

    public static class Builder implements StorageBuilder<RuntimeRule> {
        @Override
        public RuntimeRule storage2Entity(final Convert2Entity converter) {
            final RuntimeRule rule = new RuntimeRule();
            rule.setCatalog((String) converter.get(CATALOG));
            rule.setName((String) converter.get(NAME));
            rule.setContent((String) converter.get(CONTENT));
            rule.setStatus((String) converter.get(STATUS));
            final Object updateTime = converter.get(UPDATE_TIME);
            if (updateTime != null) {
                rule.setUpdateTime(((Number) updateTime).longValue());
            }
            return rule;
        }

        @Override
        public void entity2Storage(final RuntimeRule entity, final Convert2Storage converter) {
            converter.accept(CATALOG, entity.getCatalog());
            converter.accept(NAME, entity.getName());
            converter.accept(CONTENT, entity.getContent());
            converter.accept(STATUS, entity.getStatus());
            converter.accept(UPDATE_TIME, entity.getUpdateTime());
        }
    }
}
