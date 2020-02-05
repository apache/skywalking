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

import java.util.*;
import org.apache.skywalking.apm.agent.core.context.*;
import org.apache.skywalking.apm.agent.core.context.tag.*;
import org.apache.skywalking.apm.agent.core.context.util.*;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.apache.skywalking.apm.network.language.agent.SpanType;
import org.apache.skywalking.apm.network.language.agent.v2.SpanObjectV2;
import org.apache.skywalking.apm.network.trace.component.Component;

/**
 * The <code>AbstractTracingSpan</code> represents a group of {@link AbstractSpan} implementations, which belongs a real
 * distributed trace.
 *
 * @author wusheng
 */
public abstract class AbstractTracingSpan implements AbstractSpan {
    protected int spanId;
    protected int parentSpanId;
    protected List<TagValuePair> tags;
    protected String operationName;
    protected int operationId;
    protected SpanLayer layer;
    /**
     * The span has been tagged in async mode, required async stop to finish.
     */
    protected volatile boolean isInAsyncMode = false;
    /**
     * The flag represents whether the span has been async stopped
     */
    private volatile boolean isAsyncStopped = false;

    /**
     * The context to which the span belongs
     */
    protected final TracingContext owner;

    /**
     * The start time of this Span.
     */
    protected long startTime;
    /**
     * The end time of this Span.
     */
    protected long endTime;
    /**
     * Error has occurred in the scope of span.
     */
    protected boolean errorOccurred = false;

    protected int componentId = 0;

    protected String componentName;

    /**
     * Log is a concept from OpenTracing spec. https://github.com/opentracing/specification/blob/master/specification.md#log-structured-data
     */
    protected List<LogDataEntity> logs;

    /**
     * The refs of parent trace segments, except the primary one. For most RPC call, {@link #refs} contains only one
     * element, but if this segment is a start span of batch process, the segment faces multi parents, at this moment,
     * we use this {@link #refs} to link them.
     */
    protected List<TraceSegmentRef> refs;

    protected AbstractTracingSpan(int spanId, int parentSpanId, String operationName, TracingContext owner) {
        this.operationName = operationName;
        this.operationId = DictionaryUtil.nullValue();
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.owner = owner;
    }

    protected AbstractTracingSpan(int spanId, int parentSpanId, int operationId, TracingContext owner) {
        this.operationName = null;
        this.operationId = operationId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.owner = owner;
    }

    /**
     * Set a key:value tag on the Span.
     *
     * {@inheritDoc}
     * @return this Span instance, for chaining
     */
    @Override
    public AbstractTracingSpan tag(String key, String value) {
        return tag(Tags.ofKey(key), value);
    }

    @Override
    public AbstractTracingSpan tag(AbstractTag<?> tag, String value) {
        if (tags == null) {
            tags = new ArrayList<>(8);
        }

        if (tag.isCanOverwrite()) {
            for (TagValuePair pair : tags) {
                if (pair.sameWith(tag)) {
                    pair.setValue(value);
                    return this;
                }
            }
        }

        tags.add(new TagValuePair(tag, value));
        return this;
    }

    /**
     * Finish the active Span. When it is finished, it will be archived by the given {@link TraceSegment}, which owners
     * it.
     *
     * @param owner of the Span.
     */
    public boolean finish(TraceSegment owner) {
        this.endTime = System.currentTimeMillis();
        owner.archive(this);
        return true;
    }

    @Override
    public AbstractTracingSpan start() {
        this.startTime = System.currentTimeMillis();
        return this;
    }

    /**
     * Record an exception event of the current walltime timestamp.
     *
     * @param t any subclass of {@link Throwable}, which occurs in this span.
     * @return the Span, for chaining
     */
    @Override
    public AbstractTracingSpan log(Throwable t) {
        if (logs == null) {
            logs = new LinkedList<>();
        }
        logs.add(new LogDataEntity.Builder()
            .add(new KeyValuePair("event", "error"))
            .add(new KeyValuePair("error.kind", t.getClass().getName()))
            .add(new KeyValuePair("message", t.getMessage()))
            .add(new KeyValuePair("stack", ThrowableTransformer.INSTANCE.convert2String(t, 4000)))
            .build(System.currentTimeMillis()));
        return this;
    }

    /**
     * Record a common log with multi fields, for supporting opentracing-java
     *
     * @param fields
     * @return the Span, for chaining
     */
    @Override
    public AbstractTracingSpan log(long timestampMicroseconds, Map<String, ?> fields) {
        if (logs == null) {
            logs = new LinkedList<>();
        }
        LogDataEntity.Builder builder = new LogDataEntity.Builder();
        for (Map.Entry<String, ?> entry : fields.entrySet()) {
            builder.add(new KeyValuePair(entry.getKey(), entry.getValue().toString()));
        }
        logs.add(builder.build(timestampMicroseconds));
        return this;
    }

    /**
     * In the scope of this span tracing context, error occurred, in auto-instrumentation mechanism, almost means throw
     * an exception.
     *
     * @return span instance, for chaining.
     */
    @Override
    public AbstractTracingSpan errorOccurred() {
        this.errorOccurred = true;
        return this;
    }

    /**
     * Set the operation name, just because these is not compress dictionary value for this name. Use the entire string
     * temporarily, the agent will compress this name in async mode.
     *
     * @param operationName
     * @return span instance, for chaining.
     */
    @Override
    public AbstractTracingSpan setOperationName(String operationName) {
        this.operationName = operationName;
        this.operationId = DictionaryUtil.nullValue();

        // recheck profiling status
        owner.profilingRecheck(this, operationName);
        return this;
    }

    /**
     * Set the operation id, which compress by the name.
     *
     * @param operationId
     * @return span instance, for chaining.
     */
    @Override
    public AbstractTracingSpan setOperationId(int operationId) {
        this.operationId = operationId;
        this.operationName = null;
        return this;
    }

    @Override
    public int getSpanId() {
        return spanId;
    }

    @Override
    public int getOperationId() {
        return operationId;
    }

    @Override
    public String getOperationName() {
        return operationName;
    }

    @Override
    public AbstractTracingSpan setLayer(SpanLayer layer) {
        this.layer = layer;
        return this;
    }

    /**
     * Set the component of this span, with internal supported. Highly recommend to use this way.
     *
     * @param component
     * @return span instance, for chaining.
     */
    @Override
    public AbstractTracingSpan setComponent(Component component) {
        this.componentId = component.getId();
        return this;
    }

    /**
     * Set the component name. By using this, cost more memory and network.
     *
     * @param componentName
     * @return span instance, for chaining.
     */
    @Override
    public AbstractTracingSpan setComponent(String componentName) {
        this.componentName = componentName;
        return this;
    }

    @Override
    public AbstractSpan start(long startTime) {
        this.startTime = startTime;
        return this;
    }

    public SpanObjectV2.Builder transform() {
        SpanObjectV2.Builder spanBuilder = SpanObjectV2.newBuilder();

        spanBuilder.setSpanId(this.spanId);
        spanBuilder.setParentSpanId(parentSpanId);
        spanBuilder.setStartTime(startTime);
        spanBuilder.setEndTime(endTime);
        if (operationId != DictionaryUtil.nullValue()) {
            spanBuilder.setOperationNameId(operationId);
        } else {
            spanBuilder.setOperationName(operationName);
        }
        if (isEntry()) {
            spanBuilder.setSpanType(SpanType.Entry);
        } else if (isExit()) {
            spanBuilder.setSpanType(SpanType.Exit);
        } else {
            spanBuilder.setSpanType(SpanType.Local);
        }
        if (this.layer != null) {
            spanBuilder.setSpanLayerValue(this.layer.getCode());
        }
        if (componentId != DictionaryUtil.nullValue()) {
            spanBuilder.setComponentId(componentId);
        } else {
            if (componentName != null) {
                spanBuilder.setComponent(componentName);
            }
        }
        spanBuilder.setIsError(errorOccurred);
        if (this.tags != null) {
            for (TagValuePair tag : this.tags) {
                spanBuilder.addTags(tag.transform());
            }
        }
        if (this.logs != null) {
            for (LogDataEntity log : this.logs) {
                spanBuilder.addLogs(log.transform());
            }
        }
        if (this.refs != null) {
            for (TraceSegmentRef ref : this.refs) {
                spanBuilder.addRefs(ref.transform());
            }
        }

        return spanBuilder;
    }

    @Override public void ref(TraceSegmentRef ref) {
        if (refs == null) {
            refs = new LinkedList<>();
        }
        if (!refs.contains(ref)) {
            refs.add(ref);
        }
    }

    @Override public AbstractSpan prepareForAsync() {
        if (isInAsyncMode) {
            throw new RuntimeException("Prepare for async repeatedly. Span is already in async mode.");
        }
        ContextManager.awaitFinishAsync(this);
        isInAsyncMode = true;
        return this;
    }

    @Override public AbstractSpan asyncFinish() {
        if (!isInAsyncMode) {
            throw new RuntimeException("Span is not in async mode, please use '#prepareForAsync' to active.");
        }
        if (isAsyncStopped) {
            throw new RuntimeException("Can not do async finish for the span repeately.");
        }
        this.endTime = System.currentTimeMillis();
        owner.asyncStop(this);
        isAsyncStopped = true;
        return this;
    }
}
