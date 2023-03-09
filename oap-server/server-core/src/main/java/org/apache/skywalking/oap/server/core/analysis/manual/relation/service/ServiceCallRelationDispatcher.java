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

import java.util.Optional;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntList;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.source.ServiceRelation;
import org.apache.skywalking.oap.server.library.util.StringUtil;

public class ServiceCallRelationDispatcher implements SourceDispatcher<ServiceRelation> {
    @Override
    public void dispatch(ServiceRelation source) {
        switch (source.getDetectPoint()) {
            case SERVER:
                serverSide(source);
                break;
            case CLIENT:
                clientSide(source);
                break;
        }
    }

    private void serverSide(ServiceRelation source) {
        ServiceRelationServerSideMetrics metrics = new ServiceRelationServerSideMetrics();
        metrics.setTimeBucket(source.getTimeBucket());
        metrics.setSourceServiceId(source.getSourceServiceId());
        metrics.setDestServiceId(source.getDestServiceId());
        final IntList componentIds = metrics.getComponentIds();
        componentIds.add(source.getComponentId());
        tlsStatus(source.getTlsMode()).ifPresent(componentIds::add);
        metrics.setEntityId(source.getEntityId());
        MetricsStreamProcessor.getInstance().in(metrics);
    }

    private void clientSide(ServiceRelation source) {
        ServiceRelationClientSideMetrics metrics = new ServiceRelationClientSideMetrics();
        metrics.setTimeBucket(source.getTimeBucket());
        metrics.setSourceServiceId(source.getSourceServiceId());
        metrics.setDestServiceId(source.getDestServiceId());
        final IntList componentIds = metrics.getComponentIds();
        componentIds.add(source.getComponentId());
        tlsStatus(source.getTlsMode()).ifPresent(componentIds::add);
        metrics.setEntityId(source.getEntityId());
        MetricsStreamProcessor.getInstance().in(metrics);
    }

    private Optional<Integer> tlsStatus(String tlsMode) {
        if (StringUtil.isBlank(tlsMode)) {
            return Optional.empty();
        }
        switch (tlsMode) {
            case Const.TLS_MODE.M_TLS:
                // component ID, mtls = 142
                return Optional.of(142);
            case Const.TLS_MODE.TLS:
                // component ID, mtls = 130
                return Optional.of(130);
            case Const.TLS_MODE.NON_TLS:
            default:
                return Optional.empty();
        }
    }
}
