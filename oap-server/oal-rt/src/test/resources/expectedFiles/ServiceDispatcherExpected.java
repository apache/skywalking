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
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.EqualMatch;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.GreaterMatch;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.source.Service;

/**
 * This class is auto generated. Please don't change this class manually.
 */
public class ServiceDispatcher implements SourceDispatcher<Service> {

    @Override
    public void dispatch(Service source) {
        doServiceAvg(source);
    }

    private void doServiceAvg(Service source) {
        ServiceAvgMetrics metrics = new ServiceAvgMetrics();

        if (!new EqualMatch().setLeft(source.getName()).setRight("/service/prod/save").match()) {
            return;
        }
        if (!new GreaterMatch().match(source.getLatency(), 1000)) {
            return;
        }

        metrics.setTimeBucket(source.getTimeBucket());
        metrics.setEntityId(source.getEntityId());
        metrics.combine(source.getLatency(), 1);

        MetricsStreamProcessor.getInstance().in(metrics);
    }
}
