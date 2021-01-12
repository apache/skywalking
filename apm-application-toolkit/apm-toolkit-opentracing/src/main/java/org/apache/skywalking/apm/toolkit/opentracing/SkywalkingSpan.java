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

import io.opentracing.Span;
import io.opentracing.SpanContext;
import java.util.HashMap;
import java.util.Map;

public class SkywalkingSpan implements Span {
    @NeedSnifferActivation("1.ContextManager#createSpan (Entry,Exit,Local based on builder)." + "2.set the span reference to the dynamic field of enhanced SkywalkingSpan")
    SkywalkingSpan(SkywalkingSpanBuilder builder) {
    }

    /**
     * Create a shell span for {@link SkywalkingTracer#activeSpan()}
     */
    @NeedSnifferActivation("1. set the span reference to the dynamic field of enhanced SkywalkingSpan")
    public SkywalkingSpan(SkywalkingTracer tracer) {

    }

    @NeedSnifferActivation("Override span's operationName, which has been given at ")
    @Override
    public Span setOperationName(String operationName) {
        return this;
    }

    @NeedSnifferActivation("AbstractTracingSpan#log(long timestampMicroseconds, Map<String, ?> fields)")
    @Override
    public Span log(long timestampMicroseconds, Map<String, ?> fields) {
        return this;
    }

    /**
     * Stop the active span
     */
    @NeedSnifferActivation("1.ContextManager#stopSpan(AbstractSpan span)" + "2. The parameter of stop methed is from the dynamic field of enhanced SkywalkingSpan")
    @Override
    public void finish(long finishMicros) {

    }

    @Override
    public Span log(long timestampMicroseconds, String event) {
        Map<String, String> eventMap = new HashMap<String, String>(1);
        eventMap.put("event", event);
        return log(timestampMicroseconds, eventMap);
    }

    @Override
    public void finish() {
        this.finish(System.currentTimeMillis());
    }

    @Override
    public SpanContext context() {
        return SkywalkingContext.INSTANCE;
    }

    @NeedSnifferActivation("1. ContextManager#activeSpan()" + "2. SkywalkingSpan#setTag(String, String)")
    @Override
    public Span setTag(String key, String value) {
        return this;
    }

    @Override
    public Span setTag(String key, boolean value) {
        return setTag(key, String.valueOf(value));
    }

    @Override
    public Span setTag(String key, Number value) {
        return setTag(key, String.valueOf(value));
    }

    @Override
    public Span log(Map<String, ?> fields) {
        return log(System.currentTimeMillis(), fields);
    }

    @Override
    public Span log(String event) {
        return log(System.currentTimeMillis(), event);
    }

    /**
     * Don't support baggage item.
     */
    @Override
    public Span setBaggageItem(String key, String value) {
        return this;
    }

    /**
     * Don't support baggage item.
     *
     * @return null, always.
     */
    @Override
    public String getBaggageItem(String key) {
        return null;
    }

    /**
     * Don't support logging with payload.
     */
    @Deprecated
    @Override
    public Span log(String eventName, Object payload) {
        return this;
    }

    /**
     * Don't support logging with payload.
     */
    @Deprecated
    @Override
    public Span log(long timestampMicroseconds, String eventName, Object payload) {
        return this;
    }
}
