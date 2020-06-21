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

package org.apache.skywalking.apm.agent.core.meter.transform;

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.meter.MeterService;
import org.apache.skywalking.apm.agent.core.meter.adapter.CounterAdapter;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterSingleValue;

public class CounterTransformer extends MeterTransformer<CounterAdapter> {
    private static MeterService METER_SERVICE;

    public CounterTransformer(CounterAdapter adapter) {
        super(adapter);
    }

    @Override
    public MeterData.Builder transform() {
        if (METER_SERVICE == null) {
            METER_SERVICE = ServiceManager.INSTANCE.findService(MeterService.class);
        }

        final MeterData.Builder builder = MeterData.newBuilder();
        builder.setSingleValue(MeterSingleValue.newBuilder()
            .setName(getName())
            .addAllLabels(transformTags())
            .setValue(adapter.getCount()).build());

        return builder;
    }

}
