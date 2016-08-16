package com.a.eye.skywalking.protocol;

import com.a.eye.skywalking.protocol.common.AbstractDataSerializable;
import com.a.eye.skywalking.protocol.exception.ConvertFailedException;
import com.a.eye.skywalking.protocol.proto.TraceProtocol;

/**
 * Created by xin on 16/8/16.
 */
public class OutputParameterSpan extends AbstractDataSerializable {
    private static final OutputParameterSpan INSTANCE = new OutputParameterSpan();

    /**
     * tid，调用链的全局唯一标识
     */
    private String traceId;
    /**
     * 当前调用链的描述<br/>
     */
    private String traceLevelId;

    private String outputParameter;

    public OutputParameterSpan() {
    }

    public OutputParameterSpan(String traceId, String traceLevelId) {
        this.traceId = traceId;
        this.traceLevelId = traceLevelId;
    }

    public int getDataType() {
        return 4;
    }

    public byte[] getData() {
        return TraceProtocol.OutputParametersSpan.newBuilder().setOutputParameter(getOutputParameter())
                .setTraceId(getTraceId()).setTraceLevelId(getTraceLevelId()).build().toByteArray();
    }

    public AbstractDataSerializable convertData(byte[] data) throws ConvertFailedException {
        try {
            OutputParameterSpan outputParameterSpan = new OutputParameterSpan();
            TraceProtocol.OutputParametersSpan _protobufOutputSpan = TraceProtocol.OutputParametersSpan.parseFrom(data);
            outputParameterSpan.setOutputParameter(_protobufOutputSpan.getOutputParameter());
            outputParameterSpan.setTraceId(_protobufOutputSpan.getTraceId());
            outputParameterSpan.setTraceLevelId(_protobufOutputSpan.getTraceLevelId());
            return outputParameterSpan;
        } catch (Exception e) {
            throw new ConvertFailedException("Failed to convert output parameter span.", e);
        }

    }

    public static OutputParameterSpan convert(byte[] data) throws ConvertFailedException {
        return (OutputParameterSpan) INSTANCE.convertData(data);
    }

    public boolean isNull() {
        return false;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getTraceLevelId() {
        return traceLevelId;
    }

    public void setTraceLevelId(String traceLevelId) {
        this.traceLevelId = traceLevelId;
    }

    public String getOutputParameter() {
        if (outputParameter == null) {
            return "";
        }
        return outputParameter;
    }

    public void setOutputParameter(String outputParameter) {
        this.outputParameter = outputParameter;
    }
}
