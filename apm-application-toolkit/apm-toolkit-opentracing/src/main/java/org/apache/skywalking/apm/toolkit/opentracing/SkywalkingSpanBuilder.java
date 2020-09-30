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
import io.opentracing.BaseSpan;
import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import java.util.ArrayList;
import java.util.List;

public class SkywalkingSpanBuilder implements Tracer.SpanBuilder {
    private List<Tag> tags = new ArrayList<Tag>();
    private String operationName;
    private boolean isEntry = false;
    private boolean isExit = false;
    private int port;
    private String peer;
    private boolean isError = false;
    private long startTime;

    public SkywalkingSpanBuilder(String operationName) {
        this.operationName = operationName;
    }

    @Override
    public Tracer.SpanBuilder asChildOf(SpanContext parent) {
        if (parent instanceof SkywalkingContext) {
            return this;
        }
        throw new IllegalArgumentException("parent must be type of SpanContext");
    }

    @Override
    public Tracer.SpanBuilder asChildOf(BaseSpan<?> parent) {
        if (parent instanceof SkywalkingSpan || parent instanceof SkywalkingActiveSpan) {
            return this;
        }
        throw new IllegalArgumentException("parent must be type of SkywalkingSpan");
    }

    /**
     * Ignore the reference type. the span always the entry or has a parent span.
     */
    @Override
    public Tracer.SpanBuilder addReference(String referenceType, SpanContext referencedContext) {
        if (References.FOLLOWS_FROM.equals(referenceType)) {
            throw new IllegalArgumentException("only support CHILD_OF reference");
        }
        return asChildOf(referencedContext);
    }

    @Override
    public Tracer.SpanBuilder withTag(String key, String value) {
        if (Tags.SPAN_KIND.getKey().equals(key)) {
            if (Tags.SPAN_KIND_CLIENT.equals(value) || Tags.SPAN_KIND_PRODUCER.equals(value)) {
                isEntry = false;
                isExit = true;
            } else if (Tags.SPAN_KIND_SERVER.equals(value) || Tags.SPAN_KIND_CONSUMER.equals(value)) {
                isEntry = true;
                isExit = false;
            } else {
                isEntry = false;
                isExit = false;
            }
        } else if (Tags.PEER_HOST_IPV4.getKey().equals(key) ||
            Tags.PEER_HOST_IPV6.getKey().equals(key) || Tags.PEER_HOSTNAME.getKey().equals(key)) {
            peer = value;
        } else if (Tags.PEER_SERVICE.getKey().equals(key)) {
            operationName = value;
        } else {
            tags.add(new Tag(key, value));
        }
        return this;
    }

    @Override
    public Tracer.SpanBuilder withTag(String key, boolean value) {
        if (Tags.ERROR.getKey().equals(key)) {
            isError = value;
        } else {
            tags.add(new Tag(key, value ? "true" : "false"));
        }
        return this;
    }

    @Override
    public Tracer.SpanBuilder withTag(String key, Number value) {
        if (Tags.PEER_PORT.getKey().equals(key)) {
            port = value.intValue();
        } else {
            tags.add(new Tag(key, value.toString()));
        }
        return this;
    }

    @Override
    public Tracer.SpanBuilder withStartTimestamp(long microseconds) {
        startTime = microseconds;
        return this;
    }

    @Override
    public ActiveSpan startActive() {
        return new SkywalkingActiveSpan(new SkywalkingSpan(this));
    }

    @Override
    public Span startManual() {
        return new SkywalkingSpan(this);
    }

    @Override
    @Deprecated
    public Span start() {
        return startManual();
    }

    /**
     * All the get methods are for accessing data from activation
     */
    public List<Tag> getTags() {
        return tags;
    }

    public String getOperationName() {
        return operationName;
    }

    public boolean isEntry() {
        return isEntry;
    }

    public boolean isExit() {
        return isExit;
    }

    public int getPort() {
        return port;
    }

    public String getPeer() {
        return peer;
    }

    public boolean isError() {
        return isError;
    }

    /**
     * All the following methods are needed for activation.
     */
    @Override
    @NeedSnifferActivation("Stop the active span.")
    public Tracer.SpanBuilder ignoreActiveSpan() {
        return this;
    }
}
