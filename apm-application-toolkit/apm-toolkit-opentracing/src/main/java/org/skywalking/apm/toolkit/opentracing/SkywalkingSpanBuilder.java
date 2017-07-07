package org.skywalking.apm.toolkit.opentracing;

import io.opentracing.ActiveSpan;
import io.opentracing.BaseSpan;
import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import java.util.LinkedList;
import java.util.List;

/**
 * @author wusheng
 */
public class SkywalkingSpanBuilder implements Tracer.SpanBuilder {
    private List<Tag> tags = new LinkedList<Tag>();
    private String operationName;
    private boolean isEntry = false;
    private boolean isExit = false;
    private int port;
    private String peer;
    private String componentName;
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
        if (parent instanceof SkywalkingSpan) {
            return this;
        }
        throw new IllegalArgumentException("parent must be type of SkywalkingSpan");
    }

    /**
     * Ignore the reference type. the span always the entry or has a parent span.
     *
     * @param referenceType
     * @param referencedContext
     * @return
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
        if (Tags.COMPONENT.equals(key)) {
            componentName = value;
        } else if (Tags.SPAN_KIND.equals(key)) {
            if (Tags.SPAN_KIND_CLIENT.equals(key) || Tags.SPAN_KIND_PRODUCER.equals(key)) {
                isEntry = false;
                isExit = true;
            } else if (Tags.SPAN_KIND_SERVER.equals(key) || Tags.SPAN_KIND_CONSUMER.equals(key)) {
                isEntry = true;
                isExit = false;
            } else {
                isEntry = false;
                isExit = false;
            }
        } else if (Tags.PEER_HOST_IPV4.equals(key) || Tags.PEER_HOST_IPV6.equals(key)
            || Tags.PEER_HOSTNAME.equals(key)) {
            peer = value;
        } else if (Tags.PEER_SERVICE.equals(key)) {
            operationName = value;
        } else {
            tags.add(new Tag(key, value));
        }
        return this;
    }

    @Override
    public Tracer.SpanBuilder withTag(String key, boolean value) {
        if (Tags.ERROR.equals(key)) {
            isError = value;
        } else {
            tags.add(new Tag(key, value ? "true" : "false"));
        }
        return this;
    }

    @Override
    public Tracer.SpanBuilder withTag(String key, Number value) {
        if (Tags.PEER_PORT.equals(key)) {
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

    public String getComponentName() {
        return componentName;
    }

    public boolean isError() {
        return isError;
    }

    public long getStartTime() {
        return startTime;
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
