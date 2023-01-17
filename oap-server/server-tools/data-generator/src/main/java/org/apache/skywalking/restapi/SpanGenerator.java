/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.restapi;

import java.util.List;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.generator.Generator;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public final class SpanGenerator implements Generator<SpanObject> {
    private Generator<Long> spanId;
    private Generator<Long> parentSpanId;
    private Generator<Long> startTime;
    private Generator<Long> endTime;
    private Generator<String> operationName;
    private Generator<String> peer;
    private Generator<Long> spanLayer;
    private Generator<Long> componentId;
    private Generator<Boolean> error;
    private Generator<List<TagGenerator>> tags;

    @Override
    public SpanObject next() {
        return SpanObject
            .newBuilder()
            .setSpanId(getSpanId().next().intValue())
            .setParentSpanId(getParentSpanId().next().intValue())
            .setStartTime(getStartTime().next())
            .setEndTime(getEndTime().next())
            .setOperationName(getOperationName().next())
            .setPeer(getPeer().next())
            .setSpanLayer(SpanLayer.forNumber(getSpanLayer().next().intValue()))
            .setComponentId(getComponentId().next().intValue())
            .setIsError(getError().next())
            .addAllTags(
                getTags()
                    .next()
                    .stream()
                    .map(TagGenerator::next)
                    .map(it -> KeyStringValuePair
                        .newBuilder().setKey(it.getKey())
                        .setValue(it.getValue()).build())
                    .collect(Collectors.toList()))
            .build();
    }

    @Override
    public void reset() {
        spanId.reset();
        parentSpanId.reset();
        startTime.reset();
        endTime.reset();
        operationName.reset();
        peer.reset();
        spanLayer.reset();
        componentId.reset();
        error.reset();
        tags.reset();
    }
}
