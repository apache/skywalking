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

package org.apache.skywalking.apm.toolkit.opentracing;

import io.opentracing.ActiveSpan;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;

public class SkywalkingTracer implements Tracer {

    public SpanBuilder buildSpan(String operationName) {
        return new SkywalkingSpanBuilder(operationName);
    }

    @NeedSnifferActivation
    @Override
    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {

    }

    @NeedSnifferActivation
    @Override
    public <C> SpanContext extract(Format<C> format, C carrier) {
        return new SkywalkingContext();
    }

    @Override
    public ActiveSpan activeSpan() {
        return new SkywalkingActiveSpan(new SkywalkingSpan(this));
    }

    @Override
    public ActiveSpan makeActive(Span span) {
        if (span instanceof SkywalkingSpan) {
            return new SkywalkingActiveSpan((SkywalkingSpan) span);
        } else {
            throw new IllegalArgumentException("span must be a type of SkywalkingSpan");
        }
    }
}
