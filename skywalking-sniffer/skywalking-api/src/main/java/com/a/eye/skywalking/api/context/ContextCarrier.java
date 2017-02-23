package com.a.eye.skywalking.api.context;

import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.TraceSegmentRef;
import com.a.eye.skywalking.api.util.StringUtil;
import java.io.Serializable;

/**
 * {@link ContextCarrier} is a data carrier of {@link TracerContext}.
 * It holds the snapshot (current state) of {@link TracerContext}.
 *
 * Created by wusheng on 2017/2/17.
 */
public class ContextCarrier implements Serializable {
    /**
     * {@link TraceSegment#traceSegmentId}
     */
    private String traceSegmentId;

    /**
     * {@link Span#spanId}
     */
    private int spanId = -1;

    /**
     * Serialize this {@link ContextCarrier} to a {@link String},
     * with '|' split.
     *
     * @return the serialization string.
     */
    public String serialize() {
        return StringUtil.join('|', this.getTraceSegmentId(), this.getSpanId() + "");
    }

    /**
     * Initialize fields with the given text.
     *
     * @param text carries {@link #traceSegmentId} and {@link #spanId}, with '|' split.
     */
    public ContextCarrier deserialize(String text) {
        if(text != null){
            String[] parts = text.split("\\|");
            if(parts.length == 2){
                try{
                    setSpanId(Integer.parseInt(parts[1]));
                    setTraceSegmentId(parts[0]);
                }catch(NumberFormatException e){

                }
            }
        }
        return this;
    }

    /**
     * Make sure this {@link ContextCarrier} has been initialized.
     *
     * @return true for unbroken {@link ContextCarrier} or no-initialized. Otherwise, false;
     */
    public boolean isValid(){
        return !StringUtil.isEmpty(getTraceSegmentId()) && getSpanId() > -1;
    }

    public String getTraceSegmentId() {
        return traceSegmentId;
    }

    public int getSpanId() {
        return spanId;
    }

    public void setTraceSegmentId(String traceSegmentId) {
        this.traceSegmentId = traceSegmentId;
    }

    public void setSpanId(int spanId) {
        this.spanId = spanId;
    }
}
