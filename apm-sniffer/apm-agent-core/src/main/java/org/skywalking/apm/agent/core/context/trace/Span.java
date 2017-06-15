package org.skywalking.apm.agent.core.context.trace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.skywalking.apm.agent.core.context.tag.BooleanTagItem;
import org.skywalking.apm.agent.core.context.tag.IntTagItem;
import org.skywalking.apm.agent.core.context.tag.StringTagItem;
import org.skywalking.apm.agent.core.context.util.ThrowableTransformer;
import org.skywalking.apm.util.StringUtil;

/**
 * Span is a concept from OpenTracing Spec, also from Google Dapper Paper.
 * Traces in OpenTracing are defined implicitly by their Spans.
 * <p>
 * Know more things about span concept:
 * {@see https://github.com/opentracing/specification/blob/master/specification.md#the-opentracing-data-model}
 * <p>
 * Created by wusheng on 2017/2/17.
 */
@JsonAdapter(Span.Serializer.class)
public class Span implements AbstractSpan {
    private static Gson SERIALIZATION_GSON = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

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
     * {@link #peerHost}, {@link #port} and {@link #peers} were part of tags,
     * independence them from tags for better performance and gc.
     */
    private String peerHost;

    private int port;

    private String peers;

    /**
     * Tag is a concept from OpenTracing spec.
     * <p>
     * {@see https://github.com/opentracing/specification/blob/master/specification.md#set-a-span-tag}
     */
    private List<StringTagItem> tagsWithStr;

    private List<BooleanTagItem> tagsWithBool;

    private List<IntTagItem> tagsWithInt;

    /**
     * Log is a concept from OpenTracing spec.
     * <p>
     * {@see https://github.com/opentracing/specification/blob/master/specification.md#log-structured-data}
     */
    private List<LogData> logs;

    /**
     * Create a new span, by given span id, parent span id and operationName.
     * This span must belong a {@link TraceSegment}, also is a part of Distributed Trace.
     *
     * @param spanId given by the creator, and must be unique id in the {@link TraceSegment}
     * @param parentSpanId given by the creator, and must be an existed span id in the {@link TraceSegment}. Value -1
     * means no parent span if this {@link TraceSegment}.
     * @param operationName {@link #operationName}
     */
    protected Span(int spanId, int parentSpanId, String operationName) {
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
    protected Span(int spanId, int parentSpanId, String operationName, long startTime) {
        this();
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.startTime = startTime;
        this.setOperationName(operationName);
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
     * Set the string name for the logical operation this span represents.
     *
     * @return this Span instance, for chaining
     */
    public AbstractSpan setOperationName(String operationName) {
        this.operationName = operationName;
        return this;
    }

    /**
     * Set a key:value tag on the Span.
     *
     * @return this Span instance, for chaining
     */
    public Span setTag(String key, String value) {
        if (tagsWithStr == null) {
            tagsWithStr = new LinkedList<StringTagItem>();
        }
        tagsWithStr.add(new StringTagItem(key, value));
        return this;
    }

    public Span setTag(String key, boolean value) {
        if (tagsWithBool == null) {
            tagsWithBool = new LinkedList<BooleanTagItem>();
        }
        tagsWithBool.add(new BooleanTagItem(key, value));
        return this;
    }

    public Span setTag(String key, Integer value) {
        if (tagsWithInt == null) {
            tagsWithInt = new LinkedList<IntTagItem>();
        }
        tagsWithInt.add(new IntTagItem(key, value));
        return this;
    }

    /**
     * This method is from opentracing-java. {@see https://github.com/opentracing/opentracing-java/blob/release-0.20.9/opentracing-api/src/main/java/io/opentracing/Span.java#L91}
     * <p> Log key:value pairs to the Span with the current walltime timestamp. <p> <p><strong>CAUTIONARY NOTE:</strong>
     * not all Tracer implementations support key:value log fields end-to-end. Caveat emptor. <p> <p>A contrived example
     * (using Guava, which is not required):
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
        if (logs == null) {
            logs = new LinkedList<LogData>();
        }
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

    /**
     * This method is from opentracing-java. {@see https://github.com/opentracing/opentracing-java/blob/release-0.20.9/opentracing-api/src/main/java/io/opentracing/Span.java#L120}
     * <p> Record an event at the current walltime timestamp. <p> Shorthand for <p>
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

    public boolean isLeaf() {
        return false;
    }

    public String getPeerHost() {
        return peerHost;
    }

    public int getPort() {
        return port;
    }

    public String getPeers() {
        return peers;
    }

    public void setPeerHost(String peerHost) {
        this.peerHost = peerHost;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setPeers(String peers) {
        this.peers = peers;
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

    public static class Serializer extends TypeAdapter<Span> {
        @Override
        public void write(JsonWriter out, Span span) throws IOException {
            out.beginObject();
            out.name("si").value(span.spanId);
            out.name("ps").value(span.parentSpanId);
            out.name("st").value(span.startTime);
            out.name("et").value(span.endTime);
            out.name("on").value(span.operationName);

            this.writeTags(out, span);

            if (span.logs != null) {
                out.name("logs").jsonValue(SERIALIZATION_GSON.toJson(span.logs));
            }

            out.endObject();
        }

        public void writeTags(JsonWriter out, Span span) throws IOException {
            JsonObject tagWithStr = null;
            JsonObject tagWithInt = null;
            JsonObject tagWithBool = null;
            if (!StringUtil.isEmpty(span.peerHost)) {
                tagWithStr = new JsonObject();
                tagWithStr.addProperty("peer.host", span.peerHost);
                tagWithInt = new JsonObject();
                tagWithInt.addProperty("peer.port", span.port);
            } else if (!StringUtil.isEmpty(span.peers)) {
                tagWithStr = new JsonObject();
                tagWithStr.addProperty("peers", span.peers);
            } else if (span.tagsWithStr != null) {
                tagWithStr = new JsonObject();
            }

            if (span.tagsWithStr != null) {
                for (StringTagItem item : span.tagsWithStr) {
                    tagWithStr.addProperty(item.getKey(), item.getValue());
                }
            }
            if (span.tagsWithInt != null) {
                if (tagWithInt != null) {
                    tagWithInt = new JsonObject();
                }
                for (IntTagItem item : span.tagsWithInt) {
                    tagWithInt.addProperty(item.getKey(), item.getValue());
                }
            }
            if (span.tagsWithBool != null) {
                tagWithBool = new JsonObject();
                for (BooleanTagItem item : span.tagsWithBool) {
                    tagWithBool.addProperty(item.getKey(), item.getValue());
                }
            }

            if (tagWithStr != null) {
                out.name("ts").jsonValue(tagWithStr.toString());
            }
            if (tagWithInt != null) {
                out.name("ti").jsonValue(tagWithInt.toString());
            }
            if (tagWithBool != null) {
                out.name("tb").jsonValue(tagWithBool.toString());
            }
        }

        @Override
        public Span read(JsonReader in) throws IOException {
            throw new IOException("Can't deserialize span at agent side for performance consideration");
        }
    }
}
