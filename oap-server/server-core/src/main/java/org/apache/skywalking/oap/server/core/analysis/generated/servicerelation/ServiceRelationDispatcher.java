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

package org.apache.skywalking.oap.server.core.analysis.generated.servicerelation;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.worker.IndicatorProcess;
import org.apache.skywalking.oap.server.core.analysis.indicator.expression.*;
import org.apache.skywalking.oap.server.core.source.*;

/**
 * This class is auto generated. Please don't change this class manually.
 *
 * @author Observability Analysis Language code generator
 */
public class ServiceRelationDispatcher implements SourceDispatcher<ServiceRelation> {

    @Override public void dispatch(ServiceRelation source) {
        doServiceRelationClientCpm(source);
        doServiceRelationServerCpm(source);
        doServiceRelationClientCallSla(source);
        doServiceRelationServerCallSla(source);
        doServiceRelationClientRespTime(source);
        doServiceRelationServerRespTime(source);
    }

    private void doServiceRelationClientCpm(ServiceRelation source) {
        ServiceRelationClientCpmIndicator indicator = new ServiceRelationClientCpmIndicator();

        if (!new EqualMatch().setLeft(source.getDetectPoint()).setRight(DetectPoint.CLIENT).match()) {
            return;
        }

        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setEntityId(source.getEntityId());
        indicator.combine(1);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doServiceRelationServerCpm(ServiceRelation source) {
        ServiceRelationServerCpmIndicator indicator = new ServiceRelationServerCpmIndicator();

        if (!new EqualMatch().setLeft(source.getDetectPoint()).setRight(DetectPoint.SERVER).match()) {
            return;
        }

        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setEntityId(source.getEntityId());
        indicator.combine(1);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doServiceRelationClientCallSla(ServiceRelation source) {
        ServiceRelationClientCallSlaIndicator indicator = new ServiceRelationClientCallSlaIndicator();

        if (!new EqualMatch().setLeft(source.getDetectPoint()).setRight(DetectPoint.CLIENT).match()) {
            return;
        }

        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setEntityId(source.getEntityId());
        indicator.combine(new org.apache.skywalking.oap.server.core.analysis.indicator.expression.EqualMatch(), source.isStatus(), true);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doServiceRelationServerCallSla(ServiceRelation source) {
        ServiceRelationServerCallSlaIndicator indicator = new ServiceRelationServerCallSlaIndicator();

        if (!new EqualMatch().setLeft(source.getDetectPoint()).setRight(DetectPoint.SERVER).match()) {
            return;
        }

        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setEntityId(source.getEntityId());
        indicator.combine(new org.apache.skywalking.oap.server.core.analysis.indicator.expression.EqualMatch(), source.isStatus(), true);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doServiceRelationClientRespTime(ServiceRelation source) {
        ServiceRelationClientRespTimeIndicator indicator = new ServiceRelationClientRespTimeIndicator();

        if (!new EqualMatch().setLeft(source.getDetectPoint()).setRight(DetectPoint.CLIENT).match()) {
            return;
        }

        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setEntityId(source.getEntityId());
        indicator.combine(source.getLatency(), 1);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doServiceRelationServerRespTime(ServiceRelation source) {
        ServiceRelationServerRespTimeIndicator indicator = new ServiceRelationServerRespTimeIndicator();

        if (!new EqualMatch().setLeft(source.getDetectPoint()).setRight(DetectPoint.SERVER).match()) {
            return;
        }

        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setEntityId(source.getEntityId());
        indicator.combine(source.getLatency(), 1);
        IndicatorProcess.INSTANCE.in(indicator);
    }
}
