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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import org.apache.skywalking.generator.Generator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public final class SegmentRequest implements Generator<Object, List<SegmentGenerator.SegmentResult>> {
    private Generator<String, String> traceId;
    private Generator<String, String> serviceName;
    private Generator<String, String> serviceInstanceName;
    private Generator<Object, List<SegmentGenerator>> segments;

    @Override
    public List<SegmentGenerator.SegmentResult> next(Object ignored) {
        final String traceId = getTraceId().next(null);
        final List<SegmentGenerator> segments = getSegments().next(traceId);
        SegmentGenerator.SegmentResult last = null;
        List<SegmentGenerator.SegmentContext> context = IntStream.range(0, segments.size()).mapToObj(i -> {
            String serviceName = getServiceName().next(null);
            return new SegmentGenerator.SegmentContext(traceId, serviceName, getServiceInstanceName().next(serviceName));
        }).collect(Collectors.toList());
        List<SegmentGenerator.SegmentResult> result = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            SegmentGenerator.SegmentContext ctx = context.get(i);
            ctx.parentSegment = last;
            if (i < segments.size() - 1) {
                ctx.peer = context.get(i + 1).serviceInstanceName;
            }
            last = segments.get(i).next(ctx);
            result.add(last);
        }

        return result;
    }

}
