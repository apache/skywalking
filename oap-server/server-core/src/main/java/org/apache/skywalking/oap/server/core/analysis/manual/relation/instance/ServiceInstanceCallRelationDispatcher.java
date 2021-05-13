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

package org.apache.skywalking.oap.server.core.analysis.manual.relation.instance;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceRelation;

public class ServiceInstanceCallRelationDispatcher implements SourceDispatcher<ServiceInstanceRelation> {

    @Override
    public void dispatch(ServiceInstanceRelation source) {
        switch (source.getDetectPoint()) {
            case SERVER:
                serverSide(source);
                break;
            case CLIENT:
                clientSide(source);
                break;
        }
    }

    private void serverSide(ServiceInstanceRelation source) {
        ServiceInstanceRelationServerSideMetrics metrics = new ServiceInstanceRelationServerSideMetrics();
        metrics.setTimeBucket(source.getTimeBucket());
        metrics.setSourceServiceId(source.getSourceServiceId());
        metrics.setSourceServiceInstanceId(source.getSourceServiceInstanceId());
        metrics.setDestServiceId(source.getDestServiceId());
        metrics.setDestServiceInstanceId(source.getDestServiceInstanceId());
        metrics.setComponentId(source.getComponentId());
        metrics.setEntityId(source.getEntityId());
        MetricsStreamProcessor.getInstance().in(metrics);
    }

    private void clientSide(ServiceInstanceRelation source) {
        ServiceInstanceRelationClientSideMetrics metrics = new ServiceInstanceRelationClientSideMetrics();
        metrics.setTimeBucket(source.getTimeBucket());
        metrics.setSourceServiceId(source.getSourceServiceId());
        metrics.setSourceServiceInstanceId(source.getSourceServiceInstanceId());
        metrics.setDestServiceId(source.getDestServiceId());
        metrics.setDestServiceInstanceId(source.getDestServiceInstanceId());
        metrics.setComponentId(source.getComponentId());
        metrics.setEntityId(source.getEntityId());
        MetricsStreamProcessor.getInstance().in(metrics);
    }
}
