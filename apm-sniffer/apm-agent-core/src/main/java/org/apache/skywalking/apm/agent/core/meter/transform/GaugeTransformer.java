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

import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.meter.adapter.GaugeAdapter;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterSingleValue;

public class GaugeTransformer extends MeterTransformer<GaugeAdapter> {
    private static final ILog logger = LogManager.getLogger(GaugeTransformer.class);

    public GaugeTransformer(GaugeAdapter adapter) {
        super(adapter);
    }

    @Override
    public MeterData.Builder transform() {
        // get count
        Double count;
        try {
            count = adapter.getCount();
        } catch (Exception e) {
            logger.warn(e, "Cannot get the count in meter:{}", adapter.getId().getName());
            return null;
        }

        if (count == null) {
            return null;
        }

        final MeterData.Builder builder = MeterData.newBuilder();
        builder.setSingleValue(MeterSingleValue.newBuilder()
            .setName(getName())
            .addAllLabels(transformTags())
            .setValue(count).build());

        return builder;
    }
}
