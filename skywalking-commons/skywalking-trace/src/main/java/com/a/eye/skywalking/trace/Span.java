package com.a.eye.skywalking.trace;

import com.a.eye.skywalking.api.util.StringUtil;
import com.a.eye.skywalking.messages.ISerializable;
import com.a.eye.skywalking.trace.proto.KeyValue;
import com.a.eye.skywalking.trace.proto.LogDataMessage;
import com.a.eye.skywalking.trace.proto.SpanMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
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
public class Span implements ISerializable<SpanMessage> {
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
    private final Map<String, Object> tags;

    /**
     * Log is a concept from OpenTracing spec.
     *
     * {@see https://github.com/opentracing/specification/blob/master/specification.md#log-structured-data}
     */
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
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.startTime = startTime;
        this.operationName = operationName;
        this.tags = new HashMap<String, Object>();
        this.logs = new ArrayList<LogData>();
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
     * Create a new span, by given {@link SpanMessage}, which you can get from another {@link Span} object,
     * by calling {@link Span#serialize()};
     *
     * @param spanMessage from another {@link Span#serialize()}
     */
    public Span(SpanMessage spanMessage) {
        tags = new HashMap<String, Object>();
        logs = new LinkedList<LogData>();
        this.deserialize(spanMessage);
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
    public final Map<String, Object> getTags() {
        return Collections.unmodifiableMap(tags);
    }

    /**
     * Get tag value of the given key.
     *
     * @param key the given tag key.
     * @return tag value.
     */
    public Object getTag(String key) {
        return tags.get(key);
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
    public Span log(Map<String, ?> fields) {
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

    @Override
    public SpanMessage serialize() {
        SpanMessage.Builder builder = SpanMessage.newBuilder();
        builder.setSpanId(spanId);
        builder.setStartTime(startTime);
        builder.setEndTime(endTime);
        builder.setOperationName(operationName);
        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            KeyValue.Builder tagEntryBuilder = KeyValue.newBuilder();
            tagEntryBuilder.setKey(entry.getKey());
            String value = String.valueOf(entry.getValue());
            if (!StringUtil.isEmpty(value)) {
                tagEntryBuilder.setValue(value);
            }
            builder.addTags(tagEntryBuilder);
        }

        for (LogData log : logs) {
            builder.addLogs(log.serialize());
        }
        return builder.build();
    }

    @Override
    public void deserialize(SpanMessage message) {
        spanId = message.getSpanId();
        startTime = message.getStartTime();
        endTime = message.getEndTime();
        operationName = message.getOperationName();

        List<KeyValue> tagsList = message.getTagsList();
        if(tagsList != null){
            for (KeyValue tag : tagsList) {
                tags.put(tag.getKey(), tag.getValue());
            }
        }

        List<LogDataMessage> logsList = message.getLogsList();
        if (logsList != null) {
            for (LogDataMessage logDataMessage : logsList) {
                logs.add(new LogData(logDataMessage));
            }
        }
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
