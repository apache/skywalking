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

package org.apache.skywalking.oap.server.core.query.input;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.query.MetricsMetadataQueryService;
import org.apache.skywalking.oap.server.core.query.enumeration.MetricsType;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * Record query condition.
 *
 * @since 9.3.0
 */
@Setter
@Getter
public class RecordCondition {
    /**
     * Metrics name
     */
    private String name;
    /**
     * Follow {@link Entity} definition description. The owner of the sampled records.
     */
    private Entity parentEntity;
    private int topN;
    private Order order;

    public RecordCondition() {
    }

    public RecordCondition(TopNCondition condition) {
        this.name = condition.getName();
        if (StringUtil.isNotEmpty(condition.getParentService())) {
            final Entity entity = new Entity();
            entity.setScope(condition.getScope() == null ? Scope.Service : condition.getScope());
            entity.setServiceName(condition.getParentService());
            entity.setNormal(condition.isNormal());
            this.parentEntity = entity;
        }
        this.topN = condition.getTopN();
        this.order = condition.getOrder();
    }

    /**
     * Sense Scope through metric name.
     * @return false if not a valid metric name.
     */
    public boolean senseScope() {
        if (MetricsType.UNKNOWN.equals(MetricsMetadataQueryService.typeOfMetrics(name))) {
            return false;
        }
        parentEntity.setScope(ValueColumnMetadata.INSTANCE.getScope(name));
        return true;
    }
}
