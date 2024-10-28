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

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.MetricsMetaInfo;
import org.apache.skywalking.oap.server.core.analysis.metrics.WithMetadata;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * Meter is the abstract parent for all {@link org.apache.skywalking.oap.server.core.analysis.meter.function.MeterFunction} annotated functions.
 * It provides the {@link WithMetadata} implementation for alarm kernal.
 *
 * @since 9.0.0
 */
public abstract class Meter extends Metrics implements WithMetadata {
    protected static final String ATTR0 = "attr0";
    protected static final String ATTR1 = "attr1";
    protected static final String ATTR2 = "attr2";
    protected static final String ATTR3 = "attr3";
    protected static final String ATTR4 = "attr4";

    private MetricsMetaInfo metadata = new MetricsMetaInfo("UNKNOWN", DefaultScopeDefine.UNKNOWN);

    @Setter
    @Getter
    @Column(name = ATTR0)
    private String attr0;

    @Setter
    @Getter
    @Column(name = ATTR1)
    private String attr1;

    @Setter
    @Getter
    @Column(name = ATTR2)
    private String attr2;

    @Setter
    @Getter
    @Column(name = ATTR3)
    private String attr3;

    @Setter
    @Getter
    @Column(name = ATTR4)
    private String attr4;

    /**
     * @return entity ID to represent this metric object. Typically, meter function should have a String type field, named entityId.
     * See {@link org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgFunction#getEntityId()} as an example.
     */
    public abstract String getEntityId();

    /**
     * This method is called in {@link MeterSystem#create} process through dynamic Java codes.
     *
     * @param metricName metric name
     * @param scopeId    scope Id defined in {@link DefaultScopeDefine}
     */
    public void initMeta(String metricName, int scopeId) {
        this.metadata.setMetricsName(metricName);
        this.metadata.setScope(scopeId);
    }

    public MetricsMetaInfo getMeta() {
        // Only read the id from the implementation when needed, to avoid uninitialized cases.
        this.metadata.setId(this.getEntityId());
        return metadata;
    }

    /**
     * Decorate the metric with the entity attributes.
     * Only single value metrics can be decorated.
     * Because we only support query the decorated condition in top_n query for now.
     * @param entity The metric entity
     */
    protected void decorate(MeterEntity entity) {
        attr0 = entity.getAttr0();
        attr1 = entity.getAttr1();
        attr2 = entity.getAttr2();
        attr3 = entity.getAttr3();
        attr4 = entity.getAttr4();
    }
}
