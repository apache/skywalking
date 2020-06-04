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

import org.apache.skywalking.apm.agent.core.meter.adapter.PercentileAdapter;
import org.apache.skywalking.apm.network.language.agent.v3.MeterBucketValue;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterHistogram;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PercentileTransformer extends MeterTransformer<PercentileAdapter> {

    public PercentileTransformer(PercentileAdapter adapter) {
        super(adapter);
    }

    @Override
    public MeterData.Builder transform() {
        final MeterData.Builder builder = MeterData.newBuilder();

        // get all values
        final Map<Double, Long> records = adapter.drainRecords();
        final List<MeterBucketValue> values = records.entrySet().stream()
            .map(e -> MeterBucketValue.newBuilder().setBucket(e.getKey()).setCount(e.getValue()).build())
            .collect(Collectors.toList());

        return builder.setHistogram(MeterHistogram.newBuilder()
            .setName(getName())
            .addAllLabels(transformTags())
            .addAllValues(values)
            .build());
    }
}
