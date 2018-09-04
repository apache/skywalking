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

package org.apache.skywalking.oap.server.core.analysis.generated.endpointrelation;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.worker.IndicatorProcess;
import org.apache.skywalking.oap.server.core.source.*;

/**
 * This class is auto generated. Please don't change this class manually.
 *
 * @author Observability Analysis Language code generator
 */
public class EndpointRelationDispatcher implements SourceDispatcher<EndpointRelation> {

    @Override public void dispatch(EndpointRelation source) {
        doEndpointRelationAvg(source);
    }

    private void doEndpointRelationAvg(EndpointRelation source) {
        EndpointRelationAvgIndicator indicator = new EndpointRelationAvgIndicator();


        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setEndpointId(source.getEndpointId());
        indicator.setChildEndpointId(source.getChildEndpointId());
        indicator.setServiceId(source.getServiceId());
        indicator.setChildServiceId(source.getChildServiceId());
        indicator.setServiceInstanceId(source.getServiceInstanceId());
        indicator.setChildServiceInstanceId(source.getChildServiceInstanceId());
        indicator.combine(source.getRpcLatency(), 1);
        IndicatorProcess.INSTANCE.in(indicator);
    }
}
