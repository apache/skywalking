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

package org.apache.skywalking.oap.server.analyzer.provider.meter.process;

import com.google.common.collect.ImmutableMap;
import lombok.Builder;
import org.apache.skywalking.oap.meter.analyzer.dsl.Sample;

/**
 * Help to build Sample with agent side meter.
 */
@Builder
public class SampleBuilder {

    final String name;
    final ImmutableMap<String, String> labels;
    final double value;

    public Sample build(String service, String instance, long timestamp) {
        return Sample.builder()
            .name(name)
            .labels(ImmutableMap.<String, String>builder()
                // Put original labels
                .putAll(labels)
                // Put report service and instance to labels
                .put("service", service)
                .put("instance", instance)
                .build())
            .value(value)
            .timestamp(timestamp).build();
    }
}
