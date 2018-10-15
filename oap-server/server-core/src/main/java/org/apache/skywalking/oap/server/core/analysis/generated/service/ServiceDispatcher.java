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

package org.apache.skywalking.oap.server.core.analysis.generated.service;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.worker.IndicatorProcess;
import org.apache.skywalking.oap.server.core.source.Service;

/**
 * This class is auto generated. Please don't change this class manually.
 *
 * @author Observability Analysis Language code generator
 */
public class ServiceDispatcher implements SourceDispatcher<Service> {

    @Override public void dispatch(Service source) {
        doServiceRespTime(source);
        doServiceSla(source);
        doServiceCpm(source);
        doServiceP99(source);
        doServiceP95(source);
        doServiceP90(source);
        doServiceP75(source);
        doServiceP50(source);
    }

    private void doServiceRespTime(Service source) {
        ServiceRespTimeIndicator indicator = new ServiceRespTimeIndicator();


        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setEntityId(source.getEntityId());
        indicator.combine(source.getLatency(), 1);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doServiceSla(Service source) {
        ServiceSlaIndicator indicator = new ServiceSlaIndicator();


        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setEntityId(source.getEntityId());
        indicator.combine(new org.apache.skywalking.oap.server.core.analysis.indicator.expression.EqualMatch(), source.isStatus(), true);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doServiceCpm(Service source) {
        ServiceCpmIndicator indicator = new ServiceCpmIndicator();


        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setEntityId(source.getEntityId());
        indicator.combine(1);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doServiceP99(Service source) {
        ServiceP99Indicator indicator = new ServiceP99Indicator();


        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setEntityId(source.getEntityId());
        indicator.combine(source.getLatency(), 10);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doServiceP95(Service source) {
        ServiceP95Indicator indicator = new ServiceP95Indicator();


        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setEntityId(source.getEntityId());
        indicator.combine(source.getLatency(), 10);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doServiceP90(Service source) {
        ServiceP90Indicator indicator = new ServiceP90Indicator();


        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setEntityId(source.getEntityId());
        indicator.combine(source.getLatency(), 10);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doServiceP75(Service source) {
        ServiceP75Indicator indicator = new ServiceP75Indicator();


        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setEntityId(source.getEntityId());
        indicator.combine(source.getLatency(), 10);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doServiceP50(Service source) {
        ServiceP50Indicator indicator = new ServiceP50Indicator();


        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setEntityId(source.getEntityId());
        indicator.combine(source.getLatency(), 10);
        IndicatorProcess.INSTANCE.in(indicator);
    }
}
