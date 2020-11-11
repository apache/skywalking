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

package org.apache.skywalking.apm.toolkit.activation.meter;

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.meter.Histogram;
import org.apache.skywalking.apm.agent.core.meter.MeterService;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.toolkit.activation.meter.util.MeterIdConverter;
import org.apache.skywalking.apm.toolkit.meter.MeterId;

import java.util.List;

public class HistogramConstructInterceptor implements InstanceConstructorInterceptor {
    private static MeterService METER_SERVICE;

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        final MeterId meterId = (MeterId) allArguments[0];
        final List<Double> steps = (List<Double>) allArguments[1];

        final Histogram histogram = new Histogram(MeterIdConverter.convert(meterId), steps);

        // register the meter
        if (METER_SERVICE == null) {
            METER_SERVICE = ServiceManager.INSTANCE.findService(MeterService.class);
        }
        objInst.setSkyWalkingDynamicField(METER_SERVICE.register(histogram));
    }

}
