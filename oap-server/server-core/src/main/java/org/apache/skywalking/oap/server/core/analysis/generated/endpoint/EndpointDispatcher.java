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

package org.apache.skywalking.oap.server.core.analysis.generated.endpoint;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.worker.IndicatorProcess;
import org.apache.skywalking.oap.server.core.source.*;

/**
 * This class is auto generated. Please don't change this class manually.
 *
 * @author Observability Analysis Language code generator
 */
public class EndpointDispatcher implements SourceDispatcher<Endpoint> {

    @Override public void dispatch(Endpoint source) {
        doEndpointAvg(source);
        doEndpointPercent(source);
    }

    private void doEndpointAvg(Endpoint source) {
        EndpointAvgIndicator indicator = new EndpointAvgIndicator();


        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setId(source.getId());
        indicator.setServiceId(source.getServiceId());
        indicator.setServiceInstanceId(source.getServiceInstanceId());
        indicator.combine(source.getLatency(), 1);
        IndicatorProcess.INSTANCE.in(indicator);
    }

    private void doEndpointPercent(Endpoint source) {
        EndpointPercentIndicator indicator = new EndpointPercentIndicator();


        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setId(source.getId());
        indicator.setServiceId(source.getServiceId());
        indicator.setServiceInstanceId(source.getServiceInstanceId());
        indicator.combine(new org.apache.skywalking.oap.server.core.analysis.indicator.expression.EqualMatch(), source.isStatus(), true);
        IndicatorProcess.INSTANCE.in(indicator);
    }

}
