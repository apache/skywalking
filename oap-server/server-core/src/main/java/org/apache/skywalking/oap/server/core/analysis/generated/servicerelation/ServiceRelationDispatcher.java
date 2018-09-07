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
import org.apache.skywalking.oap.server.core.analysis.indicator.expression.EqualMatch;
import org.apache.skywalking.oap.server.core.analysis.worker.IndicatorProcess;
import org.apache.skywalking.oap.server.core.source.*;

/**
 * This class is auto generated. Please don't change this class manually.
 *
 * @author Observability Analysis Language code generator
 */
public class ServiceRelationDispatcher implements SourceDispatcher<ServiceRelation> {

    @Override public void dispatch(ServiceRelation source) {
        doServiceRelationClientCallsSum(source);
        doServiceRelationServerCallsSum(source);
        doServiceRelationAvg(source);
    }

    private void doServiceRelationClientCallsSum(ServiceRelation source) {
        ServiceRelationClientCallsSumIndicator indicator = new ServiceRelationClientCallsSumIndicator();

        if (!new EqualMatch().setLeft(source.getDetectPoint()).setRight(DetectPoint.CLIENT).match()) {
            return;
        }

        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setSourceServiceId(source.getSourceServiceId());
        indicator.setDestServiceId(source.getDestServiceId());
        indicator.combine(1);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doServiceRelationServerCallsSum(ServiceRelation source) {
        ServiceRelationServerCallsSumIndicator indicator = new ServiceRelationServerCallsSumIndicator();

        if (!new EqualMatch().setLeft(source.getDetectPoint()).setRight(DetectPoint.SERVER).match()) {
            return;
        }

        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setSourceServiceId(source.getSourceServiceId());
        indicator.setDestServiceId(source.getDestServiceId());
        indicator.combine(1);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doServiceRelationAvg(ServiceRelation source) {
        ServiceRelationAvgIndicator indicator = new ServiceRelationAvgIndicator();


        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setSourceServiceId(source.getSourceServiceId());
        indicator.setDestServiceId(source.getDestServiceId());
        indicator.combine(source.getLatency(), 1);
        IndicatorProcess.INSTANCE.in(indicator);
    }
}
