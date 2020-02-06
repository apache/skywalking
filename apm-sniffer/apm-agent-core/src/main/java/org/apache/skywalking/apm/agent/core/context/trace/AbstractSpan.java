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

import java.util.Map;
import org.apache.skywalking.apm.agent.core.context.AsyncSpan;
import org.apache.skywalking.apm.agent.core.context.tag.AbstractTag;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.network.trace.component.Component;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * The <code>AbstractSpan</code> represents the span's skeleton, which contains all open methods.
 *
 * @author wusheng
 */
public interface AbstractSpan extends AsyncSpan {
    /**
     * Set the component id, which defines in {@link ComponentsDefine}
     *
     * @param component
     * @return the span for chaining.
     */
    AbstractSpan setComponent(Component component);

    /**
     * Only use this method in explicit instrumentation, like opentracing-skywalking-bridge. It is highly recommended
     * not to use this method for performance reasons.
     *
     * @param componentName
     * @return the span for chaining.
     */
    AbstractSpan setComponent(String componentName);

    AbstractSpan setLayer(SpanLayer layer);

    /**
     * Set a key:value tag on the Span.
     *
     * @return this Span instance, for chaining
     * @deprecated use {@link #tag(AbstractTag, String)} in companion with {@link Tags#ofKey(String)} instead
     */
    @Deprecated
    AbstractSpan tag(String key, String value);

    /**
     * @param tag
     * @param value
     * @return
     */
    AbstractSpan tag(AbstractTag<?> tag, String value);

    /**
     * Record an exception event of the current walltime timestamp.
     *
     * @param t any subclass of {@link Throwable}, which occurs in this span.
     * @return the Span, for chaining
     */
    AbstractSpan log(Throwable t);

    AbstractSpan errorOccurred();

    /**
     * @return true if the actual span is an entry span.
     */
    boolean isEntry();

    /**
     * @return true if the actual span is an exit span.
     */
    boolean isExit();

    /**
     * Record an event at a specific timestamp.
     *
     * @param timestamp The explicit timestamp for the log record.
     * @param event the events
     * @return the Span, for chaining
     */
    AbstractSpan log(long timestamp, Map<String, ?> event);

    /**
     * Sets the string name for the logical operation this span represents.
     *
     * @return this Span instance, for chaining
     */
    AbstractSpan setOperationName(String operationName);

    /**
     * Start a span.
     *
     * @return this Span instance, for chaining
     */
    AbstractSpan start();

    /**
     * Get the id of span
     *
     * @return id value.
     */
    int getSpanId();

    int getOperationId();

    String getOperationName();

    AbstractSpan setOperationId(int operationId);

    /**
     * Reference other trace segment.
     *
     * @param ref segment ref
     */
    void ref(TraceSegmentRef ref);

    AbstractSpan start(long startTime);

    AbstractSpan setPeer(String remotePeer);
}
