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
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.generator.Generator;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public final class SpanGenerator implements Generator<SpanGenerator.SpanGeneratorContext, SpanObject> {
    private Generator<Object, Long> endTime;
    private Generator<Object, Long> latency;
    private Generator<String, String> operationName;
    private Generator<String, String> peer;
    private Generator<Object, Long> spanLayer;
    private Generator<Object, Long> componentId;
    private Generator<Object, Boolean> error;
    private Generator<Object, List<TagGenerator>> tags;

    @Override
    public SpanObject next(SpanGenerator.SpanGeneratorContext ctx) {
        long now;
        if (endTime == null) {
            now = System.currentTimeMillis();
        } else {
            now = endTime.next(null);
        }
        SpanObject.Builder sob = SpanObject
            .newBuilder()
            .setSpanId(ctx.index)
            .setParentSpanId(ctx.index - 1)
            .setStartTime(now - latency.next(null))
            .setEndTime(now)
            .setComponentId(getComponentId().next(null).intValue())
            .setIsError(getError().next(null))
            .addAllTags(
                getTags()
                    .next(null)
                    .stream()
                    .map(tg -> tg.next(null))
                    .map(it -> KeyStringValuePair
                        .newBuilder().setKey(it.getKey())
                        .setValue(it.getValue()).build())
                    .collect(Collectors.toList()));
        if (ctx.index == 0) {
            sob.setSpanLayer(SpanLayer.forNumber(getSpanLayer().next(null).intValue()))
                    .setSpanType(SpanType.Entry);
            if (ctx.ref != null) {
                sob.addRefs(ctx.ref);
            }
        } else if (ctx.length > 1 && ctx.index == ctx.length - 1) {
            sob.setSpanType(SpanType.Exit).setPeer(getPeer().next(null)).setSpanLayer(SpanLayer.Database);
        } else {
            sob.setSpanLayer(SpanLayer.forNumber(getSpanLayer().next(null).intValue()))
                    .setSpanType(SpanType.Local);
        }
        return sob.build();
    }

    @Override
    public void reset() {
        operationName.reset();
        peer.reset();
        spanLayer.reset();
        componentId.reset();
        error.reset();
        tags.reset();
    }

    @RequiredArgsConstructor
    public static class SpanGeneratorContext {
        final int index;
        final int length;
        final SegmentReference ref;
    }
}
