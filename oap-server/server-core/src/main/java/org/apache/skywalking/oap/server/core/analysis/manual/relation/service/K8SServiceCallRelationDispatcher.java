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

package org.apache.skywalking.oap.server.core.analysis.manual.relation.service;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntList;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.source.K8SServiceRelation;

public class K8SServiceCallRelationDispatcher implements SourceDispatcher<K8SServiceRelation> {
    @Override
    public void dispatch(K8SServiceRelation source) {
        switch (source.getDetectPoint()) {
            case SERVER:
                serverSide(source);
                break;
            case CLIENT:
                clientSide(source);
                break;
        }
    }

    private void clientSide(K8SServiceRelation source) {
        ServiceRelationClientSideMetrics metrics = new ServiceRelationClientSideMetrics();
        metrics.setTimeBucket(source.getTimeBucket());
        metrics.setSourceServiceId(source.getSourceServiceId());
        metrics.setDestServiceId(source.getDestServiceId());
        final IntList componentIds = metrics.getComponentIds();
        source.getComponentIds().forEach(componentIds::add);
        metrics.setEntityId(source.getEntityId());
        MetricsStreamProcessor.getInstance().in(metrics);
    }

    private void serverSide(K8SServiceRelation source) {
        final ServiceRelationServerSideMetrics metrics = new ServiceRelationServerSideMetrics();
        metrics.setTimeBucket(source.getTimeBucket());
        metrics.setSourceServiceId(source.getSourceServiceId());
        metrics.setDestServiceId(source.getDestServiceId());
        final IntList componentIds = metrics.getComponentIds();
        source.getComponentIds().forEach(componentIds::add);
        metrics.setEntityId(source.getEntityId());
        MetricsStreamProcessor.getInstance().in(metrics);
    }
}
