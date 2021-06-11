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

package org.apache.skywalking.apm.agent.core.context.trace;

import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.TracingContext;
import org.apache.skywalking.apm.agent.core.context.tag.AbstractTag;
import org.apache.skywalking.apm.network.trace.component.Component;

/**
 * The <code>ExitSpan</code> represents a service consumer point, such as Feign, Okhttp client for an Http service.
 * <p>
 * It is an exit point or a leaf span(our old name) of trace tree. In a single rpc call, because of a combination of
 * discovery libs, there maybe contain multi-layer exit point:
 * <p>
 * The <code>ExitSpan</code> only presents the first one.
 * <p>
 * Such as: Dubbox - Apache Httpcomponent - ...(Remote) The <code>ExitSpan</code> represents the Dubbox span, and ignore
 * the httpcomponent span's info.
 */
public class ExitSpan extends StackBasedTracingSpan implements ExitTypeSpan {

    public ExitSpan(int spanId, int parentSpanId, String operationName, String peer, TracingContext owner) {
        super(spanId, parentSpanId, operationName, peer, owner);
    }

    public ExitSpan(int spanId, int parentSpanId, String operationName, TracingContext owner) {
        super(spanId, parentSpanId, operationName, owner);
    }

    /**
     * Set the {@link #startTime}, when the first start, which means the first service provided.
     */
    @Override
    public ExitSpan start() {
        if (++stackDepth == 1) {
            super.start();
        }
        return this;
    }

    @Override
    public ExitSpan tag(String key, String value) {
        if (stackDepth == 1 || isInAsyncMode) {
            super.tag(key, value);
        }
        return this;
    }

    @Override
    public AbstractTracingSpan tag(AbstractTag<?> tag, String value) {
        if (stackDepth == 1 || tag.isCanOverwrite() || isInAsyncMode) {
            super.tag(tag, value);
        }
        return this;
    }

    @Override
    public AbstractTracingSpan setLayer(SpanLayer layer) {
        if (stackDepth == 1 || isInAsyncMode) {
            return super.setLayer(layer);
        } else {
            return this;
        }
    }

    @Override
    public AbstractTracingSpan setComponent(Component component) {
        if (stackDepth == 1 || isInAsyncMode) {
            return super.setComponent(component);
        } else {
            return this;
        }
    }

    @Override
    public ExitSpan log(Throwable t) {
        super.log(t);
        return this;
    }

    @Override
    public AbstractTracingSpan setOperationName(String operationName) {
        if (stackDepth == 1 || isInAsyncMode) {
            return super.setOperationName(operationName);
        } else {
            return this;
        }
    }

    @Override
    public String getPeer() {
        return peer;
    }

    @Override
    public ExitSpan inject(final ContextCarrier carrier) {
        this.owner.inject(this, carrier);
        return this;
    }

    @Override
    public boolean isEntry() {
        return false;
    }

    @Override
    public boolean isExit() {
        return true;
    }
}
