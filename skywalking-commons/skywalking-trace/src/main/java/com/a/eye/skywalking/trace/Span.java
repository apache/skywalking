package com.a.eye.skywalking.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Span is a concept from OpenTracing Spec, also from Google Dapper Paper.
 * Traces in OpenTracing are defined implicitly by their Spans.
 *
 *               [Span A]  ←←←(the root span)
 *                  |
 *           +------+------+
 *           |             |
 *           [Span B]      [Span C] ←←←(Span C is a `ChildOf` Span A)
 *           |             |
 *           [Span D]      +---+-------+
 *                         |           |
 *                      [Span E]    [Span F] >>> [Span G] >>> [Span H]
 *                                     ↑
 *                                     ↑
 *                                     ↑
 *                                  (Span G `FollowsFrom` Span F)
 *
 * Created by wusheng on 2017/2/17.
 */
public class Span {
    private int spanId;

    private int parentSpanId;

    /**
     * The start time of this Span.
     */
    private long startTime;

    /**
     * The end time of this Span.
     */
    private long endTime;

    /**
     * The operation name ot this Span.
     * If you want to know, how to set an operation name,
     * {@see https://github.com/opentracing/specification/blob/master/specification.md#start-a-new-span}
     */
    private String operationName;

    /**
     * Tag is a concept from OpenTracing spec.
     *
     * {@see https://github.com/opentracing/specification/blob/master/specification.md#set-a-span-tag}
     */
    private final Map<String,Object> tags = new HashMap<String,Object>();

    /**
     * Log is a concept from OpenTracing spec.
     *
     * {@see https://github.com/opentracing/specification/blob/master/specification.md#log-structured-data}
     */
    private final List<LogData> logs = new ArrayList<LogData>();

    /**
     * Create a new span, by given span id and parent span id.
     * This span must belong a {@link TraceSegment}, also is a part of Distributed Trace.
     *
     * @param spanId given by the creator, and must be unique id in the {@link TraceSegment}
     * @param parentSpanId given by the creator, and must be an existed span id in the {@link TraceSegment}.
     *                  Value -1 means no parent span if this {@link TraceSegment}.
     * @param operationName {@link #operationName}
     */
    public Span(int spanId, int parentSpanId, String operationName){
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Create a new span, by given span id and no parent span id.
     * No parent span id means that, this Span is the first span of the {@link TraceSegment}
     *
     * @param spanId given by the creator, and must be unique id in the {@link TraceSegment}
     * @param operationName {@link #operationName}
     */
    public Span(int spanId, String operationName){
        this(spanId, -1, operationName);
    }

    /**
     * Finish the active Span.
     * When it is finished, it will be archived by the given {@link TraceSegment}, which owners it.
     *
     * @param owner of the Span.
     */
    public void finish(TraceSegment owner){
        this.endTime = System.currentTimeMillis();
        owner.archive(this);
    }

    /**
     * Set a key:value tag on the Span.
     */
    public final Span setTag(String key, String value) {
        tags.put(key, value);
        return this;
    }

    public final Span setTag(String key, boolean value) {
        tags.put(key, value);
        return this;
    }

    public final Span setTag(String key, Number value) {
        tags.put(key, value);
        return this;
    }

    /**
     * Get all tags from this span, but readonly.
     *
     * @return
     */
    public final Map<String,Object> getTags() {
        return Collections.unmodifiableMap(tags);
    }

    /**
     * This method is from opentracing-java.
     * {@see https://github.com/opentracing/opentracing-java/blob/release-0.20.9/opentracing-api/src/main/java/io/opentracing/Span.java#L91}
     *
     * Log key:value pairs to the Span with the current walltime timestamp.
     *
     * <p><strong>CAUTIONARY NOTE:</strong> not all Tracer implementations support key:value log fields end-to-end.
     * Caveat emptor.
     *
     * <p>A contrived example (using Guava, which is not required):
     * <pre>{@code
        span.log(
            ImmutableMap.Builder<String, Object>()
            .put("event", "soft error")
            .put("type", "cache timeout")
            .put("waited.millis", 1500)
            .build());
        }</pre>
     *
     * @param fields key:value log fields. Tracer implementations should support String, numeric, and boolean values;
     *               some may also support arbitrary Objects.
     * @return the Span, for chaining
     * @see Span#log(String)
     */
    public Span log(Map<String, ?> fields){
        logs.add(new LogData(System.currentTimeMillis(), fields));
        return this;
    }

    /**
     * This method is from opentracing-java.
     * {@see https://github.com/opentracing/opentracing-java/blob/release-0.20.9/opentracing-api/src/main/java/io/opentracing/Span.java#L120}
     *
     * Record an event at the current walltime timestamp.
     *
     * Shorthand for
     *
     * <pre>{@code
        span.log(Collections.singletonMap("event", event));
        }</pre>
     *
     * @param event the event value; often a stable identifier for a moment in the Span lifecycle
     * @return the Span, for chaining
     */
    public Span log(String event){
        log(Collections.singletonMap("event", event));
        return this;
    }
}
