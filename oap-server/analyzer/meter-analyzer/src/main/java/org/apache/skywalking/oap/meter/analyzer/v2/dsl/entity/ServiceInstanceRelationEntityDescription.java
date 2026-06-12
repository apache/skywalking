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

package org.apache.skywalking.oap.meter.analyzer.v2.dsl.entity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.google.common.collect.ImmutableList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.library.util.StringUtil;

@Getter
@RequiredArgsConstructor
@ToString
public class ServiceInstanceRelationEntityDescription implements EntityDescription {
    private final ScopeType scopeType = ScopeType.SERVICE_INSTANCE_RELATION;
    private final List<String> sourceServiceKeys;
    private final List<String> sourceInstanceKeys;
    private final List<String> destServiceKeys;
    private final List<String> destInstanceKeys;
    private final DetectPoint detectPoint;
    private final Layer layer;
    private final String delimiter;
    private final String componentIdKey;

    @Override
    public List<String> getLabelKeys() {
        // De-duplicate: the source and destination service key lists are usually identical for an
        // intra-cluster (same-service) relation (e.g. both ['cluster']). The label keys are used to
        // group samples into an ImmutableMap keyed by label name, which rejects duplicate keys, so the
        // union must be distinct. The per-side key lists (read in buildMeterEntity) keep their own copy.
        final Set<String> keys = new LinkedHashSet<>();
        keys.addAll(this.sourceServiceKeys);
        keys.addAll(this.sourceInstanceKeys);
        keys.addAll(this.destServiceKeys);
        keys.addAll(this.destInstanceKeys);
        if (StringUtil.isNotEmpty(componentIdKey)) {
            keys.add(componentIdKey);
        }
        return ImmutableList.copyOf(keys);
    }
}
