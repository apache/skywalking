package com.a.eye.skywalking.trace;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Span is a concept from OpenTracing Spec, also from Google Dapper Paper.
 * Traces in OpenTracing are defined implicitly by their Spans.
 *
 * Know more things about span concept:
 * {@see https://github.com/opentracing/specification/blob/master/specification.md#the-opentracing-data-model}
 *
 * Created by wusheng on 2017/2/17.
 */
public class Span {
    @Expose
    @SerializedName(value = "si")
    private int spanId;

    @Expose
    @SerializedName(value = "ps")
    private int parentSpanId;

    /**
     * The start time of this Span.
     */
    @Expose
    @SerializedName(value = "st")
    private long startTime;

    /**
     * The end time of this Span.
     */
    @Expose
    @SerializedName(value = "et")
    private long endTime;

    /**
     * The operation name ot this Span.
     * If you want to know, how to set an operation name,
     * {@see https://github.com/opentracing/specification/blob/master/specification.md#start-a-new-span}
     */
    @Expose
    @SerializedName(value = "on")
    private String operationName;

    /**
     * Tag is a concept from OpenTracing spec.
     *
     * {@see https://github.com/opentracing/specification/blob/master/specification.md#set-a-span-tag}
     */
    @Expose
    @SerializedName(value = "ts")
    private final Map<String, String> tagsWithStr;

    @Expose
    @SerializedName(value = "tb")
    private final Map<String, Boolean> tagsWithBool;

    @Expose
    @SerializedName(value = "ti")
    private final Map<String, Integer> tagsWithInt;

    /**
     * Log is a concept from OpenTracing spec.
     *
     * {@see https://github.com/opentracing/specification/blob/master/specification.md#log-structured-data}
     */
    @Expose
    @SerializedName(value = "lo")
    private final List<LogData> logs;

    /**
     * Create a new span, by given span id, parent span id and operationName.
     * This span must belong a {@link TraceSegment}, also is a part of Distributed Trace.
     *
     * @param spanId given by the creator, and must be unique id in the {@link TraceSegment}
     * @param parentSpanId given by the creator, and must be an existed span id in the {@link TraceSegment}. Value -1
     * means no parent span if this {@link TraceSegment}.
     * @param operationName {@link #operationName}
     */
    private Span(int spanId, int parentSpanId, String operationName) {
        this(spanId, parentSpanId, operationName, System.currentTimeMillis());
    }

    /**
     * Create a new span, by given span id, parent span id, operationName and startTime.
     * This span must belong a {@link TraceSegment}, also is a part of Distributed Trace.
     *
     * @param spanId given by the creator, and must be unique id in the {@link TraceSegment}
     * @param parentSpanId given by the creator, and must be an existed span id in the {@link TraceSegment}. Value -1
     * means no parent span if this {@link TraceSegment}.
     * @param operationName {@link #operationName}
     * @param startTime given start timestamp.
     */
    private Span(int spanId, int parentSpanId, String operationName, long startTime) {
        this();
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.startTime = startTime;
        this.operationName = operationName;
    }

    /**
     * Create a new span, by given span id and no parent span id.
     * No parent span id means that, this Span is the first span of the {@link TraceSegment}
     *
     * @param spanId given by the creator, and must be unique id in the {@link TraceSegment}
     * @param operationName {@link #operationName}
     */
    public Span(int spanId, String operationName) {
        this(spanId, -1, operationName);
    }

    /**
     * Create a new span, by given span id and give startTime but no parent span id,
     * No parent span id means that, this Span is the first span of the {@link TraceSegment}
     *
     * @param spanId given by the creator, and must be unique id in the {@link TraceSegment}
     * @param operationName {@link #operationName}
     * @param startTime given start time of span
     */
    public Span(int spanId, String operationName, long startTime) {
        this(spanId, -1, operationName, startTime);
    }

    /**
     * Create a new span, by given span id and given parent {@link Span}.
     *
     * @param spanId given by the creator, and must be unique id in the {@link TraceSegment}
     * @param parentSpan {@link Span}
     * @param operationName {@link #operationName}
     */
    public Span(int spanId, Span parentSpan, String operationName) {
        this(spanId, parentSpan.spanId, operationName, System.currentTimeMillis());
    }

    /**
     * Create a new span, by given span id, parent span, operationName and startTime.
     * This span must belong a {@link TraceSegment}, also is a part of Distributed Trace.
     *
     * @param spanId given by the creator, and must be unique id in the {@link TraceSegment}
     * @param parentSpan {@link Span}
     * @param operationName {@link #operationName}
     * @param startTime given start timestamp
     */
    public Span(int spanId, Span parentSpan, String operationName, long startTime) {
        this(spanId, parentSpan.spanId, operationName, startTime);
    }

    /**
     * Create a new/empty span.
     */
    public Span() {
        tagsWithStr = new HashMap<String, String>(5);
        tagsWithBool = new HashMap<String, Boolean>(1);
        tagsWithInt = new HashMap<String, Integer>(2);
        logs = new LinkedList<LogData>();
    }

    /**
     * Finish the active Span.
     * When it is finished, it will be archived by the given {@link TraceSegment}, which owners it.
     *
     * @param owner of the Span.
     */
    public void finish(TraceSegment owner) {
        this.finish(owner, System.currentTimeMillis());
    }

    /**
     * Finish the active Span.
     * When it is finished, it will be archived by the given {@link TraceSegment}, which owners it.
     * At the same out, set the {@link #endTime} as the given endTime
     *
     * @param owner of the Span.
     * @param endTime of the Span.
     */
    public void finish(TraceSegment owner, long endTime) {
        this.endTime = endTime;
        owner.archive(this);
    }

    /**
     * Sets the string name for the logical operation this span represents.
     *
     * @return this Span instance, for chaining
     */
    public Span setOperationName(String operationName) {
        this.operationName = operationName;
        return this;
    }

    /**
     * Set a key:value tag on the Span.
     *
     * @return this Span instance, for chaining
     */
    public final Span setTag(String key, String value) {
        tagsWithStr.put(key, value);
        return this;
    }

    public final Span setTag(String key, boolean value) {
        tagsWithBool.put(key, value);
        return this;
    }

    public final Span setTag(String key, Integer value) {
        tagsWithInt.put(key, value);
        return this;
    }

    /**
     * Get all tags from this span, but readonly.
     *
     * @return
     */
    public final Map<String, Object> getTags() {
        Map<String, Object> tags = new HashMap<String, Object>();
        tags.putAll(tagsWithStr);
        tags.putAll(tagsWithBool);
        tags.putAll(tagsWithInt);
        return tags;
    }

    /**
     * Get tag value of the given key.
     *
     * @param key the given tag key.
     * @return tag value.
     */
    public String getStrTag(String key) {
        return tagsWithStr.get(key);
    }

    public Boolean getBoolTag(String key) {
        return tagsWithBool.get(key);
    }

    public Integer getIntTag(String key) {
        return tagsWithInt.get(key);
    }

    /**
     * This method is from opentracing-java. {@see https://github.com/opentracing/opentracing-java/blob/release-0.20.9/opentracing-api/src/main/java/io/opentracing/Span.java#L91}
     *
     * Log key:value pairs to the Span with the current walltime timestamp.
     *
     * <p><strong>CAUTIONARY NOTE:</strong> not all Tracer implementations support key:value log fields end-to-end.
     * Caveat emptor.
     *
     * <p>A contrived example (using Guava, which is not required):
     * <pre>{@code
     * span.log(
     * ImmutableMap.Builder<String, Object>()
     * .put("event", "soft error")
     * .put("type", "cache timeout")
     * .put("waited.millis", 1500)
     * .build());
     * }</pre>
     *
     * @param fields key:value log fields. Tracer implementations should support String, numeric, and boolean values;
     * some may also support arbitrary Objects.
     * @return the Span, for chaining
     * @see Span#log(String)
     */
    public Span log(Map<String, String> fields) {
        logs.add(new LogData(System.currentTimeMillis(), fields));
        return this;
    }

    /**
     * Record an exception event of the current walltime timestamp.
     *
     * @param t any subclass of {@link Throwable}, which occurs in this span.
     * @return the Span, for chaining
     */
    public Span log(Throwable t) {
        Map<String, String> exceptionFields = new HashMap<String, String>();
        exceptionFields.put("event", "error");
        exceptionFields.put("error.kind", t.getClass().getName());
        exceptionFields.put("message", t.getMessage());
        exceptionFields.put("stack", ThrowableTransformer.INSTANCE.convert2String(t, 4000));

        return log(exceptionFields);
    }

    private enum ThrowableTransformer {
        INSTANCE;

        private String convert2String(Throwable e, int maxLength) {
            ByteArrayOutputStream buf = null;
            StringBuilder expMessage = new StringBuilder();
            try {
                buf = new ByteArrayOutputStream();
                Throwable causeException = e;
                while (expMessage.length() < maxLength && causeException != null) {
                    causeException.printStackTrace(new java.io.PrintWriter(buf, true));
                    expMessage.append(buf.toString());
                    causeException = causeException.getCause();
                }

            } finally {
                try {
                    buf.close();
                } catch (IOException ioe) {
                }
            }

            return (maxLength > expMessage.length() ? expMessage : expMessage.substring(0, maxLength)).toString();
        }
    }

    /**
     * This method is from opentracing-java. {@see https://github.com/opentracing/opentracing-java/blob/release-0.20.9/opentracing-api/src/main/java/io/opentracing/Span.java#L120}
     *
     * Record an event at the current walltime timestamp.
     *
     * Shorthand for
     *
     * <pre>{@code
     * span.log(Collections.singletonMap("event", event));
     * }</pre>
     *
     * @param event the event value; often a stable identifier for a moment in the Span lifecycle
     * @return the Span, for chaining
     */
    public Span log(String event) {
        log(Collections.singletonMap("event", event));
        return this;
    }

    public int getSpanId() {
        return spanId;
    }

    public int getParentSpanId() {
        return parentSpanId;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public String getOperationName() {
        return operationName;
    }

    public List<LogData> getLogs() {
        return Collections.unmodifiableList(logs);
    }

    @Override
    public String toString() {
        return "Span{" +
            "spanId=" + spanId +
            ", parentSpanId=" + parentSpanId +
            ", startTime=" + startTime +
            ", operationName='" + operationName + '\'' +
            '}';
    }
}
