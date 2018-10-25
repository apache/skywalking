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

package org.apache.skywalking.oap.server.core.analysis.manual.servicerelation;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.worker.IndicatorProcess;
import org.apache.skywalking.oap.server.core.source.ServiceRelation;

/**
 * @author wusheng
 */
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
        ServiceRelationServerSideIndicator indicator = new ServiceRelationServerSideIndicator();
        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setSourceServiceId(source.getSourceServiceId());
        indicator.setDestServiceId(source.getDestServiceId());
        indicator.setComponentId(source.getComponentId());
        indicator.setEntityId(source.getEntityId());
        IndicatorProcess.INSTANCE.in(indicator);
    }

    private void clientSide(ServiceRelation source) {
        ServiceRelationClientSideIndicator indicator = new ServiceRelationClientSideIndicator();
        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setSourceServiceId(source.getSourceServiceId());
        indicator.setDestServiceId(source.getDestServiceId());
        indicator.setComponentId(source.getComponentId());
        indicator.setEntityId(source.getEntityId());
        IndicatorProcess.INSTANCE.in(indicator);
    }
}
