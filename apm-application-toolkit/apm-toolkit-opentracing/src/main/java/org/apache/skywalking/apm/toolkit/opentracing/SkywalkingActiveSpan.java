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
import io.opentracing.SpanContext;
import java.util.Map;

/**
 * The <code>SkywalkingActiveSpan</code> is an extension of {@link SkywalkingSpan}, but because of Java inheritance
 * restrict, only can do with a facade mode.
 */
public class SkywalkingActiveSpan implements ActiveSpan {
    private SkywalkingSpan span;

    public SkywalkingActiveSpan(SkywalkingSpan span) {
        this.span = span;
    }

    @Override
    public void deactivate() {
        span.finish();
    }

    @Override
    public void close() {
        this.deactivate();
    }

    @Override
    public Continuation capture() {
        return new SkywalkingContinuation();
    }

    @Override
    public SpanContext context() {
        return span.context();
    }

    @Override
    public ActiveSpan setTag(String key, String value) {
        span.setTag(key, value);
        return this;
    }

    @Override
    public ActiveSpan setTag(String key, boolean value) {
        span.setTag(key, value);
        return this;
    }

    @Override
    public ActiveSpan setTag(String key, Number value) {
        span.setTag(key, value);
        return this;
    }

    @Override
    public ActiveSpan log(Map<String, ?> fields) {
        span.log(fields);
        return this;
    }

    @Override
    public ActiveSpan log(long timestampMicroseconds, Map<String, ?> fields) {
        span.log(timestampMicroseconds, fields);
        return this;
    }

    @Override
    public ActiveSpan log(String event) {
        span.log(event);
        return this;
    }

    @Override
    public ActiveSpan log(long timestampMicroseconds, String event) {
        span.log(timestampMicroseconds, event);
        return this;
    }

    /**
     * Don't support baggage item.
     */
    @Override
    public ActiveSpan setBaggageItem(String key, String value) {
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

    @Override
    public ActiveSpan setOperationName(String operationName) {
        span.setOperationName(operationName);
        return this;
    }

    /**
     * Don't support logging with payload.
     */
    @Deprecated
    @Override
    public ActiveSpan log(String eventName, Object payload) {
        return this;
    }

    /**
     * Don't support logging with payload.
     */
    @Deprecated
    @Override
    public ActiveSpan log(long timestampMicroseconds, String eventName, Object payload) {
        return this;
    }
}
